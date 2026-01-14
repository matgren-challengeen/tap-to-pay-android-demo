# Venloop Tap-to-Pay

Android application for the Venloop smart vending machine. Users tap their payment card to authenticate, unlock the door, and shop. The system uses Stripe Terminal SDK for card reading and Firebase Firestore for real-time session updates.

## Features

- **Card-based Login**: Tap to authenticate (via Stripe SetupIntent flow)
- **Guest Support**: New cards automatically create guest accounts
- **Real-time Shopping**: Live cart updates via Firebase Firestore
- **Native UI**: Connect Reader → Shopping → Summary screens

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Stripe Terminal│────▶│   Stub Backend  │────▶│    Firestore    │
│      SDK        │     │  (or Medusa)    │     │   (real-time)   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │                                               │
         ▼                                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ MainActivity │  │  Repositories│  │  ViewModels  │          │
│  │ (Stripe SDK) │  │  (Firebase)  │  │  (LiveData)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
```

## Requirements

- **Android Studio** Iguana or newer
- **JDK 17** (required - JDK 25 causes KAPT issues)
- **Kotlin 2.2.0**
- Physical Android device with NFC (Tap to Pay requires real hardware)

## Setup

### 1. Clone & Configure

```bash
git clone <repo-url>
cd tap-to-pay-venloop
```

Edit `gradle.properties`:
```properties
EXAMPLE_BACKEND_URL="http://YOUR_BACKEND_URL:3000/"
```

### 2. Firebase Setup

Place your `google-services.json` in `app/` directory.

### 3. Stripe Location

Create at least one [Terminal Location](https://dashboard.stripe.com/test/terminal/locations) in your Stripe Dashboard (**Test Mode**).

### 4. Start Backend

In a **separate terminal**, start the stub backend:

```bash
cd tap-to-pay-stub-backend
npm install        # First time only
npm run dev
```

The backend must be running at `http://YOUR_IP:3000` for the app to connect.

### 5. Build & Run

```bash
./gradlew assembleDebug
```

Or open in Android Studio and run on a physical device.

## Flow

1. **Connect Reader** – App connects to phone's NFC (Local Mobile Reader)
2. **Tap Card** – User taps payment card
3. **SetupIntent** – Stripe captures payment method without charging
4. **Login** – Backend authenticates user by card fingerprint
5. **Shopping** – App listens to Firestore `sessions/{id}` for cart updates
6. **Summary** – Door closes → Final total displayed

## Project Structure

```
app/src/main/java/com/example/taptopayandroid/
├── MainActivity.kt          # Stripe Terminal + login flow
├── ApiClient.kt             # Backend API calls
├── BackendService.kt        # Retrofit interface
├── fragments/
│   ├── ConnectReaderFragment.kt
│   ├── ShoppingFragment.kt
│   └── SummaryFragment.kt
├── repository/
│   ├── SessionRepository.kt  # Firestore listener
│   └── CartRepository.kt     # Medusa cart API
├── viewmodel/
│   └── ShoppingViewModel.kt  # MVVM state management
└── models/
    ├── Cart.kt
    └── StoreCartResponse.kt
```

## Backend

For development, use `tap-to-pay-stub-backend` – a Node.js simulator that:
- Generates real Stripe `SetupIntent` secrets
- Mocks login responses
- Writes to Firestore to simulate hardware events

For production, connect to the Medusa backend with the `venloop-pos-plugin`.

## Documentation

See `/docs` for detailed specs:
- `implementation_plan.md` – Technical architecture
- `requirements_prd.md` – Product requirements
- `task.md` – Implementation checklist

## License

Proprietary – Venloop
