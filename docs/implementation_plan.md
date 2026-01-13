# Tap to Pay & Medusa Vending Integration Plan

## Goal Description
Enable a seamless "Tap to Pay" experience where users can unlock a vending machine and purchase items using their payment card as an identifier. The system must support both existing users (login by card) and new users (guest account creation via card). The solution must be Stripe Certified.

## User Review Required
> [!IMPORTANT]
> **Stripe Certification Risk**: We cannot read the raw "Card ID" (PAN). We must use the **Card Fingerprint** provided by Stripe after a tap. This fingerprint is unique to your Stripe account.
> **Logic Gap**: The current `venloop-pos-plugin` attaches cards to users but **does not store the fingerprint** locally. We cannot currently "find a user by card". We must add this.

> [!WARNING]
> **Guest Flow**: A "Guest" in this context is just a Customer with no email/password but with an attached PaymentMethod. We must ensure we capture the `PaymentMethod` correctly to charge it *after* the vending session closes.

## lightweight Principles (Revised)
To keep development fast and simple (MVP style), we will avoid over-engineering.

### 1. Architecture: Standard MVVM
*   **Keep it Simple**: No Modular Monoliths or complex DI frameworks for now.
*   **Pattern**: Standard Android `ViewModel` + `Repository` pattern.
*   **Validation**: Basic input checks are enough.

### 2. Testing: Essential Logic Only
*   **Focus**: We will **only** write automated Unit Tests for the *complex business logic* (e.g., cart calculations, session constraints).
*   **UI**: We will verify UI manually using the Simulator (Stub Backend).
*   **Tool**: Standard JUnit (no heavy E2E setup).

---

## Proposed Changes

### Strategic Pivot: The Stub Backend
We will use a **Stub Backend** (Simulator) for the entire development lifecycle.
*   **Why?** Allows us to simulate the "Tunnel Scenario" (Fail Safe) and "Card Declined" scenarios easily, which are hard to reproduce with a real backend/Stripe.
*   **Production Deployment**: We will release the app connected to the real backend only in Phase 4.
*   **Stripe Review**: We will submit the app pointed to the **Production Backend** (in Test Mode logic) for review. We CANNOT submit with the Stub.

---

### Phase 1: Stub Backend (The Simulator)
**Objective**: Build a controlled environment to develop the Android App without risking the production backend.
*   **Tech Stack**: Node.js, Express, TypeScript, Firestore Admin SDK.
*   **Why**: We need to simulate edge cases (Declines, Network Latency, Door Open events) that are difficult or slow to reproduce with physical hardware and the real Medusa backend.

**Key Deliverables:**
1.  **Stripe Wrapper**: An endpoint `POST /auth/prepare-setup` that talks to the *Real* Stripe API (Test Mode) to generate valid `client_secret`s. This allows the Android SDK to actually function and process "Test Cards".
2.  **Mock Auth**: An endpoint `POST /auth/login-by-card` that accepts a `payment_method_id` and returns a hardcoded "Guest Session" (e.g., `cust_stub` / `sess_stub`).
3.  **Mock Hardware Trigger**: An endpoint `POST /simulate/door-open` and `POST /simulate/item-picked`.
4.  **Firestore Writer**: The Stub will write to the *Real* Firestore (Test Collection) to trigger the Android App's real-time listeners.

**Exit Criteria (DoD):**
*   [ ] `curl` to Stub returns a valid Stripe `secret` starting with `seti_...`.
*   [ ] `curl` to Stub triggers a document update in Firestore that I can see in the Firebase Console.

---

### Phase 2: Android App Implementation (Native)
**Objective**: Build the complete User Experience (UI) and Logic on the Android device, tested against the Stub.
*   **Tech Stack**: Kotlin, Jetpack Compose (or Views), Stripe Terminal SDK, Firebase SDK.

