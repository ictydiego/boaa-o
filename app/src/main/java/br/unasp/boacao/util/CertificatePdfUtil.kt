package br.unasp.boacao.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import br.unasp.boacao.R
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.UserProfile
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CertificatePdfUtil {

    private val ORANGE = BaseColor(240, 106, 56)
    private val DARK_TEXT = BaseColor(33, 33, 33)
    private val GRAY_TEXT = BaseColor(96, 96, 96)

    fun generate(
        context: Context,
        event: Event,
        ngo: UserProfile,
        volunteer: UserProfile,
        attendance: Attendance
    ): File {
        val dir = File(context.filesDir, "certificates").apply { mkdirs() }
        val out = File(dir, "${attendance.id}.pdf")
        if (out.exists() && out.length() > 0) return out

        val doc = Document(PageSize.A4.rotate(), 56f, 56f, 64f, 64f)
        val writer = PdfWriter.getInstance(doc, FileOutputStream(out))
        writer.pageEvent = OrangeBorderPageEvent()
        doc.open()

        val titleFont = Font(Font.FontFamily.HELVETICA, 32f, Font.BOLD, ORANGE)
        val bodyFont = Font(Font.FontFamily.HELVETICA, 14f, Font.NORMAL, DARK_TEXT)
        val boldFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD, DARK_TEXT)
        val small = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, GRAY_TEXT)
        val brandFont = Font(Font.FontFamily.HELVETICA, 13f, Font.BOLD, ORANGE)

        // Logo top-left + brand text
        val logoBytes = loadLauncherIcon(context)
        if (logoBytes != null) {
            try {
                val img = Image.getInstance(logoBytes)
                img.scaleAbsolute(60f, 60f)
                img.setAbsolutePosition(64f, doc.pageSize.height - 92f)
                doc.add(img)
            } catch (_: Exception) { }
        }

        // Brand title centered
        val brand = Paragraph("BOA AÇÃO", brandFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 8f
        }
        doc.add(brand)
        doc.add(Paragraph(" "))

        val title = Paragraph("CERTIFICADO DE PARTICIPAÇÃO", titleFont).apply {
            alignment = Element.ALIGN_CENTER
            spacingAfter = 24f
        }
        doc.add(title)

        val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val cpfFormatted = FormatUtils.formatDocument(volunteer.document)
        val cnpjFormatted = FormatUtils.formatDocument(ngo.document)
        val hoursFormatted = FormatUtils.formatHours(event.workloadHours)

        val body = Paragraph().apply {
            alignment = Element.ALIGN_JUSTIFIED
            font = bodyFont
            add(Phrase("Certificamos que ", bodyFont))
            add(Phrase(volunteer.name, boldFont))
            add(Phrase(", inscrito(a) sob o CPF ", bodyFont))
            add(Phrase(cpfFormatted, boldFont))
            add(Phrase(", participou da ação voluntária ", bodyFont))
            add(Phrase("\"${event.title}\"", boldFont))
            add(Phrase(", organizada por ", bodyFont))
            add(Phrase(ngo.name, boldFont))
            add(Phrase(" (CNPJ ", bodyFont))
            add(Phrase(cnpjFormatted, boldFont))
            add(Phrase("), realizada em ", bodyFont))
            add(Phrase(df.format(Date(event.startAt)), boldFont))
            add(Phrase(", totalizando ", bodyFont))
            add(Phrase("$hoursFormatted hora(s)", boldFont))
            add(Phrase(" de carga horária.", bodyFont))
            if (attendance.performanceNote.isNotBlank()) {
                add(Phrase("\n\nObservação da organização: ${attendance.performanceNote}", bodyFont))
            }
        }
        doc.add(body)
        doc.add(Paragraph("\n\n"))

        if (ngo.signatureBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(ngo.signatureBase64, Base64.NO_WRAP)
                val img = Image.getInstance(bytes)
                img.scaleToFit(180f, 80f)
                img.alignment = Element.ALIGN_CENTER
                doc.add(img)
            } catch (_: Exception) {
                doc.add(Paragraph("\n"))
            }
        } else {
            doc.add(Paragraph("\n"))
        }

        val sigLine = Paragraph("____________________________\n${ngo.name}", small).apply {
            alignment = Element.ALIGN_CENTER
        }
        doc.add(sigLine)

        val footer = Paragraph(
            "\nAutenticidade: ${attendance.certificateHash}\nGerado em ${df.format(Date())}",
            small
        ).apply {
            alignment = Element.ALIGN_CENTER
        }
        doc.add(footer)

        doc.close()
        return out
    }

    private fun loadLauncherIcon(context: Context): ByteArray? {
        return try {
            val bmp = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
                ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        } catch (_: Exception) { null }
    }

    /**
     * Draws orange L-shaped corner ornaments on each page.
     */
    private class OrangeBorderPageEvent : PdfPageEventHelper() {
        override fun onEndPage(writer: PdfWriter, document: Document) {
            val cb: PdfContentByte = writer.directContent
            val ps = document.pageSize
            val margin = 24f
            val len = 50f
            val thickness = 3f

            cb.saveState()
            cb.setColorStroke(ORANGE)
            cb.setLineWidth(thickness)

            // Top-left
            cb.moveTo(margin, ps.height - margin - len)
            cb.lineTo(margin, ps.height - margin)
            cb.lineTo(margin + len, ps.height - margin)
            cb.stroke()
            // Top-right
            cb.moveTo(ps.width - margin - len, ps.height - margin)
            cb.lineTo(ps.width - margin, ps.height - margin)
            cb.lineTo(ps.width - margin, ps.height - margin - len)
            cb.stroke()
            // Bottom-left
            cb.moveTo(margin, margin + len)
            cb.lineTo(margin, margin)
            cb.lineTo(margin + len, margin)
            cb.stroke()
            // Bottom-right
            cb.moveTo(ps.width - margin - len, margin)
            cb.lineTo(ps.width - margin, margin)
            cb.lineTo(ps.width - margin, margin + len)
            cb.stroke()

            cb.restoreState()
        }
    }
}
