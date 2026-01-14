# Walkthrough - Phase 2: Android App Implementation

I have completed **Phase 2** of the Venloop Tap-to-Pay project. The Android App is now connected to the Stub Backend and implements the full "Tap to Login" flow.

## 1. Key Implementations
-   **Stub Connection**: App points to `http://10.0.2.2:3000` (Localhost via Android Emulator).
-   **Login Logic**: Implemented `SetupIntent` flow.
    -   Calls `POST /store/auth/prepare-setup` -> `POST /store/auth/login-by-card`.
-   **UI Flow**:
    -   `ConnectReaderFragment`: "Tap to Login" button (simulates tap if reader connected, or simple trigger).
    -   `ShoppingFragment`: Shows real-time cart updates from Firestore.
    -   `SummaryFragment`: Shows "Thank You" and total when session completes.
-   **Data Layer**:
    -   `SessionRepository`: Listens to Firestore `sessions/{id}`.
    -   `CartRepository`: Fetches cart from Medusa (`Stub`) API.

## 2. How to Test (Manual Verification)

### Prerequisites
1.  **Stub Backend** must be running:
    ```bash
    cd tap-to-pay-stub-backend
    npm run dev
    ```
2.  **Firestore Emulator** (or real Firestore) must be accessible.
3.  **Android Emulator**: Run the app on an API 31+ emulator.

### Scenario 1: Happy Path Login & Shop
1.  **Launch App**: Grant permissions if asked.
2.  **Connect Reader**: (In Simulator mode, this might auto-connect or use simulated reader).
3.  **Tap to Login**: Click "Tap to Login" (or tap card).
    -   *Expected*: App should log logs for `prepare-setup` and `login-by-card`.
    -   *Expected*: Navigation to `Shopping Screen`.
4.  **Simulate Shopping**:
    -   Use Postman/Curl to update the Firestore session (or use Stub's `simulate/item-picked`).
    -   *Expected*: Shopping list updates in real-time.
5.  **End Session**:
    -   Update Firestore session status to `completed` (or use Stub's `simulate/door-close`).
    -   *Expected*: App navigates to `Summary Screen`.

## 3. Configuration Notes
-   **google-services.json**: A **placeholder** file was created in `app/google-services.json`. You **MUST** replace this with a valid file from your Firebase Console to build a working release. 
-   **Backend URL**: Configured in `gradle.properties`.
    -   **Emulator**: Use `EXAMPLE_BACKEND_URL="http://10.0.2.2:3000/"`.
    -   **Physical Device**: Use your laptop's Local IP (e.g., `http://192.168.68.111:3000/"`). ensure phone and laptop are on the same Wi-Fi.
