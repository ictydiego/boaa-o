# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew build                  # Full build (all variants)
./gradlew clean                  # Clean build artifacts
./gradlew lint                   # Run lint checks
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)

# Run a specific test class
./gradlew test --tests "br.unasp.boacao.ExampleUnitTest"

# Firebase App Distribution
./gradlew appDistributionUploadDebug
```

## Architecture

**Clean Architecture** with three layers:

- **`data/`** — Repositories that interface with Firebase (Auth, Firestore, Storage). `BoaAcaoApplication` initializes all repositories at startup and holds them as singletons (manual DI, no Hilt/Dagger).
- **`domain/`** — Plain Kotlin data models (e.g. `Donation`, `model.kt`).
- **`presentation/`** — Jetpack Compose screens and ViewModels, organized by feature: `login/`, `donor/`, `volunteer/`, `beneficiary/`, `main/`. Navigation is centralized in `navigation/AppNavigation.kt` using a sealed class route system (Login is the start destination).

**UI layer:** Compose + Material3, no XML layouts. Reusable components live in `presentation/components/`.

**State management:** MVVM — ViewModels expose state via `StateFlow`/`LiveData`, consumed by Compose screens.

## Key Tech Stack

| Concern | Library |
|---|---|
| UI | Jetpack Compose (BOM 2024.06.00), Material3 |
| Backend | Firebase Auth, Firestore, Storage (BOM 33.1.0) |
| Local DB | Room 2.6.1 (KSP for code generation) |
| Async | Kotlin Coroutines 1.8.1 |
| Navigation | Compose Navigation 2.9.7 |
| Image loading | Coil, Lottie |
| OCR / Scanning | ML Kit Text Recognition, Barcode Scanning |
| PDF | iTextG, PDFBox Android |
| Charts | MPAndroidChart (JitPack) |

## Product Flavors

Two flavors: `demo` and `full` (dimension: `"version"`). Use `assembleDemo` / `assembleFull` to target a specific flavor.

## Project Configuration

- **Package:** `br.unasp.boacao`
- **Min SDK:** 26 (Android 8.0) — **Target/Compile SDK:** 35
- **Java/Kotlin target:** 17
- **Gradle:** 8.11.1, AGP 8.10.1, Kotlin 1.9.25
- **`google-services.json`** must be present in `app/` for Firebase to work (not committed to VCS; obtain from Firebase Console).
- Version catalog is at `gradle/libs.versions.toml` — add new dependencies there.