**Key Deliverables:**
1.  **Architecture**: `AuthManager` (Login), `ShoppingManager` (Cart), `ConnectionManager`.
2.  **Stripe Integration**:
    *   Initialize SDK on startup.
    *   Connect to "Local Mobile Reader" (the phone's NFC).
    *   Implement the `SetupIntent` flow to "Save Card".
3.  **UI Implementation**:
    *   **Idle Screen**: "Tap to Open" / "Return Containers".
    *   **Loading Screen**: "Verifying..." (while talking to Stub).
    *   **Shopping Screen**: Real-time list listening to Firestore `sessions/{id}`.
    *   **Summary Screen**: "Thank you, {Total}".

**Exit Criteria (DoD):**
*   [ ] User can Tap a Test Card -> See "Welcome" -> See visual feedback when we trigger the Stub -> See Receipt.

---

### Phase 3: Validation & Failover (Robustness)
**Objective**: Ensure the system handles real-world failures gracefully.
*   **Method**: Manual testing with the App + Simulator.

**Key Deliverables:**
1.  **The "Tunnel" Test**:
    *   Action: User Opens Door -> **Disconnect Internet** -> User Closes Door.
    *   Expected: App stores "Close Request" locally. App retries every 30s. App succeeds when internet returns.
2.  **The "Decline" Test**:
    *   Action: Configure Stub to return `402 Payment Required`.
    *   Expected: App shows Red "Card Declined" screen. Door remains locked.
3.  **The "Power Cut" Test**:
    *   Action: Kill App during Shopping. Restart App.
    *   Expected: App detects "Active Session" from Firestore and restores "Shopping Screen" immediately.

**Exit Criteria (DoD):**
*   [ ] All 3 scenarios pass without crashing or data loss.

---

### Phase 4: Production Integration & Release
**Objective**: Swap "The Simulator" for "The Real Thing" and Publish.

**Key Deliverables:**
1.  **Backend Implementation (`venloop-pos-plugin`)**:
    *   Port the logic from the Stub to the real Medusa Plugin.
    *   Implement "Reverse Lookup": `payment_method_id` -> `fingerprint` -> `Customer`.
2.  **Android Config Switch**:
    *   Change `BASE_URL` from `10.0.2.2:3000` to `https://api.venloop.com`.
3.  **Google Play Release**:
    *   Generate a **Signed Release Bundle (.aab)**.
    *   Create an application in Google Play Console.
    *   Upload to **Internal Test Track** (Closed Testing).
    *   Add `stripe-terminal-certified` testers.

**Exit Criteria (DoD):**
*   [ ] App is installed via Play Store on the Production Hardware.
*   [ ] Real Credit Card tap opens the Real Door.

---

## Master Test Plan (Scenarios)
We will verify these scenarios manually using the Simulator (Phase 1/2) and then on Real Hardware (Phase 4).

### 1. Happy Paths (Success)
| ID | Scenario | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **HP-01** | **Existing User Login** | 1. Tap known Card.<br>2. Stripe returns ID. | App shows "Welcome [Name]", Door Unlocks. |
| **HP-02** | **Guest User Signup** | 1. Tap NEW Card.<br>2. Stripe acts (SetupIntent). | App shows "Welcome Guest", Backend creates Customer, Door Unlocks. |
| **HP-03** | **Shopping Session** | 1. User picks items.<br>2. Door Closes. | App updates Cart in Real-time. App shows "Thank You" + Total. |

### 2. Payment & Auth Failures
| ID | Scenario | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **ER-01** | **Card Declined** | 1. Tap Blocked Card (Stub: `402`). | App shows Red "Card Declined - Try Another" screen. Door stays Locked. |
| **ER-02** | **Read Error** | 1. Tap too fast / Bad NFC. | UI shows "Read Error - Tap Again". No backend call made. |
| **ER-03** | **Expired Session** | 1. Door Open > 5 mins. | App flashes Warning. (Optional: Auto-close logic). |

### 3. Hardware & Network Failures ("The Tunnel")
| ID | Scenario | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **HW-01** | **Network Cut (Idle)** | 1. Cut Internet.<br>2. Tap Card. | UI shows "Offline - Cannot Login". Door stays locked. |
| **HW-02** | **Network Cut (Shopping)** | 1. Login (Success).<br>2. Cut Internet.<br>3. Door Closes. | App stores "Session Complete" locally. Retries until Online. User charged eventually. |
| **HW-03** | **Power Cut (Crash)** | 1. Session Active.<br>2. Kill App.<br>3. Restart App. | App checks Firestore/Local State. Detects "Open Session". Restores Shopping Screen immediately. |

### 4. Concurrency (Edge)
| ID | Scenario | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **CN-01** | **Double Tap** | 1. User A Taps.<br>2. User B Taps immediately. | System ignores B until A's door cycle is complete (or rejects B saying "Busy"). |

---

### Detailed Changes

### 1. `tap-to-pay-stub-backend` (New Project)
*   **Setup**: `npm init`, `tsc --init`, `express`.
*   **Stripe Integration**: Needs a real Stripe Secret Key (Test Mode) to generate valid `client_secret`s for the Terminal SDK to actually work.

### 2. `venloop-pos-plugin` (Deferred)
*   *Note: These changes are deferred until Phase 3.*
*   We need to enable "Reverse Lookup" (Card -> User).

#### [MODIFY] `src/workflows/payments/payment-attach-card.ts`
- **Current Behavior**: Attaches `payment_method_id` to Stripe Customer.
- **New Behavior**:
    1.  Retrieve the `PaymentMethod` details from Stripe (specifically `card.fingerprint`).
    2.  Update the Medusa `Customer` (or `AccountHolder` context) to store this `fingerprint` in `metadata` (e.g., `metadata.stripe_card_fingerprints: ["fing_123"]`).
    3.  Ensure fingerprints are unique per user to avoid collisions.

#### [NEW] `src/api/store/auth/login-by-card/route.ts`
- **Purpose**: Authenticates a user via their card's fingerprint.
- **Logic**:
    1.  Receive `payment_method_id` from Android App.
    2.  Retrieve `fingerprint` from Stripe.
    3.  Search `Customer` table for `metadata.stripe_card_fingerprints` containing this fingerprint.
    4.  **If Found**: Return Auth Token / Session.
    5.  **If Not Found**: Trigger "Guest Creation Flow" (Create Customer -> Attach Card -> Store Fingerprint -> Return Session).

### 3. `tap-to-pay-venloop` (Android App)
#### Architecture Overview
The app currently uses a standard "Payment Intent" flow. We will modify this to a "Save Card / Login" flow.
*   **Base URL**: Configurable to point to Stub (`http://10.0.2.2:3000`) or Real Backend.

**New Flow Diagram:**
1.  **Initialize**: `Terminal.initTerminal()`
2.  **Connect**: `Terminal.connectLocalMobileReader()`
3.  **Tap**: `Terminal.collectPaymentMethod()` **(No Payment Intent)**
    *   *Note*: We must use `collectPaymentMethod` *without* a PaymentIntent to just read the card.
4.  **Login**: Send `PaymentMethod.id` to `POST /store/auth/login-by-card`.
5.  **Session**: Receive `customer_id` / `session_token` / `session_id`.
6.  **Subscribe**: Listen to Firestore `sessions/{session_id}`.
7.  **Shop**: Update Native UI in real-time as Backend updates Firestore.
8.  **Close**: When door closes (Firestore status 'completed'), show Native Summary.

#### [MODIFY] `ApiClient.kt`
- **Current**: Has `createPaymentIntent` and `capturePaymentIntent`.
- **New Methods**:
    - `loginByCard(paymentMethodId: String, callback: Callback<LoginResponse>)`: Calls our new `venloop-pos-plugin` endpoint.

#### [MODIFY] `MainActivity.kt`
- **Objective**: Change the UX from "Enter Amount -> Tap" to "Tap to Login".
- **Step 1**: Remove `collectPayment` triggering.
- **Step 2**: Implement `startLoginFlow()`.
    - Call Backend: `POST /store/auth/prepare-setup` (New endpoint or reuse intent creation? Better to keep it clean).
    - **Better Approach**: We don't actually need a `SetupIntent` to *read* a card for simple ID purposes if we use `readReusableCard`.
    - **Constraint**: `readReusableCard` is only for "Intermittent" usage. For a main login flow, Stripe advises `collectPaymentMethod` with a `SetupIntent`.
    - **Selected Path**: **SetupIntent**.
    - **Why?** It's the most robust way to get a `PaymentMethod` that we can later charge off-session. 
    - **Logic**:
        1.  App calls `POST /store/auth/prepare-setup`.
        2.  Backend creates `SetupIntent` (allows "filtering" attached to a generic/temp customer if needed, although we want to Identify. So we might need to attach to a *temporary* Guest customer or just use it to capture the PM).
        3.  Backend returns `client_secret`.
        4.  App calls `Terminal.collectPaymentMethod(setupIntentSecret)`.
        5.  App calls `Terminal.confirmSetupIntent`.
        6.  App gets `SetupIntent.paymentMethodId`.
        7.  App calls `POST /store/auth/login-by-card { payment_method_id }`.
        8.  Backend links PM to User (or creates Guest) and returns Session.

#### [NEW] `managers/FirebaseManager.kt`
- **Purpose**: Handles Firestore connection.
- **Method**: `listenToSession(sessionId: String, callback: (SessionData) -> Unit)`.

#### [NEW] `ui/ShoppingActivity.kt` (or Composable)
- **State**: `Idle` | `Loading` | `Shopping(cart)` | `Summary(total)`.
- **Logic**:
    - On `SessionData` update:
        - If `cart_id` changes -> Fetch Cart from Medusa API.
        - Render Cart Items.
    - If `status` == "completed" -> Show Summary.

### 3. Native UI Components

### 3. Storefront (Iframe)
- Ensure the `buy` page does **not** attempt to collect payment details again. It should assume the session is pre-authorized.
- The "Summary" screen should just show the total; the actual charge happens backend-side using the saved `PaymentMethod` or captured `PaymentIntent`.

### 3. Native UI Components
- **Shopping List**: RecyclerView/LazyColumn showing items, quantity, price.
- **Total Banner**: Sticky footer with current total.
- **Summary Screen**: Big "Thank You", list of charged items, green checkmark.

## Verification Plan

### Automated Tests
-   **Backend**: Unit tests for `posPaymentAttachCardWorkflow` to verify `metadata` update.
-   **Backend**: Integration test for `/store/auth/login-by-card` matching a mock customer.

### Manual Verification
1.  **Stage 1 (Stub)**:
    *   Verify Tap -> Token -> Stub -> Login Flow.
    *   Verify Firestore Real-time updates.
2.  **Stage 2 (Real Backend)**:
    *   Verify Tap -> Token -> Real API -> Real User Lookup.
3.  **Stage 3 (Failover)**:
    *   Simulate Network Cut during "Door Closed" event.
    *   Verify local queuing.
