package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface BeneficiaryRepository {
    suspend fun getIncomingDonations(beneficiaryId: String): Result<List<Donation>>
    suspend fun getDeliveryHistory(beneficiaryId: String): Result<List<Donation>>
    suspend fun getDonationByDeliveryCode(code: String): Result<Donation?>
    suspend fun confirmDelivery(
        donationId: String,
        deliveryCode: String,
        proofPhotoBase64: String = "",
        beneficiaryId: String = "",
        beneficiaryName: String = ""
    ): Result<Unit>
}

class BeneficiaryRepositoryImpl(
    private val firestore: FirebaseFirestore
) : BeneficiaryRepository {

    override suspend fun getIncomingDonations(beneficiaryId: String): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("beneficiaryId", beneficiaryId)
                .get().await()
            val list = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .filter { it.status == DonationStatus.IN_TRANSIT }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDeliveryHistory(beneficiaryId: String): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("beneficiaryId", beneficiaryId)
                .get().await()
            val list = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .filter { it.status == DonationStatus.DELIVERED }
                .sortedByDescending { it.deliveredAt }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDonationByDeliveryCode(code: String): Result<Donation?> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("deliveryCode", code)
                .get().await()
            val donation = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .firstOrNull { it.status == DonationStatus.IN_TRANSIT }
            Result.success(donation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmDelivery(
        donationId: String,
        deliveryCode: String,
        proofPhotoBase64: String,
        beneficiaryId: String,
        beneficiaryName: String
    ): Result<Unit> {
        return try {
            val doc = firestore.collection("donations").document(donationId).get().await()
            if (doc.getString("deliveryCode") == deliveryCode) {
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val updates = mutableMapOf<String, Any>(
                    "status" to DonationStatus.DELIVERED.name,
                    "deliveredAt" to today
                )
                if (proofPhotoBase64.isNotBlank()) updates["proofPhotoBase64"] = proofPhotoBase64
                if (beneficiaryId.isNotBlank()) updates["beneficiaryId"] = beneficiaryId
                if (beneficiaryName.isNotBlank()) updates["beneficiaryName"] = beneficiaryName
                firestore.collection("donations").document(donationId).update(updates).await()
                // Increment NGO's receivedCount
                if (beneficiaryId.isNotBlank()) {
                    firestore.collection("users").document(beneficiaryId)
                        .update("receivedCount", FieldValue.increment(1)).await()
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("PIN inválido. Verifique o código com o voluntário."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
