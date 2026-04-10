package br.unasp.boacao.domain.model

enum class DonationStatus {
    AVAILABLE,  // Disponível
    CLAIMED,    // Voluntário reservou (indo buscar)
    IN_TRANSIT, // Voluntário digitou o PIN e pegou a comida (indo pra ONG)
    DELIVERED   // ONG digitou o PIN (Finalizado)
}

data class DonationItem(
    val name: String = "",
    val quantity: String = ""
)

data class Donation(
    val id: String = "",
    val donorId: String = "",
    val donorName: String = "",
    val title: String = "",
    val description: String = "",
    val expiryDate: String = "",
    val pickupAddress: String = "",
    val status: DonationStatus = DonationStatus.AVAILABLE,
    val pickupCode: String = "",
    val deliveryCode: String = "",

    // Novos campos para o rastreio do ciclo de vida:
    val volunteerId: String? = null,
    val volunteerName: String? = null,
    val beneficiaryId: String? = null,
    val beneficiaryName: String? = null,
    val items: List<DonationItem> = emptyList(),
    val createdAt: String = "",
    val deliveredAt: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val proofPhotoBase64: String = ""
)