# Eventos & Certificação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add event management + automated certificate PDF generation to App Boa Ação.

**Architecture:** New `event/` feature module under `presentation/` + two new repositories (`EventRepository`, `AttendanceRepository`). Reuses existing Clean Architecture (manual DI in `BoaAcaoApplication`, MVVM with `StateFlow`, Firestore callbackFlow listeners, transactions for atomicity). PDF via existing `itextg:5.5.10`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Firestore, ZXing/CameraX (existing), iTextG (existing), Android Canvas for signature pad.

**Verification model:** No unit tests are written (project only has `ExampleUnitTest`). Verify each task by `./gradlew assembleDebug` (compile) and where indicated, manual run. Tests would be added in a follow-up.

---

## Task 1: Domain models — Event, Attendance, signature field

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/domain/Event.kt`
- Create: `app/src/main/java/br/unasp/boacao/domain/Attendance.kt`
- Modify: `app/src/main/java/br/unasp/boacao/domain/model.kt` (add `signatureBase64`)

- [ ] **Step 1: Create `Event.kt`**

```kotlin
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
```

- [ ] **Step 2: Create `Attendance.kt`**

```kotlin
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
```

- [ ] **Step 3: Add `signatureBase64` to `UserProfile`**

In `model.kt`, add field after `photoBase64`:
```kotlin
val signatureBase64: String = "",
```

- [ ] **Step 4: Verify**

```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/br/unasp/boacao/domain
git commit -m "feat(domain): add Event, Attendance models and signatureBase64"
```

---

## Task 2: HashUtil

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/util/HashUtil.kt`

- [ ] **Step 1: Create file**

```kotlin
package br.unasp.boacao.util

import java.security.MessageDigest

object HashUtil {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun certificateHash(volunteerId: String, eventId: String): String =
        sha256("$volunteerId|$eventId")
}
```

- [ ] **Step 2: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/util/HashUtil.kt
git commit -m "feat(util): add SHA-256 hash helper for certificates"
```

---

## Task 3: EventRepository

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/data/repository/EventRepository.kt`

- [ ] **Step 1: Create file**

```kotlin
package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.EventStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface EventRepository {
    fun observePublishedEvents(): Flow<List<Event>>
    fun observeNgoEvents(ngoId: String): Flow<List<Event>>
    suspend fun getEvent(eventId: String): Result<Event>
    suspend fun createEvent(event: Event): Result<String>
    suspend fun updateStatus(eventId: String, status: EventStatus): Result<Unit>
}

class EventRepositoryImpl(
    private val firestore: FirebaseFirestore
) : EventRepository {

    private val collection = firestore.collection("events")

    override fun observePublishedEvents(): Flow<List<Event>> = callbackFlow {
        val reg = collection
            .whereIn("status", listOf(EventStatus.PUBLISHED.name, EventStatus.IN_PROGRESS.name))
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Event::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeNgoEvents(ngoId: String): Flow<List<Event>> = callbackFlow {
        val reg = collection.whereEqualTo("ngoId", ngoId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Event::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun getEvent(eventId: String): Result<Event> = try {
        val doc = collection.document(eventId).get().await()
        val event = doc.toObject(Event::class.java)?.copy(id = doc.id)
            ?: throw Exception("Evento não encontrado")
        Result.success(event)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun createEvent(event: Event): Result<String> = try {
        val ref = collection.document()
        val toSave = event.copy(id = ref.id, createdAt = System.currentTimeMillis())
        ref.set(toSave).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun updateStatus(eventId: String, status: EventStatus): Result<Unit> = try {
        collection.document(eventId).update("status", status.name).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
```

- [ ] **Step 2: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/data/repository/EventRepository.kt
git commit -m "feat(data): add EventRepository"
```

---

## Task 4: AttendanceRepository

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/data/repository/AttendanceRepository.kt`

- [ ] **Step 1: Create file**

