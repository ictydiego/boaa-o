package br.unasp.boacao.domain

data class PointsTransaction(
    val points: Int = 0,
    val reason: String = "",
    val date: String = ""
)

data class GiftCard(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val brand: String = "",
    val requiredPoints: Int = 0,
    val iconName: String = "",
    val isActive: Boolean = true
)

data class RedeemedGiftCard(
    val id: String = "",
    val userId: String = "",
    val giftCardId: String = "",
    val giftCardTitle: String = "",
    val voucherCode: String = "",
    val redeemedAt: String = "",
    val pointsSpent: Int = 0
)

val GIFT_CARD_CATALOG = listOf(
    GiftCard("netflix", "Netflix 1 mês", "Assista filmes e séries à vontade por 30 dias.", "Netflix", 500, "movie"),
    GiftCard("spotify", "Spotify Premium", "Ouça músicas sem anúncios por 1 mês.", "Spotify", 300, "music_note"),
    GiftCard("ifood", "iFood R\$20", "Desconto de R\$20 no seu próximo pedido.", "iFood", 200, "restaurant"),
    GiftCard("amazon", "Amazon R\$30", "Vale-presente de R\$30 para compras na Amazon.", "Amazon", 400, "shopping_bag"),
    GiftCard("americanas", "Americanas R\$25", "Vale-presente de R\$25 nas Americanas.", "Americanas", 250, "store")
)
