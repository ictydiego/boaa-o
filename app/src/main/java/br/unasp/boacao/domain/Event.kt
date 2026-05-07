package br.unasp.boacao.domain.model

data class Event(
    val id: String = "",
    val ngoId: String = "",
    val ngoName: String = "",
    val title: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val workloadHours: Double = 0.0,
    val maxParticipants: Int = 0,
    val status: EventStatus = EventStatus.PUBLISHED,
    val createdAt: Long = 0L
)

enum class EventStatus { PUBLISHED, IN_PROGRESS, FINISHED, CANCELLED }
