package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface VolunteerRepository {
    suspend fun getAvailableDonations(): Result<List<Donation>>
    fun observeAvailableDonations(): Flow<List<Donation>>
    fun observeMyDeliveries(volunteerId: String): Flow<List<Donation>>
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

    override fun observeAvailableDonations(): Flow<List<Donation>> = callbackFlow {
        val registration = firestore.collection("donations")
            .whereEqualTo("status", DonationStatus.AVAILABLE.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Donation::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    override fun observeMyDeliveries(volunteerId: String): Flow<List<Donation>> = callbackFlow {
        val registration = firestore.collection("donations")
            .whereEqualTo("volunteerId", volunteerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close()
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(Donation::class.java) }
                    ?.filter { it.status == DonationStatus.CLAIMED || it.status == DonationStatus.IN_TRANSIT }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
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

    /**
     * Uses a Firestore transaction to atomically claim a donation.
     * Verifies the donation is still AVAILABLE before claiming,
     * preventing two volunteers from claiming the same donation.
     */
    override suspend fun claimDonation(donationId: String, volunteerId: String, volunteerName: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("donations").document(donationId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStatus = snapshot.getString("status")
                if (currentStatus != DonationStatus.AVAILABLE.name) {
                    throw Exception("Esta doação já foi reservada por outro voluntário.")
                }
                transaction.update(docRef,
                    "status", DonationStatus.CLAIMED.name,
                    "volunteerId", volunteerId,
                    "volunteerName", volunteerName
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uses a Firestore transaction to confirm pickup.
     * Verifies status is CLAIMED and PIN matches atomically.
     */
    override suspend fun confirmPickup(donationId: String, typedPin: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("donations").document(donationId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStatus = snapshot.getString("status")
                if (currentStatus != DonationStatus.CLAIMED.name) {
                    throw Exception("Status inválido. A doação não está mais aguardando coleta.")
                }
                val realPin = snapshot.getString("pickupCode")
                if (realPin != typedPin) {
                    throw Exception("PIN incorreto. Peça ao doador para verificar o código.")
                }
                transaction.update(docRef, "status", DonationStatus.IN_TRANSIT.name)
            }.await()
            Result.success(Unit)
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
            val list = snapshot.documents
                .mapNotNull { it.toObject(Donation::class.java) }
                .filter { it.status == DonationStatus.DELIVERED }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
