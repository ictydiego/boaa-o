package br.unasp.boacao.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object MarkerUtils {

    /** Ícone de casinha verde para pins de Doadores */
    fun createDonorMarker(): BitmapDescriptor {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Círculo de fundo verde
        paint.color = android.graphics.Color.parseColor("#FF4CAF50")
        canvas.drawCircle(48f, 48f, 44f, paint)
        // Borda branca
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(48f, 48f, 44f, paint)

        // Casa - telhado (triângulo)
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        val roof = Path()
        roof.moveTo(48f, 14f)
        roof.lineTo(20f, 42f)
        roof.lineTo(76f, 42f)
        roof.close()
        canvas.drawPath(roof, paint)

        // Corpo da casa
        canvas.drawRect(24f, 42f, 72f, 74f, paint)

        // Porta (verde escura)
        paint.color = android.graphics.Color.parseColor("#FF2E7D32")
        canvas.drawRect(40f, 56f, 56f, 74f, paint)

        // Janela esquerda
        paint.color = android.graphics.Color.parseColor("#FF81C784")
        canvas.drawRect(28f, 48f, 38f, 58f, paint)

        // Janela direita
        canvas.drawRect(58f, 48f, 68f, 58f, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /** Ícone de prédio azul para pins de ONGs (Beneficiários) */
    fun createNgoMarker(): BitmapDescriptor {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Círculo de fundo azul
        paint.color = android.graphics.Color.parseColor("#FF1976D2")
        canvas.drawCircle(48f, 48f, 44f, paint)
        // Borda branca
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(48f, 48f, 44f, paint)

        paint.style = Paint.Style.FILL

        // Corpo do prédio
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(22f, 28f, 74f, 74f, paint)

        // Topo do prédio (retângulo estreito)
        canvas.drawRect(34f, 18f, 62f, 30f, paint)

        // Janelas (azul escuro)
        paint.color = android.graphics.Color.parseColor("#FF1565C0")
        canvas.drawRect(28f, 34f, 40f, 46f, paint)
        canvas.drawRect(56f, 34f, 68f, 46f, paint)
        canvas.drawRect(28f, 52f, 40f, 64f, paint)
        canvas.drawRect(56f, 52f, 68f, 64f, paint)

        // Porta central
        paint.color = android.graphics.Color.parseColor("#FF0D47A1")
        canvas.drawRect(40f, 58f, 56f, 74f, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
