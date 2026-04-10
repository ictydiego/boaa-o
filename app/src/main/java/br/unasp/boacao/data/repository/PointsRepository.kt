package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.PointsTransaction
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface PointsRepository {
    suspend fun addPoints(userId: String, points: Int, reason: String): Result<Int>
    suspend fun getPoints(userId: String): Result<Int>
    suspend fun getPointsHistory(userId: String): Result<List<PointsTransaction>>
}

class PointsRepositoryImpl(private val firestore: FirebaseFirestore) : PointsRepository {

    override suspend fun addPoints(userId: String, points: Int, reason: String): Result<Int> {
        return try {
            val userRef = firestore.collection("users").document(userId)
            userRef.update("points", FieldValue.increment(points.toLong())).await()
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val transaction = hashMapOf("points" to points, "reason" to reason, "date" to dateStr)
            userRef.collection("pointsHistory").add(transaction).await()
            val doc = userRef.get().await()
            val newTotal = (doc.getLong("points") ?: 0L).toInt()
            Result.success(newTotal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPoints(userId: String): Result<Int> {
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            Result.success((doc.getLong("points") ?: 0L).toInt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPointsHistory(userId: String): Result<List<PointsTransaction>> {
        return try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("pointsHistory")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
            val list = snapshot.documents.mapNotNull { doc ->
                PointsTransaction(
                    points = (doc.getLong("points") ?: 0L).toInt(),
                    reason = doc.getString("reason") ?: "",
                    date = doc.getString("date") ?: ""
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
