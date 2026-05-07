# Eventos & Certificação — Design

**Date:** 2026-05-07
**Author:** Claude (brainstorm with @ictydiego)

## Context

App Boa Ação currently handles donation flow (donor → volunteer → beneficiary). This spec adds **event management** so beneficiary-role accounts (treated as ONGs) can create events, volunteers can subscribe, and the system issues a signed PDF certificate after attended check-out.

## Design Decisions

| # | Decision | Choice |
|---|---|---|
| 1 | ONG identity | Reuse existing `BENEFICIARY` role |
| 2 | Ticket signing | Plain UUID (no JWT). Firestore is source of truth. |
| 3 | Geofence | Skip — trust QR scan only |
| 4 | ONG signature | Captured once at profile setup, stored in `UserProfile.signatureBase64` (PNG, ≤300×150) |
| 5 | PDF persistence | On-demand generation, cached in app `filesDir`. No Firebase Storage upload. |
| 6 | Event lifecycle | `PUBLISHED → IN_PROGRESS → FINISHED → CANCELLED` |

## Domain Model

### `Event`
```kotlin
data class Event(
    val id: String = "",
    val ngoId: String = "",
    val ngoName: String = "",
    val title: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val startAt: Long = 0L,            // epoch millis
    val endAt: Long = 0L,
    val workloadHours: Double = 0.0,   // declared hours (used in certificate)
    val maxParticipants: Int = 0,      // 0 = unlimited
    val status: EventStatus = EventStatus.PUBLISHED,
    val createdAt: Long = 0L
)

enum class EventStatus { PUBLISHED, IN_PROGRESS, FINISHED, CANCELLED }
```

### `Attendance` (presença)
```kotlin
data class Attendance(
    val id: String = "",          // doc id, also = QR ticket UUID
    val eventId: String = "",
    val volunteerId: String = "",
    val volunteerName: String = "",
    val volunteerDocument: String = "", // CPF
    val ticketCode: String = "",        // UUID, embedded in QR
    val checkInAt: Long? = null,
    val checkOutAt: Long? = null,
    val performanceNote: String = "",   // ONG observation
    val status: AttendanceStatus = AttendanceStatus.SUBSCRIBED,
    val certificateHash: String = ""    // SHA-256(volunteerId + eventId), set at checkout
)

enum class AttendanceStatus { SUBSCRIBED, CHECKED_IN, CHECKED_OUT, NO_SHOW }
```

### `UserProfile` extension
Add field: `signatureBase64: String = ""` (only used by BENEFICIARY-role users).

## Firestore Layout

```
events/{eventId}                     -> Event document
events/{eventId}/attendances/{id}    -> Attendance subcollection
users/{userId}                       -> existing UserProfile (+ signatureBase64)
```

Subcollection chosen so per-event listing avoids composite indexes.

## Repository Layer

### `EventRepository`
```kotlin
interface EventRepository {
    fun observePublishedEvents(): Flow<List<Event>>
    fun observeNgoEvents(ngoId: String): Flow<List<Event>>
    suspend fun createEvent(event: Event): Result<String>
    suspend fun updateStatus(eventId: String, status: EventStatus): Result<Unit>
}
```

### `AttendanceRepository`
```kotlin
interface AttendanceRepository {
    suspend fun subscribe(eventId: String, volunteer: UserProfile): Result<Attendance>
    fun observeMyAttendances(volunteerId: String): Flow<List<Attendance>>
    fun observeEventAttendances(eventId: String): Flow<List<Attendance>>
    suspend fun checkIn(eventId: String, ticketCode: String): Result<Attendance>
    suspend fun checkOut(eventId: String, ticketCode: String, performanceNote: String): Result<Attendance>
}
```

All write ops use Firestore transactions:

- **subscribe**: tx checks `maxParticipants` not exceeded, creates Attendance with new UUID `ticketCode`.
- **checkIn**: tx finds attendance by `ticketCode`, asserts status == SUBSCRIBED, sets `checkInAt` + status = CHECKED_IN.
- **checkOut**: tx asserts status == CHECKED_IN, sets `checkOutAt`, `performanceNote`, status = CHECKED_OUT, computes `certificateHash` = SHA-256(`volunteerId|eventId`).

## UI Surfaces

### Beneficiary (ONG)
- `BeneficiaryDashboardScreen` → new tab "Eventos"
  - List own events + FAB "Criar Evento"