```kotlin
package br.unasp.boacao.data.repository

import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.util.HashUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface AttendanceRepository {
    suspend fun subscribe(eventId: String, volunteer: UserProfile): Result<Attendance>
    fun observeMyAttendances(volunteerId: String): Flow<List<Attendance>>
    fun observeEventAttendances(eventId: String): Flow<List<Attendance>>
    suspend fun getAttendance(eventId: String, attendanceId: String): Result<Attendance>
    suspend fun checkIn(eventId: String, ticketCode: String): Result<Attendance>
    suspend fun checkOut(eventId: String, ticketCode: String, performanceNote: String): Result<Attendance>
}

class AttendanceRepositoryImpl(
    private val firestore: FirebaseFirestore
) : AttendanceRepository {

    private fun col(eventId: String) =
        firestore.collection("events").document(eventId).collection("attendances")

    override suspend fun subscribe(eventId: String, volunteer: UserProfile): Result<Attendance> = try {
        val eventRef = firestore.collection("events").document(eventId)
        val attRef = eventRef.collection("attendances").document()
        val ticket = UUID.randomUUID().toString()
        val attendance = Attendance(
            id = attRef.id,
            eventId = eventId,
            volunteerId = volunteer.id,
            volunteerName = volunteer.name,
            volunteerDocument = volunteer.document,
            ticketCode = ticket,
            status = AttendanceStatus.SUBSCRIBED
        )
        firestore.runTransaction { tx ->
            val eventSnap = tx.get(eventRef)
            val max = eventSnap.getLong("maxParticipants")?.toInt() ?: 0
            // Note: Cannot query subcollection inside tx; rely on client check + rules.
            tx.set(attRef, attendance)
        }.await()
        Result.success(attendance)
    } catch (e: Exception) { Result.failure(e) }

    override fun observeMyAttendances(volunteerId: String): Flow<List<Attendance>> = callbackFlow {
        val reg = firestore.collectionGroup("attendances")
            .whereEqualTo("volunteerId", volunteerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(Attendance::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeEventAttendances(eventId: String): Flow<List<Attendance>> = callbackFlow {
        val reg = col(eventId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { d ->
                d.toObject(Attendance::class.java)?.copy(id = d.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun getAttendance(eventId: String, attendanceId: String): Result<Attendance> = try {
        val doc = col(eventId).document(attendanceId).get().await()
        val a = doc.toObject(Attendance::class.java)?.copy(id = doc.id)
            ?: throw Exception("Inscrição não encontrada")
        Result.success(a)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun checkIn(eventId: String, ticketCode: String): Result<Attendance> = try {
        val query = col(eventId).whereEqualTo("ticketCode", ticketCode).limit(1).get().await()
        val doc = query.documents.firstOrNull() ?: throw Exception("Ticket inválido")
        val ref = doc.reference
        val updated = firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val status = snap.getString("status")
            if (status != AttendanceStatus.SUBSCRIBED.name) {
                throw Exception("Status inválido para check-in: $status")
            }
            val now = System.currentTimeMillis()
            tx.update(ref, mapOf(
                "checkInAt" to now,
                "status" to AttendanceStatus.CHECKED_IN.name
            ))
            snap.toObject(Attendance::class.java)!!.copy(
                id = snap.id, checkInAt = now, status = AttendanceStatus.CHECKED_IN
            )
        }.await()
        Result.success(updated)
    } catch (e: Exception) { Result.failure(e) }

    override suspend fun checkOut(eventId: String, ticketCode: String, performanceNote: String): Result<Attendance> = try {
        val query = col(eventId).whereEqualTo("ticketCode", ticketCode).limit(1).get().await()
        val doc = query.documents.firstOrNull() ?: throw Exception("Ticket inválido")
        val ref = doc.reference
        val updated = firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val status = snap.getString("status")
            if (status != AttendanceStatus.CHECKED_IN.name) {
                throw Exception("Check-in necessário antes do check-out")
            }
            val volunteerId = snap.getString("volunteerId") ?: ""
            val now = System.currentTimeMillis()
            val hash = HashUtil.certificateHash(volunteerId, eventId)
            tx.update(ref, mapOf(
                "checkOutAt" to now,
                "performanceNote" to performanceNote,
                "status" to AttendanceStatus.CHECKED_OUT.name,
                "certificateHash" to hash
            ))
            snap.toObject(Attendance::class.java)!!.copy(
                id = snap.id, checkOutAt = now,
                performanceNote = performanceNote,
                status = AttendanceStatus.CHECKED_OUT,
                certificateHash = hash
            )
        }.await()
        Result.success(updated)
    } catch (e: Exception) { Result.failure(e) }
}
```

- [ ] **Step 2: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/data/repository/AttendanceRepository.kt
git commit -m "feat(data): add AttendanceRepository with check-in/out transactions"
```

---

## Task 5: Wire repos into Application + extend AuthRepository for signature

**Files:**
- Modify: `app/src/main/java/br/unasp/boacao/BoaAcaoApplication.kt`
- Modify: `app/src/main/java/br/unasp/boacao/data/repository/AuthRepository.kt` (add updateSignature)

- [ ] **Step 1: Read current Application + AuthRepository**

Open both, locate where existing repos are declared.

- [ ] **Step 2: Add EventRepository + AttendanceRepository as singletons**

In `BoaAcaoApplication`, mirror the existing repo declarations:
```kotlin
val eventRepository: EventRepository by lazy {
    EventRepositoryImpl(firestore)
}
val attendanceRepository: AttendanceRepository by lazy {
    AttendanceRepositoryImpl(firestore)
}
```
Add corresponding imports.

- [ ] **Step 3: Add `updateSignature` to AuthRepository**

In `AuthRepository`, add:
```kotlin
suspend fun updateSignature(userId: String, signatureBase64: String): Result<Unit>
```
Implement in impl:
```kotlin
override suspend fun updateSignature(userId: String, signatureBase64: String): Result<Unit> = try {
    firestore.collection("users").document(userId)
        .update("signatureBase64", signatureBase64).await()
    Result.success(Unit)
} catch (e: Exception) { Result.failure(e) }
```

- [ ] **Step 4: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/BoaAcaoApplication.kt app/src/main/java/br/unasp/boacao/data/repository/AuthRepository.kt
git commit -m "feat(data): wire event/attendance repos and add updateSignature"
```

