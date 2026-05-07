package br.unasp.boacao.presentation.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor

@Composable
fun SignaturePad(
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var current by remember { mutableStateOf(listOf<Offset>()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> current = listOf(offset) },
                        onDrag = { change, _ ->
                            current = current + change.position
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) {
                                strokes = strokes + listOf(current)
                                current = emptyList()
                            }
                        }
                    )
                }
        ) {
            (strokes + listOf(current)).forEach { stroke ->
                if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Black, style = Stroke(width = 4f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                strokes = emptyList()
                current = emptyList()
            }) { Text("Limpar") }
            Button(
                onClick = {
                    val base64 = rasterize(strokes)
                    if (base64.isNotBlank()) onSave(base64)
                },
                enabled = strokes.isNotEmpty()
            ) { Text("Salvar") }
        }
    }
}

private fun rasterize(strokes: List<List<Offset>>): String {
    val w = 300
    val h = 150
    val all = strokes.flatten()
    if (all.isEmpty()) return ""
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    val minX = all.minOf { it.x }
    val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }
    val maxY = all.maxOf { it.y }
    val srcW = (maxX - minX).coerceAtLeast(1f)
    val srcH = (maxY - minY).coerceAtLeast(1f)
    val s = minOf(w / srcW, h / srcH) * 0.9f
    val offsetX = (w - srcW * s) / 2f
    val offsetY = (h - srcH * s) / 2f
    strokes.forEach { stroke ->
        for (i in 1 until stroke.size) {
            val a = stroke[i - 1]
            val b = stroke[i]
            canvas.drawLine(
                offsetX + (a.x - minX) * s, offsetY + (a.y - minY) * s,
                offsetX + (b.x - minX) * s, offsetY + (b.y - minY) * s,
                paint
            )
        }
    }
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
