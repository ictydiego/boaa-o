package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.domain.model.UserRole
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface RankingRepository {
    suspend fun getTopUsers(role: UserRole, limit: Int = 10): Result<List<UserProfile>>
}

class RankingRepositoryImpl(private val firestore: FirebaseFirestore) : RankingRepository {

    override suspend fun getTopUsers(role: UserRole, limit: Int): Result<List<UserProfile>> {
        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("role", role.name)
                .get().await()
            val list = snapshot.documents.mapNotNull { doc ->
                val roleStr = doc.getString("role") ?: return@mapNotNull null
                val r = try { UserRole.valueOf(roleStr) } catch (e: Exception) { return@mapNotNull null }
                UserProfile(
                    id = doc.id,
                    email = doc.getString("email") ?: "",
                    role = r,
                    name = doc.getString("name") ?: "",
                    document = doc.getString("document") ?: "",
                    address = doc.getString("address") ?: "",
                    points = (doc.getLong("points") ?: 0L).toInt(),
                    donationCount = (doc.getLong("donationCount") ?: 0L).toInt(),
                    receivedCount = (doc.getLong("receivedCount") ?: 0L).toInt(),
                    photoBase64 = doc.getString("photoBase64") ?: ""
                )
            }
            // Sort locally — avoids needing composite Firestore index
            val sorted = when (role) {
                UserRole.DONOR -> list.sortedByDescending { it.donationCount }
                UserRole.VOLUNTEER -> list.sortedByDescending { it.points }
                UserRole.BENEFICIARY -> list.sortedByDescending { it.receivedCount }
            }.take(limit)
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