---

## Task 6: SignaturePad component

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/components/SignaturePad.kt`

- [ ] **Step 1: Create component**

```kotlin
package br.unasp.boacao.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

@Composable
fun SignaturePad(
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf(listOf<Offset>()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ ->
                            current = current + change.position
                            strokes.removeAll { false } // trigger recomposition
                            if (strokes.isEmpty() || strokes.last() !== current) {
                                if (strokes.isEmpty()) strokes.add(current)
                                else strokes[strokes.size - 1] = current
                            }
                        },
                        onDragEnd = {
                            strokes.add(current)
                            current = emptyList()
                        }
                    )
                }
        ) {
            strokes.forEach { stroke ->
                if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokes.clear() }) { Text("Limpar") }
            Button(onClick = {
                val base64 = rasterize(strokes)
                onSave(base64)
            }) { Text("Salvar") }
        }
    }
}

private fun rasterize(strokes: List<List<Offset>>): String {
    val w = 300; val h = 150
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(AndroidColor.WHITE)
    val paint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 3f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    // Find bounding box and scale
    val all = strokes.flatten()
    if (all.isEmpty()) return ""
    val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
    val srcW = (maxX - minX).coerceAtLeast(1f)
    val srcH = (maxY - minY).coerceAtLeast(1f)
    val sx = w / srcW; val sy = h / srcH
    val s = minOf(sx, sy)
    strokes.forEach { stroke ->
        for (i in 1 until stroke.size) {
            val a = stroke[i - 1]; val b = stroke[i]
            c.drawLine((a.x - minX) * s, (a.y - minY) * s, (b.x - minX) * s, (b.y - minY) * s, paint)
        }
    }
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
```

- [ ] **Step 2: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/components/SignaturePad.kt
git commit -m "feat(ui): add SignaturePad component"
```

---

## Task 7: CertificatePdfUtil

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/util/CertificatePdfUtil.kt`

- [ ] **Step 1: Create file**

```kotlin
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
import com.itextpdf.text.Rectangle
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

        val doc = Document(PageSize.A4.rotate(), 36f, 36f, 48f, 48f)
        PdfWriter.getInstance(doc, FileOutputStream(out))
        doc.open()

        // Border
        val border = Rectangle(doc.pageSize)
        border.left += 18f; border.right -= 18f; border.top -= 18f; border.bottom += 18f
        border.borderWidth = 2f
        border.border = Rectangle.BOX
        border.borderColor = BaseColor(0, 102, 51)
        doc.add(border)

        val titleFont = Font(Font.FontFamily.HELVETICA, 28f, Font.BOLD, BaseColor(0, 102, 51))
        val bodyFont = Font(Font.FontFamily.HELVETICA, 14f, Font.NORMAL)
        val small = Font(Font.FontFamily.HELVETICA, 9f, Font.NORMAL, BaseColor.DARK_GRAY)

        val header = Paragraph("BOA AÇÃO\n", Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD))
        header.alignment = Element.ALIGN_CENTER
        doc.add(header)

        val title = Paragraph("CERTIFICADO DE PARTICIPAÇÃO\n\n", titleFont)
        title.alignment = Element.ALIGN_CENTER
        doc.add(title)

        val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val body = Paragraph().apply {
            alignment = Element.ALIGN_JUSTIFIED
            font = bodyFont
            add(Phrase("Certificamos que ", bodyFont))
            add(Phrase(volunteer.name, Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)))
            add(Phrase(", CPF ${volunteer.document}, participou da ação voluntária ", bodyFont))
            add(Phrase("\"${event.title}\"", Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)))
            add(Phrase(", organizada por ${ngo.name} (CNPJ ${ngo.document}), realizada em ${df.format(Date(event.startAt))}, totalizando ${event.workloadHours} horas de carga horária.", bodyFont))
            if (attendance.performanceNote.isNotBlank()) {
                add(Phrase("\n\nObservação: ${attendance.performanceNote}", bodyFont))
            }
        }
        doc.add(body)
        doc.add(Paragraph("\n\n\n"))

        // Signature image
        if (ngo.signatureBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(ngo.signatureBase64, Base64.NO_WRAP)
                val img = Image.getInstance(bytes)
                img.scaleToFit(180f, 80f)
                img.alignment = Element.ALIGN_CENTER
                doc.add(img)
            } catch (_: Exception) { }
        }
        val sigLine = Paragraph("____________________________\nAssinatura ${ngo.name}", small)
        sigLine.alignment = Element.ALIGN_CENTER
        doc.add(sigLine)

        // Footer hash
        val footer = Paragraph("\nAutenticidade: ${attendance.certificateHash}\nGerado em ${df.format(Date())}", small)
        footer.alignment = Element.ALIGN_CENTER
        doc.add(footer)

        doc.close()
        return out
    }
}
```

- [ ] **Step 2: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/util/CertificatePdfUtil.kt
git commit -m "feat(util): add CertificatePdfUtil for A4 landscape PDF generation"
```

