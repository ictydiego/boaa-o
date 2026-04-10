package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.GiftCard
import br.unasp.boacao.domain.GIFT_CARD_CATALOG
import br.unasp.boacao.domain.RedeemedGiftCard
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

interface GiftCardRepository {
    fun getAvailableGiftCards(): List<GiftCard>
    suspend fun redeemGiftCard(userId: String, giftCard: GiftCard, currentPoints: Int): Result<RedeemedGiftCard>
    suspend fun getRedeemedCards(userId: String): Result<List<RedeemedGiftCard>>
}

class GiftCardRepositoryImpl(private val firestore: FirebaseFirestore) : GiftCardRepository {

    override fun getAvailableGiftCards(): List<GiftCard> = GIFT_CARD_CATALOG

    override suspend fun redeemGiftCard(
        userId: String,
        giftCard: GiftCard,
        currentPoints: Int
    ): Result<RedeemedGiftCard> {
        return try {
            if (currentPoints < giftCard.requiredPoints) {
                return Result.failure(Exception("Pontos insuficientes. Você precisa de ${giftCard.requiredPoints} pontos."))
            }
            val voucher = "BOA${Random.nextInt(10000, 99999)}-${giftCard.brand.take(3).uppercase()}"
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val redeemed = RedeemedGiftCard(
                userId = userId,
                giftCardId = giftCard.id,
                giftCardTitle = giftCard.title,
                voucherCode = voucher,
                redeemedAt = dateStr,
                pointsSpent = giftCard.requiredPoints
            )
            val userRef = firestore.collection("users").document(userId)
            // Deduct points atomically
            userRef.update("points", FieldValue.increment(-giftCard.requiredPoints.toLong())).await()
            // Save redemption record
            val docRef = userRef.collection("redeemedGiftCards").add(
                hashMapOf(
                    "giftCardId" to redeemed.giftCardId,
                    "giftCardTitle" to redeemed.giftCardTitle,
                    "voucherCode" to redeemed.voucherCode,
                    "redeemedAt" to redeemed.redeemedAt,
                    "pointsSpent" to redeemed.pointsSpent,
                    "userId" to userId
                )
            ).await()
            Result.success(redeemed.copy(id = docRef.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRedeemedCards(userId: String): Result<List<RedeemedGiftCard>> {
        return try {
            val snapshot = firestore.collection("users").document(userId)
                .collection("redeemedGiftCards")
                .orderBy("redeemedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()
            val list = snapshot.documents.map { doc ->
                RedeemedGiftCard(
                    id = doc.id,
                    userId = userId,
                    giftCardId = doc.getString("giftCardId") ?: "",
                    giftCardTitle = doc.getString("giftCardTitle") ?: "",
                    voucherCode = doc.getString("voucherCode") ?: "",
                    redeemedAt = doc.getString("redeemedAt") ?: "",
                    pointsSpent = (doc.getLong("pointsSpent") ?: 0L).toInt()
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
