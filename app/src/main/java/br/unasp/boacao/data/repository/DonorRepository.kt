package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DonorRepository {
    suspend fun createDonation(donation: Donation): Result<Unit>
    suspend fun getMyDonations(donorId: String): Result<List<Donation>>
    suspend fun cancelDonation(donationId: String, donorId: String): Result<Unit>
}

class DonorRepositoryImpl(
    private val firestore: FirebaseFirestore
) : DonorRepository {

    override suspend fun createDonation(donation: Donation): Result<Unit> {
        return try {
            val collection = firestore.collection("donations")
            val documentId = collection.document().id
            val pCode = (1000..9999).random().toString()
            val dCode = (1000..9999).random().toString()
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val donationToSave = donation.copy(id = documentId, pickupCode = pCode, deliveryCode = dCode, createdAt = today)
            collection.document(documentId).set(donationToSave).await()
            firestore.collection("users").document(donation.donorId)
                .update("donationCount", FieldValue.increment(1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyDonations(donorId: String): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("donorId", donorId)
                .get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(Donation::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelDonation(donationId: String, donorId: String): Result<Unit> {
        return try {
            firestore.collection("donations").document(donationId).delete().await()
            firestore.collection("users").document(donorId)
                .update("donationCount", FieldValue.increment(-1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