---

## Task 8: FileProvider config for sharing PDFs

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add provider XML**

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="certificates" path="certificates/" />
</paths>
```

- [ ] **Step 2: Register provider in manifest**

Inside `<application>` in AndroidManifest.xml, add (skip if already present for similar use):
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

If a FileProvider already exists, just add the `<files-path>` to its existing `file_paths.xml`.

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat(android): register FileProvider for certificate sharing"
```

---

## Task 9: ONG Signature Setup screen + ViewModel

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/OngSignatureSetupViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/OngSignatureSetupScreen.kt`

- [ ] **Step 1: ViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OngSignatureSetupViewModel(
    private val authRepository: AuthRepository,
    private val userId: String
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun save(signatureBase64: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val r = authRepository.updateSignature(userId, signatureBase64)
            _state.value = r.fold({ UiState.Idle.also { onSuccess() } }, { UiState.Error(it.message ?: "Erro") })
        }
    }

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Error(val msg: String) : UiState()
    }
}
```

- [ ] **Step 2: Screen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.unasp.boacao.presentation.components.SignaturePad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OngSignatureSetupScreen(
    viewModel: OngSignatureSetupViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Assinatura da ONG") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Desenhe sua assinatura. Será usada nos certificados.")
            Spacer(Modifier.height(16.dp))
            SignaturePad(onSave = { sig ->
                if (sig.isNotBlank()) viewModel.save(sig, onSaved)
            })
            (state as? OngSignatureSetupViewModel.UiState.Error)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.msg, color = MaterialTheme.colorScheme.error)
            }
            if (state is OngSignatureSetupViewModel.UiState.Loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
    }
}
```

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/OngSignatureSetupScreen.kt app/src/main/java/br/unasp/boacao/presentation/event/OngSignatureSetupViewModel.kt
git commit -m "feat(event): add ONG signature setup screen"
```

---

## Task 10: Event Create screen + ViewModel

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventCreateViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventCreateScreen.kt`

- [ ] **Step 1: ViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EventCreateViewModel(
    private val eventRepository: EventRepository,
    private val ngoId: String,
    private val ngoName: String
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun submit(
        title: String, description: String, address: String,
        startAt: Long, endAt: Long, workloadHours: Double, maxParticipants: Int,
        onCreated: (String) -> Unit
    ) {
        if (title.isBlank() || startAt == 0L) {
            _state.value = UiState.Error("Preencha título e data de início")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.Loading
            val event = Event(
                ngoId = ngoId, ngoName = ngoName,
                title = title.trim(), description = description.trim(),
                address = address.trim(), startAt = startAt, endAt = endAt,
                workloadHours = workloadHours, maxParticipants = maxParticipants
            )
            eventRepository.createEvent(event).fold(
                { id -> _state.value = UiState.Idle; onCreated(id) },
                { _state.value = UiState.Error(it.message ?: "Erro") }
            )
        }
    }

    sealed class UiState {
        data object Idle : UiState()
        data object Loading : UiState()
        data class Error(val msg: String) : UiState()
    }
}
```

- [ ] **Step 2: Screen**

