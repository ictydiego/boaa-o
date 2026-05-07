package br.unasp.boacao.util

import android.content.Context
import android.util.Base64
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
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CertificatePdfUtil {

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

        val doc = Document(PageSize.A4.rotate(), 48f, 48f, 56f, 56f)
        PdfWriter.getInstance(doc, FileOutputStream(out))
        doc.open()

        val titleFont = Font(Font.FontFamily.HELVETICA, 28f, Font.BOLD, BaseColor(0, 102, 51))
        val bodyFont = Font(Font.FontFamily.HELVETICA, 14f, Font.NORMAL)
        val boldFont = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)
        val small = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.DARK_GRAY)
        val brandFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD, BaseColor(0, 102, 51))

        val header = Paragraph("BOA AÇÃO\n", brandFont)
        header.alignment = Element.ALIGN_CENTER
        doc.add(header)

        doc.add(Paragraph("\n"))

        val title = Paragraph("CERTIFICADO DE PARTICIPAÇÃO\n\n", titleFont)
        title.alignment = Element.ALIGN_CENTER
        doc.add(title)

        val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val body = Paragraph().apply {
            alignment = Element.ALIGN_JUSTIFIED
            font = bodyFont
            add(Phrase("Certificamos que ", bodyFont))
            add(Phrase(volunteer.name, boldFont))
            add(Phrase(", inscrito(a) sob o CPF ${volunteer.document}, participou da ação voluntária ", bodyFont))
            add(Phrase("\"${event.title}\"", boldFont))
            add(Phrase(", organizada por ${ngo.name} (CNPJ ${ngo.document}), realizada em ${df.format(Date(event.startAt))}, totalizando ${event.workloadHours} horas de carga horária.", bodyFont))
            if (attendance.performanceNote.isNotBlank()) {
                add(Phrase("\n\nObservação da organização: ${attendance.performanceNote}", bodyFont))
            }
        }
        doc.add(body)
        doc.add(Paragraph("\n\n\n"))

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
        val sigLine = Paragraph("____________________________\n${ngo.name}", small)
        sigLine.alignment = Element.ALIGN_CENTER
        doc.add(sigLine)

        val footer = Paragraph(
            "\nAutenticidade: ${attendance.certificateHash}\nGerado em ${df.format(Date())}",
            small
        )
        footer.alignment = Element.ALIGN_CENTER
        doc.add(footer)

        doc.close()
        return out
    }
}
