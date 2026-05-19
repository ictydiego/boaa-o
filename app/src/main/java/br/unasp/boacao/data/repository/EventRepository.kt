package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.EventStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface EventRepository {
    fun observePublishedEvents(): Flow<List<Event>>
    fun observeNgoEvents(ngoId: String): Flow<List<Event>>
    suspend fun getEvent(eventId: String): Result<Event>
    suspend fun createEvent(event: Event): Result<String>
    suspend fun updateStatus(eventId: String, status: EventStatus): Result<Unit>
}

class EventRepositoryImpl(
    private val firestore: FirebaseFirestore
) : EventRepository {

    private val collection = firestore.collection("events")

    override fun observePublishedEvents(): Flow<List<Event>> = callbackFlow {
        val reg = collection
            .whereIn("status", listOf(EventStatus.PUBLISHED.name, EventStatus.IN_PROGRESS.name))
            .addSnapshotListener { snap, err ->
                if (err != null) { close(); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Event::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeNgoEvents(ngoId: String): Flow<List<Event>> = callbackFlow {
        val reg = collection.whereEqualTo("ngoId", ngoId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Event::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun getEvent(eventId: String): Result<Event> = try {
        val doc = collection.document(eventId).get().await()
        val event = doc.toObject(Event::class.java)?.copy(id = doc.id)
            ?: throw Exception("Evento não encontrado")
        Result.success(event)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun createEvent(event: Event): Result<String> = try {
        val ref = collection.document()
        val toSave = event.copy(id = ref.id, createdAt = System.currentTimeMillis())
        ref.set(toSave).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun updateStatus(eventId: String, status: EventStatus): Result<Unit> = try {
        collection.document(eventId).update("status", status.name).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