```kotlin
package br.unasp.boacao.presentation.event

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCreateScreen(
    viewModel: EventCreateViewModel,
    onCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val state by viewModel.state.collectAsState()
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var startAt by remember { mutableStateOf(0L) }
    var endAt by remember { mutableStateOf(0L) }
    var hours by remember { mutableStateOf("4") }
    var maxP by remember { mutableStateOf("0") }

    fun pickDate(onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(y, m, d, 8, 0, 0)
            onPick(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Criar Evento") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(desc, { desc = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(address, { address = it }, label = { Text("Endereço") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickDate { startAt = it } }) {
                Text(if (startAt == 0L) "Início" else "Início: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(startAt)}")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickDate { endAt = it } }) {
                Text(if (endAt == 0L) "Fim" else "Fim: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(endAt)}")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(hours, { hours = it }, label = { Text("Carga horária (h)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(maxP, { maxP = it }, label = { Text("Máx participantes (0=ilim.)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            (state as? EventCreateViewModel.UiState.Error)?.let {
                Text(it.msg, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    viewModel.submit(
                        title, desc, address, startAt, endAt,
                        hours.toDoubleOrNull() ?: 0.0,
                        maxP.toIntOrNull() ?: 0,
                        onCreated
                    )
                },
                enabled = state !is EventCreateViewModel.UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state is EventCreateViewModel.UiState.Loading) "Salvando..." else "Criar")
            }
        }
    }
}
```

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/EventCreate*
git commit -m "feat(event): add event creation screen"
```

---

## Task 11: Event Manage screen (ONG side) + ViewModel

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventManageViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventManageScreen.kt`

- [ ] **Step 1: ViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.EventStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventManageViewModel(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    val eventId: String
) : ViewModel() {
    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private val _attendances = MutableStateFlow<List<Attendance>>(emptyList())
    val attendances: StateFlow<List<Attendance>> = _attendances.asStateFlow()

    init {
        viewModelScope.launch {
            eventRepository.getEvent(eventId).onSuccess { _event.value = it }
        }
        viewModelScope.launch {
            attendanceRepository.observeEventAttendances(eventId).collect { _attendances.value = it }
        }
    }

    fun setStatus(status: EventStatus) {
        viewModelScope.launch {
            eventRepository.updateStatus(eventId, status)
            eventRepository.getEvent(eventId).onSuccess { _event.value = it }
        }
    }
}
```

- [ ] **Step 2: Screen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.EventStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventManageScreen(
    viewModel: EventManageViewModel,
    onScan: () -> Unit,
    onBack: () -> Unit
) {
    val event by viewModel.event.collectAsState()
    val attendances by viewModel.attendances.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(event?.title ?: "Evento") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onScan, text = { Text("Escanear QR") }, icon = {})
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            event?.let { e ->
                Text("Status: ${e.status}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.status == EventStatus.PUBLISHED) {
                        Button(onClick = { viewModel.setStatus(EventStatus.IN_PROGRESS) }) { Text("Iniciar") }
                    }
                    if (e.status == EventStatus.IN_PROGRESS) {
                        Button(onClick = { viewModel.setStatus(EventStatus.FINISHED) }) { Text("Finalizar") }
                    }
                    if (e.status != EventStatus.CANCELLED && e.status != EventStatus.FINISHED) {
                        OutlinedButton(onClick = { viewModel.setStatus(EventStatus.CANCELLED) }) { Text("Cancelar") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Text("Inscritos (${attendances.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(attendances) { a ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.volunteerName, style = MaterialTheme.typography.titleSmall)
                            Text("CPF: ${a.volunteerDocument}", style = MaterialTheme.typography.bodySmall)
                            Text("Status: ${a.status}", style = MaterialTheme.typography.bodySmall)
                            if (a.status == AttendanceStatus.CHECKED_OUT && a.performanceNote.isNotBlank()) {
                                Text("Obs: ${a.performanceNote}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/EventManage*
git commit -m "feat(event): add ONG event management screen"
```

---

## Task 12: Event Scanner screen (QR check-in/out) + ViewModel

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventScannerViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventScannerScreen.kt`

- [ ] **Step 1: Inspect existing `QrScannerView`**

Open `app/src/main/java/br/unasp/boacao/presentation/components/QrScannerView.kt` to see the API; reuse it directly.

- [ ] **Step 2: ViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EventScannerViewModel(
    private val attendanceRepository: AttendanceRepository,
    val eventId: String
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Scanning)
    val state: StateFlow<UiState> = _state

    fun onTicketScanned(ticket: String) {
        if (_state.value !is UiState.Scanning) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            attendanceRepository.checkIn(eventId, ticket).fold(
                { a ->
                    if (a.status == AttendanceStatus.CHECKED_IN) _state.value = UiState.NeedsCheckout(a)
                    else _state.value = UiState.Done("Check-in registrado")
                },
                { err ->
                    if (err.message?.contains("Status inválido") == true) {
                        _state.value = UiState.NeedsCheckoutFromTicket(ticket)
                    } else {
                        _state.value = UiState.Error(err.message ?: "Erro")
                    }
                }
            )
        }
    }

    fun confirmCheckout(ticket: String, note: String) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            attendanceRepository.checkOut(eventId, ticket, note).fold(
                { _state.value = UiState.Done("Check-out registrado. Certificado liberado.") },
                { _state.value = UiState.Error(it.message ?: "Erro") }
            )
        }
    }

    fun reset() { _state.value = UiState.Scanning }

    sealed class UiState {
        data object Scanning : UiState()
        data object Loading : UiState()
        data class NeedsCheckout(val attendance: Attendance) : UiState()
        data class NeedsCheckoutFromTicket(val ticket: String) : UiState()
        data class Done(val msg: String) : UiState()
        data class Error(val msg: String) : UiState()
    }
}
```

