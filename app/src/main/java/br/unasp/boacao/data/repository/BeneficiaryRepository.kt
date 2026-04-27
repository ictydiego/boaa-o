package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface BeneficiaryRepository {
    suspend fun getIncomingDonations(beneficiaryId: String): Result<List<Donation>>
    fun observeIncomingDonations(beneficiaryId: String): Flow<List<Donation>>
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

    override fun observeIncomingDonations(beneficiaryId: String): Flow<List<Donation>> = callbackFlow {
        val registration = firestore.collection("donations")
            .whereEqualTo("beneficiaryId", beneficiaryId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(Donation::class.java) }
                    ?.filter { it.status == DonationStatus.IN_TRANSIT }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
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

    /**
     * Uses a Firestore transaction to confirm delivery atomically.
     * Verifies status is IN_TRANSIT and delivery code matches before completing.
     */
    override suspend fun confirmDelivery(
        donationId: String,
        deliveryCode: String,
        proofPhotoBase64: String,
        beneficiaryId: String,
        beneficiaryName: String
    ): Result<Unit> {
        return try {
            val docRef = firestore.collection("donations").document(donationId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStatus = snapshot.getString("status")
                if (currentStatus != DonationStatus.IN_TRANSIT.name) {
                    throw Exception("Esta doação não está mais em trânsito.")
                }
                val storedCode = snapshot.getString("deliveryCode")
                if (storedCode != deliveryCode) {
                    throw Exception("PIN inválido. Verifique o código com o voluntário.")
                }
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val updates = mutableMapOf<String, Any>(
                    "status" to DonationStatus.DELIVERED.name,
                    "deliveredAt" to today
                )
                if (proofPhotoBase64.isNotBlank()) updates["proofPhotoBase64"] = proofPhotoBase64
                if (beneficiaryId.isNotBlank()) updates["beneficiaryId"] = beneficiaryId
                if (beneficiaryName.isNotBlank()) updates["beneficiaryName"] = beneficiaryName
                updates.forEach { (key, value) -> transaction.update(docRef, key, value) }
            }.await()
            // Increment NGO's receivedCount outside transaction (non-critical)
            if (beneficiaryId.isNotBlank()) {
                firestore.collection("users").document(beneficiaryId)
                    .update("receivedCount", FieldValue.increment(1)).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
