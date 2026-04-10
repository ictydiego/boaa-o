package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface VolunteerRepository {
    suspend fun getAvailableDonations(): Result<List<Donation>>
    suspend fun getMyDeliveries(volunteerId: String): Result<List<Donation>>
    suspend fun claimDonation(donationId: String, volunteerId: String, volunteerName: String): Result<Unit>
    suspend fun confirmPickup(donationId: String, typedPin: String): Result<Unit>
    suspend fun assignNgo(donationId: String, ngoId: String, ngoName: String): Result<Unit>
    suspend fun getDeliveryHistory(volunteerId: String): Result<List<Donation>>
}

class VolunteerRepositoryImpl(
    private val firestore: FirebaseFirestore
) : VolunteerRepository {

    override suspend fun getAvailableDonations(): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("status", DonationStatus.AVAILABLE.name)
                .get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(Donation::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyDeliveries(volunteerId: String): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("volunteerId", volunteerId)
                .get().await()
            val list = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .filter { it.status == DonationStatus.CLAIMED || it.status == DonationStatus.IN_TRANSIT }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun claimDonation(donationId: String, volunteerId: String, volunteerName: String): Result<Unit> {
        return try {
            firestore.collection("donations").document(donationId)
                .update(
                    "status", DonationStatus.CLAIMED.name,
                    "volunteerId", volunteerId,
                    "volunteerName", volunteerName
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmPickup(donationId: String, typedPin: String): Result<Unit> {
        return try {
            val doc = firestore.collection("donations").document(donationId).get().await()
            val realPin = doc.getString("pickupCode")
            if (realPin == typedPin) {
                firestore.collection("donations").document(donationId)
                    .update("status", DonationStatus.IN_TRANSIT.name)
                    .await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("PIN incorreto. Peça ao doador para verificar o código."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignNgo(donationId: String, ngoId: String, ngoName: String): Result<Unit> {
        return try {
            firestore.collection("donations").document(donationId)
                .update("beneficiaryId", ngoId, "beneficiaryName", ngoName).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDeliveryHistory(volunteerId: String): Result<List<Donation>> {
        return try {
            val snapshot = firestore.collection("donations")
                .whereEqualTo("volunteerId", volunteerId)
                .get().await()
            // Filter DELIVERED locally to avoid needing a composite Firestore index
            val list = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .filter { it.status == DonationStatus.DELIVERED }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