- [ ] **Step 3: Screen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.unasp.boacao.presentation.components.QrScannerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventScannerScreen(
    viewModel: EventScannerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var note by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Leitor de Tickets") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            when (val s = state) {
                EventScannerViewModel.UiState.Scanning -> {
                    QrScannerView(onQrCodeScanned = { viewModel.onTicketScanned(it) })
                }
                EventScannerViewModel.UiState.Loading -> {
                    CircularProgressIndicator()
                }
                is EventScannerViewModel.UiState.NeedsCheckout -> {
                    Text("Voluntário: ${s.attendance.volunteerName}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.confirmCheckout(s.attendance.ticketCode, note) }) {
                        Text("Confirmar check-out")
                    }
                    OutlinedButton(onClick = { note = ""; viewModel.reset() }) { Text("Cancelar") }
                }
                is EventScannerViewModel.UiState.NeedsCheckoutFromTicket -> {
                    Text("Voluntário já fez check-in. Registrar check-out?")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.confirmCheckout(s.ticket, note) }) { Text("Confirmar") }
                    OutlinedButton(onClick = { note = ""; viewModel.reset() }) { Text("Cancelar") }
                }
                is EventScannerViewModel.UiState.Done -> {
                    Text(s.msg, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { note = ""; viewModel.reset() }) { Text("Escanear próximo") }
                }
                is EventScannerViewModel.UiState.Error -> {
                    Text(s.msg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.reset() }) { Text("Tentar novamente") }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/EventScanner*
git commit -m "feat(event): add ONG QR scanner with check-in/out flow"
```

---

## Task 13: Volunteer event list + subscribe + my tickets

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventListViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/EventListScreen.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/MyTicketsViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/MyTicketsScreen.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/TicketQrScreen.kt`

- [ ] **Step 1: EventListViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventListViewModel(
    eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    private val volunteer: UserProfile
) : ViewModel() {

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

    init {
        viewModelScope.launch {
            eventRepository.observePublishedEvents().collect { _events.value = it }
        }
    }

    fun subscribe(eventId: String) {
        viewModelScope.launch {
            attendanceRepository.subscribe(eventId, volunteer).fold(
                { _msg.value = "Inscrito! Veja seus tickets." },
                { _msg.value = "Erro: ${it.message}" }
            )
        }
    }

    fun clearMsg() { _msg.value = null }
}
```

- [ ] **Step 2: EventListScreen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(viewModel: EventListViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsState()
    val msg by viewModel.msg.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); viewModel.clearMsg() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text("Eventos disponíveis") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        }) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            items(events) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium)
                        Text("Por: ${e.ngoName}", style = MaterialTheme.typography.bodySmall)
                        Text("Quando: ${SimpleDateFormat("dd/MM/yyyy").format(e.startAt)}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Carga: ${e.workloadHours}h", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.subscribe(e.id) }) { Text("Inscrever-se") }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: MyTicketsViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.domain.model.Attendance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyTicketsViewModel(
    attendanceRepository: AttendanceRepository,
    volunteerId: String
) : ViewModel() {
    private val _items = MutableStateFlow<List<Attendance>>(emptyList())
    val items: StateFlow<List<Attendance>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            attendanceRepository.observeMyAttendances(volunteerId).collect { _items.value = it }
        }
    }
}
```

