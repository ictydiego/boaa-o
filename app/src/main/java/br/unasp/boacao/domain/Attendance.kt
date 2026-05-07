package br.unasp.boacao.domain.model

data class Attendance(
    val id: String = "",
    val eventId: String = "",
    val volunteerId: String = "",
    val volunteerName: String = "",
    val volunteerDocument: String = "",
    val ticketCode: String = "",
    val checkInAt: Long? = null,
    val checkOutAt: Long? = null,
    val performanceNote: String = "",
    val status: AttendanceStatus = AttendanceStatus.SUBSCRIBED,
    val certificateHash: String = ""
)

enum class AttendanceStatus { SUBSCRIBED, CHECKED_IN, CHECKED_OUT, NO_SHOW }
