package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.util.HashUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface AttendanceRepository {
    suspend fun subscribe(eventId: String, volunteer: UserProfile): Result<Attendance>
    fun observeMyAttendances(volunteerId: String): Flow<List<Attendance>>
    fun observeEventAttendances(eventId: String): Flow<List<Attendance>>
    suspend fun getAttendance(eventId: String, attendanceId: String): Result<Attendance>
    suspend fun checkIn(eventId: String, ticketCode: String): Result<Attendance>
    suspend fun checkOut(eventId: String, ticketCode: String, performanceNote: String, rating: Int): Result<Attendance>
}

class AttendanceRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val pointsRepository: PointsRepository? = null
) : AttendanceRepository {

    private fun col(eventId: String) =
        firestore.collection("events").document(eventId).collection("attendances")

    override suspend fun subscribe(eventId: String, volunteer: UserProfile): Result<Attendance> = try {
        val eventRef = firestore.collection("events").document(eventId)
        // Deterministic doc id = volunteer id makes subscribe idempotent: repeated
        // taps hit the same document instead of creating duplicate attendances.
        val attRef = eventRef.collection("attendances").document(volunteer.id)
        val attendance = firestore.runTransaction { tx ->
            tx.get(eventRef) // ensure event exists
            val existing = tx.get(attRef)
            if (existing.exists()) {
                existing.toObject(Attendance::class.java)!!.copy(id = existing.id)
            } else {
                val newAttendance = Attendance(
                    id = attRef.id,
                    eventId = eventId,
                    volunteerId = volunteer.id,
                    volunteerName = volunteer.name,
                    volunteerDocument = volunteer.document,
                    ticketCode = UUID.randomUUID().toString(),
                    status = AttendanceStatus.SUBSCRIBED
                )
                tx.set(attRef, newAttendance)
                newAttendance
            }
        }.await()
        Result.success(attendance)
    } catch (e: Exception) { Result.failure(e) }

    override fun observeMyAttendances(volunteerId: String): Flow<List<Attendance>> = callbackFlow {
        val reg = firestore.collectionGroup("attendances")
            .whereEqualTo("volunteerId", volunteerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Attendance::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeEventAttendances(eventId: String): Flow<List<Attendance>> = callbackFlow {
        val reg = col(eventId).addSnapshotListener { snap, err ->
            if (err != null) { close(); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { d ->
                d.toObject(Attendance::class.java)?.copy(id = d.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun getAttendance(eventId: String, attendanceId: String): Result<Attendance> = try {
        val doc = col(eventId).document(attendanceId).get().await()
        val a = doc.toObject(Attendance::class.java)?.copy(id = doc.id)
            ?: throw Exception("Inscrição não encontrada")
        Result.success(a)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun checkIn(eventId: String, ticketCode: String): Result<Attendance> = try {
        val eventRef = firestore.collection("events").document(eventId)
        val query = col(eventId).whereEqualTo("ticketCode", ticketCode).limit(1).get().await()
        val doc = query.documents.firstOrNull() ?: throw Exception("Ticket inválido")
        val ref = doc.reference
        val updated = firestore.runTransaction { tx ->
            val eventSnap = tx.get(eventRef)
            val eventStatus = eventSnap.getString("status")
            if (eventStatus != "IN_PROGRESS") {
                throw Exception("Evento não iniciado. Inicie o evento antes de fazer check-in.")
            }
            val snap = tx.get(ref)
            val status = snap.getString("status")
            if (status != AttendanceStatus.SUBSCRIBED.name) {
                throw Exception("Status inválido para check-in: $status")
            }
            val now = System.currentTimeMillis()
            tx.update(ref, mapOf(
                "checkInAt" to now,
                "status" to AttendanceStatus.CHECKED_IN.name
            ))
            snap.toObject(Attendance::class.java)!!.copy(
                id = snap.id, checkInAt = now, status = AttendanceStatus.CHECKED_IN
            )
        }.await()
        Result.success(updated)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun checkOut(eventId: String, ticketCode: String, performanceNote: String, rating: Int): Result<Attendance> = try {
        val safeRating = rating.coerceIn(1, 5)
        val query = col(eventId).whereEqualTo("ticketCode", ticketCode).limit(1).get().await()
        val doc = query.documents.firstOrNull() ?: throw Exception("Ticket inválido")
        val ref = doc.reference
        val updated = firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val status = snap.getString("status")
            if (status != AttendanceStatus.CHECKED_IN.name) {
                throw Exception("Check-in necessário antes do check-out")
            }
            val volunteerId = snap.getString("volunteerId") ?: ""
            val now = System.currentTimeMillis()
            val hash = HashUtil.certificateHash(volunteerId, eventId)
            tx.update(ref, mapOf(
                "checkOutAt" to now,
                "performanceNote" to performanceNote,
                "rating" to safeRating,
                "status" to AttendanceStatus.CHECKED_OUT.name,
                "certificateHash" to hash
            ))
            snap.toObject(Attendance::class.java)!!.copy(
                id = snap.id, checkOutAt = now,
                performanceNote = performanceNote,
                rating = safeRating,
                status = AttendanceStatus.CHECKED_OUT,
                certificateHash = hash
            )
        }.await()
        // Award stars-weighted points: 1★=10, 5★=50.
        // Idempotency-light: relies on tx above only allowing CHECKED_IN→CHECKED_OUT once.
        val pts = safeRating * POINTS_PER_STAR
        pointsRepository?.addPoints(updated.volunteerId, pts, "Participação em evento (${safeRating}★)")
        Result.success(updated)
    } catch (e: Exception) { Result.failure(e) }

    companion object {
        const val POINTS_PER_STAR = 10
    }
}