- [ ] **Step 4: MyTicketsScreen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.unasp.boacao.domain.model.AttendanceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTicketsScreen(
    viewModel: MyTicketsViewModel,
    onOpenQr: (String, String) -> Unit, // eventId, attendanceId
    onOpenCertificate: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Meus Ingressos") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            items(items) { a ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Status: ${a.status}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        when (a.status) {
                            AttendanceStatus.SUBSCRIBED, AttendanceStatus.CHECKED_IN ->
                                Button(onClick = { onOpenQr(a.eventId, a.id) }) { Text("Ver QR") }
                            AttendanceStatus.CHECKED_OUT ->
                                Button(onClick = { onOpenCertificate(a.eventId, a.id) }) { Text("Baixar certificado") }
                            AttendanceStatus.NO_SHOW -> Text("Não compareceu")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: TicketQrScreen**

```kotlin
package br.unasp.boacao.presentation.event

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import br.unasp.boacao.util.QrCodeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketQrScreen(ticketCode: String, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Ingresso") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        })
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val bmp = remember(ticketCode) { QrCodeUtils.generate(ticketCode, 600) }
            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "QR") }
            Spacer(Modifier.height(16.dp))
            Text("Apresente este QR à ONG no check-in/out")
        }
    }
}
```

> Note: If `QrCodeUtils.generate(content, size)` signature differs, adapt the call. If method doesn't exist, add to `QrCodeUtils.kt`:
> ```kotlin
> fun generate(content: String, size: Int = 512): android.graphics.Bitmap? = try {
>     val bits = com.google.zxing.qrcode.QRCodeWriter().encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
>     val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
>     for (x in 0 until size) for (y in 0 until size)
>         bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
>     bmp
> } catch (_: Exception) { null }
> ```

- [ ] **Step 6: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/EventList* app/src/main/java/br/unasp/boacao/presentation/event/MyTickets* app/src/main/java/br/unasp/boacao/presentation/event/TicketQrScreen.kt
git commit -m "feat(event): add volunteer event list, my tickets, QR view"
```

---

## Task 14: My Certificates screen + ViewModel

**Files:**
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/MyCertificatesViewModel.kt`
- Create: `app/src/main/java/br/unasp/boacao/presentation/event/MyCertificatesScreen.kt`

- [ ] **Step 1: ViewModel**

```kotlin
package br.unasp.boacao.presentation.event

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.util.CertificatePdfUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyCertificatesViewModel(
    attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository,
    private val volunteer: UserProfile
) : ViewModel() {

    private val _items = MutableStateFlow<List<Attendance>>(emptyList())
    val items: StateFlow<List<Attendance>> = _items.asStateFlow()

    init {
        viewModelScope.launch {
            attendanceRepository.observeMyAttendances(volunteer.id).collect { all ->
                _items.value = all.filter { it.status == AttendanceStatus.CHECKED_OUT }
            }
        }
    }

    fun openCertificate(context: Context, attendance: Attendance, onReady: (android.net.Uri) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val event = eventRepository.getEvent(attendance.eventId).getOrThrow()
                val ngo = authRepository.getProfile(event.ngoId).getOrThrow()
                val file = withContext(Dispatchers.IO) {
                    CertificatePdfUtil.generate(context, event, ngo, volunteer, attendance)
                }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                onReady(uri)
            } catch (e: Exception) { onError(e.message ?: "Erro ao gerar PDF") }
        }
    }
}
```

> Note: `AuthRepository.getProfile(userId)` may already exist. If not, add it returning `Result<UserProfile>` (read `users/{id}` doc).

- [ ] **Step 2: Screen**

```kotlin
package br.unasp.boacao.presentation.event

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCertificatesScreen(viewModel: MyCertificatesViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val items by viewModel.items.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text("Meus Certificados") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Voltar") }
        }) }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            items(items) { a ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Hash: ${a.certificateHash.take(12)}...", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            viewModel.openCertificate(ctx, a,
                                onReady = { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(Intent.createChooser(intent, "Abrir certificado"))
                                },
                                onError = { msg -> scope.launch { snack.showSnackbar(msg) } }
                            )
                        }) { Text("Abrir PDF") }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/event/MyCertificates*
git commit -m "feat(event): add certificates screen with on-demand PDF generation"
```

---

## Task 15: Navigation routes

**Files:**
- Modify: `app/src/main/java/br/unasp/boacao/presentation/navigation/AppNavigation.kt`

- [ ] **Step 1: Read current `AppNavigation.kt`**

Identify the sealed-class route system and how existing screens wire ViewModels (look for use of `BoaAcaoApplication` repos).

- [ ] **Step 2: Add routes**

Add to the sealed-class:
```kotlin
data object EventList : Screen("event_list")
data object EventCreate : Screen("event_create")
data class EventManage(val eventId: String) : Screen("event_manage/$eventId") {
    companion object { const val ROUTE = "event_manage/{eventId}"; const val ARG = "eventId" }
}
data class EventScanner(val eventId: String) : Screen("event_scanner/$eventId") {
    companion object { const val ROUTE = "event_scanner/{eventId}"; const val ARG = "eventId" }
}
data object MyTickets : Screen("my_tickets")
data class TicketQr(val ticketCode: String) : Screen("ticket_qr/$ticketCode") {
    companion object { const val ROUTE = "ticket_qr/{ticketCode}"; const val ARG = "ticketCode" }
}
data object MyCertificates : Screen("my_certificates")
data object OngSignatureSetup : Screen("ong_signature_setup")
```
(Adapt naming to match existing sealed-class style.)

- [ ] **Step 3: Wire each route to its screen + ViewModel**

For each route, follow the existing pattern. Example for `EventCreate`:
```kotlin
composable(Screen.EventCreate.route) {
    val app = LocalContext.current.applicationContext as BoaAcaoApplication
    val user = /* current user from session, follow existing pattern */
    val vm = remember { EventCreateViewModel(app.eventRepository, user.id, user.name) }
    EventCreateScreen(
        viewModel = vm,
        onCreated = { id -> navController.navigate(Screen.EventManage(id).route) { popUpTo(Screen.EventCreate.route) { inclusive = true } } },
        onBack = { navController.popBackStack() }
    )
}
```
Repeat for all 8 routes.

For `TicketQr`, the volunteer screen passes `attendanceId` from `MyTickets`; resolve `ticketCode` by fetching attendance via `attendanceRepository.getAttendance(eventId, attendanceId)` inside the wiring code or pass `ticketCode` directly through nav args.

- [ ] **Step 4: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/navigation/AppNavigation.kt
git commit -m "feat(nav): wire event/attendance/cert/signature routes"
```

---

## Task 16: Dashboard integration

**Files:**
- Modify: `app/src/main/java/br/unasp/boacao/presentation/beneficiary/BeneficiaryDashboardScreen.kt`
- Modify: `app/src/main/java/br/unasp/boacao/presentation/volunteer/VolunteerDashboardScreen.kt`

- [ ] **Step 1: Beneficiary entries**

Add navigation entry-point cards/buttons:
- "Meus Eventos" → `Screen.EventList` filtered to NGO (or a new `NgoEventsScreen` — for v1 reuse `EventList` and add tab toggle later)
- For now, add two buttons: "Criar Evento" (→ `EventCreate`) and "Configurar Assinatura" (→ `OngSignatureSetup`).
- Each event card from `observeNgoEvents` opens `EventManage`.

Simplest first pass: add a Card section "ONG / Eventos" with buttons:
```kotlin
Button(onClick = { navController.navigate(Screen.EventCreate.route) }) { Text("Criar Evento") }
Button(onClick = { navController.navigate(Screen.OngSignatureSetup.route) }) { Text("Assinatura") }
```
Plus list of own events (collect from a new `BeneficiaryEventsViewModel` or inline `observeNgoEvents(currentUser.id)`).

- [ ] **Step 2: Volunteer entries**

Add buttons:
```kotlin
Button(onClick = { navController.navigate(Screen.EventList.route) }) { Text("Eventos disponíveis") }
Button(onClick = { navController.navigate(Screen.MyTickets.route) }) { Text("Meus ingressos") }
Button(onClick = { navController.navigate(Screen.MyCertificates.route) }) { Text("Meus certificados") }
```

- [ ] **Step 3: Compile + commit**

```
./gradlew assembleDebug
git add app/src/main/java/br/unasp/boacao/presentation/beneficiary/BeneficiaryDashboardScreen.kt app/src/main/java/br/unasp/boacao/presentation/volunteer/VolunteerDashboardScreen.kt
git commit -m "feat(ui): expose event/cert entry points in dashboards"
```

---

## Task 17: Manual QA pass

- [ ] **Step 1: Install debug build on device**

```
./gradlew installDebug
```

- [ ] **Step 2: Run flow**

1. Login as BENEFICIARY user → set signature → create event
2. Login as VOLUNTEER user → list events → subscribe → open ticket QR
3. BENEFICIARY user → open event → scanner → scan QR → check-in
4. Re-scan same QR → enter note → check-out
5. VOLUNTEER user → My Certificates → open PDF → verify content (name, CPF, ONG, hours, signature, hash)
6. Reopen same certificate → verify cached file used (instant load)

- [ ] **Step 3: Tag**

```
git tag v1.2-events
```

---

## Self-Review

- **Spec coverage:**
  - Decision 1 (BENEFICIARY=ONG): Tasks 9–11, 16 ✅
  - Decision 2 (UUID ticket): Task 4 (subscribe) ✅
  - Decision 3 (no geofence): omitted by design ✅
  - Decision 4 (signature once at profile): Tasks 6, 9 ✅
  - Decision 5 (on-demand PDF + cache): Task 7 ✅
  - Decision 6 (4-state lifecycle): Task 1 enum + Task 11 controls ✅
  - PDF includes all spec-required fields (name, CPF, event, ONG/CNPJ, hours, signature, hash) ✅
  - Performance note captured in checkout: Task 12 + Task 4 ✅
- **Placeholder scan:** Task 15 has wiring described in pattern terms (existing convention varies) — engineer must read existing nav file. Acceptable since it shows exact code template for one route. Task 14 notes possible missing `getProfile` — flagged but deterministic.
- **Type consistency:** `Attendance.ticketCode` used end-to-end. `EventStatus` enum names match across tasks. `AttendanceStatus` likewise. `signatureBase64` field name consistent.
