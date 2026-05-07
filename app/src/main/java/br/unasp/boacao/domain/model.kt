package br.unasp.boacao.domain.model

enum class UserRole {
    DONOR,
    VOLUNTEER,
    BENEFICIARY
}

data class UserProfile(
    val id: String = "",
    val email: String = "",
    val role: UserRole = UserRole.VOLUNTEER,
    val name: String = "",
    val document: String = "",
    val address: String = "",
    val points: Int = 0,
    val donationCount: Int = 0,
    val receivedCount: Int = 0,
    val photoBase64: String = "",
    val signatureBase64: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)