- `EventCreateScreen` — form (title, desc, dates, hours, address, max participants)
- `EventManageScreen` — per-event: live attendance list, "Iniciar leitura QR" button
- `EventCheckScannerScreen` — CameraX/ZXing QR reader
  - Reads ticket UUID → looks up Attendance → if SUBSCRIBED ask "Check-in?"; if CHECKED_IN show field for `performanceNote` + "Confirmar check-out"
- `OngSignatureSetupScreen` — drawing pad (Compose Canvas), saves PNG bitmap → 300×150 → Base64 → UserProfile

### Volunteer
- `VolunteerDashboardScreen` → new tab "Eventos"
- `EventListScreen` — published events, subscribe button
- `MyTicketsScreen` — list of own attendances, status badge
- `TicketQrScreen` — fullscreen QR (encodes `ticketCode`)
- `MyCertificatesScreen` — list CHECKED_OUT attendances, button "Baixar PDF" → opens cached or generates

## PDF Generation (`util/CertificatePdfUtil.kt`)

```kotlin
object CertificatePdfUtil {
    fun generate(
        context: Context,
        event: Event,
        ngo: UserProfile,
        volunteer: UserProfile,
        attendance: Attendance
    ): File
}
```

Steps:
1. Resolve cached file: `context.filesDir/certificates/{attendance.id}.pdf`. If exists, return.
2. Create A4 landscape `Document` via iTextG.
3. Add border rectangle, "Boa Ação" logo (drawable → byte[]).
4. Title "CERTIFICADO".
5. Body paragraph: name, CPF, event title, ONG name, ONG CNPJ, workload hours, dates (formatted pt-BR).
6. Decode `ngo.signatureBase64` → `Image.getInstance(bytes)` → place on right footer with thin line + "Assinatura ONG".
7. Footer: `certificateHash` text + generation timestamp.
8. Close, return file.

`FileProvider` used to share the resulting URI to system PDF viewer.

## Signature Capture (`presentation/components/SignaturePad.kt`)

Compose `Canvas` capturing `pointerInput` strokes → on save: rasterize to `Bitmap` 300×150 → PNG → Base64 → save in `UserProfile`. Reused at profile setup.

## Navigation

Add routes in `AppNavigation.kt`:
- `EventList`, `EventCreate`, `EventManage(id)`, `EventScanner(id)`, `MyTickets`, `TicketQr(attendanceId)`, `MyCertificates`, `OngSignatureSetup`.

## Error Handling

- All repo ops return `Result<T>`. ViewModel exposes `StateFlow<UiState>` (Loading/Success/Error) — same pattern as existing screens.
- Scanner failures: invalid QR → snackbar "Ticket inválido". Wrong status → toast "Já feito check-out" / "Aguardando inscrição".
- Missing ONG signature on cert request → block + redirect to `OngSignatureSetupScreen`.

## Free-Tier Considerations

- Signature compressed 300×150 PNG → typically <8KB Base64 → fits Firestore 1MB doc limit.
- PDFs cached locally, never uploaded.
- Listeners scoped per-screen, removed in `awaitClose`.
- No Firebase Storage usage added.

## Out of Scope

- Geofence validation (decision #3).
- JWT/cryptographic ticket signing (decision #2).
- Cert validation portal (web). Hash printed for future verifier.
- Push notifications for event reminders.
- Multi-language PDF.

## File Footprint (new)

```
domain/Event.kt
domain/Attendance.kt
data/repository/EventRepository.kt
data/repository/AttendanceRepository.kt
util/CertificatePdfUtil.kt
util/HashUtil.kt
presentation/components/SignaturePad.kt
presentation/event/EventListScreen.kt
presentation/event/EventListViewModel.kt
presentation/event/EventCreateScreen.kt
presentation/event/EventCreateViewModel.kt
presentation/event/EventManageScreen.kt
presentation/event/EventManageViewModel.kt
presentation/event/EventScannerScreen.kt
presentation/event/EventScannerViewModel.kt
presentation/event/MyTicketsScreen.kt
presentation/event/MyTicketsViewModel.kt
presentation/event/TicketQrScreen.kt
presentation/event/MyCertificatesScreen.kt
presentation/event/MyCertificatesViewModel.kt
presentation/event/OngSignatureSetupScreen.kt
presentation/event/OngSignatureSetupViewModel.kt
```

Edits:
- `domain/model.kt` (+`signatureBase64`)
- `BoaAcaoApplication.kt` (+repos)
- `presentation/navigation/AppNavigation.kt` (+routes)
- `presentation/beneficiary/BeneficiaryDashboardScreen.kt` (+tab/entry)
- `presentation/volunteer/VolunteerDashboardScreen.kt` (+tab/entry)
