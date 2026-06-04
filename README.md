<div align="center">

<img src="app/src/main/res/drawable/logo.png" width="120" alt="Boa Ação logo" />

# Boa Ação

**Transforme boas intenções em ações reais.**

App Android que conecta doadores, voluntários e ONGs — com certificados digitais que valem como horas complementares na faculdade.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Auth%20%7C%20Firestore%20%7C%20Storage-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![License](https://img.shields.io/badge/License-Acad%C3%AAmico-f06a38)](#-licença)

🌐 **Landing:** [boaacao-5a414.web.app](https://boaacao-5a414.web.app)
📥 **APK:** [Boa_Acao_1.3.apk](https://github.com/ictydiego/boaa-o/releases/download/BA_V1.1.3/Boa_Acao_1.3.apk)

</div>

---

## ✨ O que o Boa Ação faz

O Boa Ação é um aplicativo Android que aproxima três personagens:

| 🎁 **Doador** | 🙌 **Voluntário** | 🏢 **ONG** |
|---|---|---|
| Doa itens ou recursos | Encontra ações no mapa, ajuda, ganha pontos | Recebe doações e organiza eventos |
| Acompanha o destino da doação | Resgata gift cards e participa de eventos | Emite certificados digitais assinados |
| Histórico completo | Recebe certificado válido como hora complementar | Faz check-in/check-out via QR Code |

---

## 🎯 Diferencial: Eventos & Certificação para alunos

ONGs publicam eventos (uma feijoada beneficente, um mutirão de limpeza, uma campanha de agasalho) com vagas para voluntários. Estudantes se inscrevem, participam, fazem check-in/check-out pelo QR Code do app e recebem um **certificado PDF A4 paisagem** com:

- ✍️ Assinatura digitalizada da ONG
- 🔐 Token **JWT** + hash **SHA-256** de autenticidade
- ⏱️ Carga horária real, calculada pelo tempo de presença
- 🎓 Aceito como **hora complementar** pela coordenação da faculdade

---

## 📱 Funcionalidades

- 🗺️ **Mapa de ações** próximas com geolocalização
- 📦 **Fluxo de doação** doador → voluntário → beneficiário
- 📅 **Eventos voluntários** com inscrição e QR ticket
- 📸 **Check-in/check-out** via scanner QR
- 📜 **Certificado PDF** A4 paisagem assinado
- ⭐ **Sistema de pontos** + ranking
- 🎟️ **Gift cards** resgatáveis por pontos
- 🔍 **OCR** para leitura de documentos (ML Kit)
- 🔒 **Login biométrico** (digital / face)
- 🌙 Material 3 com tema dinâmico

---

## 🛠️ Stack técnica

| Camada | Tecnologia |
|---|---|
| **Linguagem** | Kotlin 1.9.25 |
| **UI** | Jetpack Compose · Material 3 |
| **Arquitetura** | Clean Architecture · MVVM |
| **DI** | Manual (singletons em `BoaAcaoApplication`) |
| **Async** | Coroutines · Flow / StateFlow |
| **Backend** | Firebase Auth · Firestore · Storage |
| **Local** | Room 2.6.1 (KSP) |
| **Navegação** | Compose Navigation 2.9.7 |
| **Imagens** | Coil · Lottie |
| **OCR / QR** | ML Kit · CameraX |
| **PDF** | iTextG 5.5.10 |
| **Auth segura** | AndroidX Biometric · EncryptedSharedPreferences |
| **Build** | Gradle 8.11.1 · AGP 8.10.1 |

---

## 🏗️ Arquitetura

```
app/src/main/java/br/unasp/boacao/
├── data/repository/      # Firebase (Auth, Firestore, Storage)
├── domain/               # Modelos puros (Event, Attendance, Donation, ...)
├── presentation/         # Compose UI + ViewModels por feature
│   ├── login/
│   ├── donor/
│   ├── volunteer/
│   ├── beneficiary/
│   ├── event/
│   ├── giftcard/
│   ├── ranking/
│   └── navigation/       # AppNavigation.kt (rotas centralizadas)
├── security/             # BiometricCredentialStore
└── util/                 # PDF, Hash, OCR, Geocode, Formatters
```

**Três camadas Clean:**
- **data/** → repositórios que falam com Firebase
- **domain/** → modelos Kotlin puros
- **presentation/** → telas Compose + ViewModels expondo `StateFlow`

---

## 🚀 Como rodar

### Pré-requisitos
- Android Studio Koala+ (ou compatível com AGP 8.10)
- JDK 17
- Conta Firebase com projeto criado

### Setup

```bash
git clone https://github.com/ictydiego/boaa-o.git
cd boaa-o
```

1. **Firebase** — baixe seu `google-services.json` do [Firebase Console](https://console.firebase.google.com/) e coloque em `app/google-services.json`.
2. **Keystore (release)** — copie `keystore.properties.example` para `keystore.properties` e preencha com o caminho do seu `.jks`.
3. **Build**

```bash
./gradlew assembleDebug          # APK debug
./gradlew assembleRelease        # APK release assinado
./gradlew test                   # Unit tests
./gradlew lint                   # Lint
./gradlew connectedAndroidTest   # Instrumented (precisa device/emulator)
```

### Product flavors

Dois sabores no `dimension: version`:

```bash
./gradlew assembleDemo           # versão demo
./gradlew assembleFull           # versão completa
```

---

## 📦 Baixar o app

A versão pública atual está disponível como APK direto:

➡️ **[Boa_Acao_1.3.apk](https://github.com/ictydiego/boaa-o/releases/download/BA_V1.1.3/Boa_Acao_1.3.apk)** · Android 8.0+ · ~96 MB

> 📲 Em breve na Play Store.

**Instalação:**
1. Baixe o APK no celular.
2. Autorize "instalar apps de fontes desconhecidas" no aviso do navegador.
3. Se o Play Protect avisar, toque em "Instalar assim mesmo".

---

## 👥 Equipe

Projeto Integrador desenvolvido no **Centro Universitário Adventista de São Paulo — UNASP**.

| Nome | RA |
|---|---|
| Diego Gonçalves | 207839 |
| Natan Rocha Almeida | 208109 |
| Leandro de Souza Gama | 209084 |
| Matheus Cruz | 209091 |

---

## 📂 Estrutura do repositório

```
boaa-o/
├── app/                  # Código-fonte do app Android
├── gradle/               # Versões e wrapper
├── firestore.rules       # Regras de segurança Firestore
├── build.gradle.kts      # Configuração raiz
├── settings.gradle.kts
└── README.md
```

---

## 🔒 Segurança & Privacidade

- Credenciais de login armazenadas com `EncryptedSharedPreferences`.
- Autenticação biométrica via AndroidX Biometric.
- Certificados validados por hash SHA-256 + JWT.
- Regras Firestore (`firestore.rules`) limitam leitura/escrita por papel de usuário.

> ⚠️ `keystore.properties` e `*.jks` estão no `.gitignore`. **Nunca** versione segredos.

---

## 🤝 Contribuindo

Este é um projeto acadêmico. Sugestões e issues são bem-vindas — abra uma [issue](https://github.com/ictydiego/boaa-o/issues) descrevendo o que encontrou.

---

## 📄 Licença

Projeto acadêmico desenvolvido para fins educacionais no UNASP. Uso livre para estudo. Para uso comercial, entre em contato com a equipe.

---

<div align="center">

Feito com 🧡 por quem acredita que boas intenções merecem virar **boas ações**.

</div>
