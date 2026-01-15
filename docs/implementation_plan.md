# Tap to Pay & Medusa Vending Integration Plan

## Goal Description
Enable a seamless "Tap to Pay" experience where users can unlock a vending machine and purchase items using their payment card as an identifier. The system must support both existing users (login by card) and new users (guest account creation via card). The solution must be Stripe Certified.

## User Review Required
> [!IMPORTANT]
> **Stripe Certification Requirement**: We use the **Pre-Auth + Capture** pattern:
> 1. At tap: Create PaymentIntent with `capture_method: 'manual'` → Card is **present** during authorization
> 2. At door close: Backend captures the final amount
> 
> This is the ONLY pattern that works for Stripe Terminal certification. Do NOT use SetupIntent (that's for off-session/card-not-present).

> [!IMPORTANT]
> **Card Fingerprint for Customer ID**: We extract `fingerprint` from the authorized PaymentIntent to identify returning customers. The `venloop-pos-plugin` must store fingerprints in Customer metadata.

> [!NOTE]
> **Guest Flow**: A \"Guest\" is a Customer with no email/password, identified only by card fingerprint. The pre-authorized PaymentIntent guarantees funds are blocked before the door opens.


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

### Strategic Pivot: The Stub Backend as API Contract
We will use a **Stub Backend** (Simulator) for the entire development lifecycle. The Stub defines the **API contract** that Medusa must implement later.

*   **Why?** Allows us to simulate edge cases and develop without risking the production backend.
*   **Migration Strategy**: When ready, change only `BACKEND_URL` – the API contract is identical.
*   **API Contract**: See [api_contract.md](file:///Users/maciejgren/Documents/Venloop/tap-to-pay-venloop/docs/api_contract.md) for full specification.

---

### Phase 1: Stub Backend (The Simulator)
**Objective**: Build a controlled environment with the exact API that Medusa will implement.
*   **Tech Stack**: Node.js, Express, TypeScript, Firestore Admin SDK.
*   **Constraint**: Do NOT use Railway. Run locally or via simple cloud functions.

> [!IMPORTANT]
> **Network Binding**: The server MUST bind to `0.0.0.0` (not `localhost`) so it's accessible from the physical Android device on the same WiFi network.

**Key Endpoints (Pre-Auth Pattern):**
1.  **`POST /connection_token`** – Returns Stripe Terminal connection token
2.  **`POST /create_payment_intent`** – Creates PaymentIntent with `capture_method: 'manual'`
3.  **`POST /store/auth/login-by-payment`** – Accepts `payment_intent_id`, returns `session_id` and `customer_id`
4.  **`POST /capture_payment_intent`** – Captures authorized PaymentIntent (called on door close)
5.  **`POST /cancel_payment_intent`** – Cancels PaymentIntent (if cart is empty)
6.  **`GET /store/carts/{id}`** – Returns cart data
7.  **Simulation endpoints**: `/simulate/door-open`, `/simulate/item-picked`, `/simulate/door-close`
8.  **Firestore Writer**: Updates `sessions/{session_id}` for real-time app notifications

**API Response Formats:**
| Endpoint | Response Format |
|----------|-----------------|
| `POST /connection_token` | `{ "secret": "pst_test_..." }` |
| `POST /store/auth/prepare-setup` | `{ "secret": "seti_...", "id": "seti_..." }` |
| `POST /store/auth/login-by-card` | `{ "session_id": "...", "customer_id": "..." }` |

**Exit Criteria (DoD):**
*   [x] `curl` to `/connection_token` returns a valid Stripe token starting with `pst_test_...`.
*   [x] `curl` to `/store/auth/prepare-setup` returns a valid Stripe `secret` starting with `seti_...`.
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
The app uses a **Pre-Authorization + Capture** flow, which is the only correct pattern for Stripe Terminal with vending machines.
*   **Base URL**: Configurable to point to Stub (`http://10.0.2.2:3000`) or Real Backend.

> [!IMPORTANT]
> **Why Pre-Auth, NOT SetupIntent?**
> - SetupIntent = "save card, charge later off-session" = **card-not-present** (WRONG for Tap to Pay)
> - PaymentIntent with `capture_method: 'manual'` = **card-present authorization** (CORRECT)
> - Stripe certification requires the card to be **present during authorization**

**Correct Flow Diagram:**
1.  **Initialize**: `Terminal.initTerminal()`
2.  **Connect**: `Terminal.connectLocalMobileReader()`
3.  **Backend**: App calls `POST /store/auth/create-payment-intent` with `amount: MAX_VENDING_AMOUNT` (e.g., $50)
4.  **Tap**: App calls `Terminal.collectPaymentMethod(paymentIntent)` → User taps card
5.  **Authorize**: App calls `Terminal.confirmPaymentIntent()` → Stripe pre-authorizes (blocks funds)
6.  **Identify**: App sends `payment_intent_id` to `POST /store/auth/login-by-payment`
7.  **Session**: Backend extracts `fingerprint` from PaymentIntent, finds/creates Customer, returns `session_id`
8.  **Unlock**: Backend triggers door unlock via MQTT
9.  **Subscribe**: App listens to Firestore `sessions/{session_id}`
10. **Shop**: User picks items → Hardware notifies Backend → Backend updates Firestore → App shows cart
11. **Close**: Door closes → Backend receives MQTT event
12. **Capture**: Backend calls `PaymentIntent.capture(amount: FINAL_AMOUNT)` (can be less than pre-auth)
13. **Complete**: Backend updates Firestore `status: 'completed'` → App shows Summary

#### [MODIFY] `ApiClient.kt`
- **Current**: Has `createPaymentIntent` and `capturePaymentIntent`.
- **Keep**: `createPaymentIntent` – used to create pre-auth at MAX amount
- **Keep**: `capturePaymentIntent` – called by backend (not app) after door closes
- **New Methods**:
    - `loginByPayment(paymentIntentId: String, callback: Callback<LoginResponse>)`: Sends authorized PaymentIntent to backend for customer identification.

#### [MODIFY] `MainActivity.kt`
- **Objective**: Implement "Tap to Pre-Authorize" flow.
- **Logic**:
    1.  App calls `POST /store/auth/create-payment-intent { amount: 5000, capture_method: 'manual' }`.
    2.  Backend creates PaymentIntent and returns `client_secret`.
    3.  App retrieves PaymentIntent: `Terminal.retrievePaymentIntent(clientSecret)`.
    4.  App collects payment: `Terminal.collectPaymentMethod(paymentIntent)`.
    5.  User taps card → App confirms: `Terminal.confirmPaymentIntent(paymentIntent)`.
    6.  Stripe authorizes (pre-auth) → PaymentIntent status = `requires_capture`.
    7.  App calls `POST /store/auth/login-by-payment { payment_intent_id }`.
    8.  Backend extracts `fingerprint`, identifies/creates Customer, stores `payment_intent_id` in session.
    9.  Backend unlocks door, returns `session_id`.
    10. App subscribes to Firestore and shows Shopping screen.

> [!NOTE]
> **Capture happens on Backend, NOT App!**
> When door closes, Backend receives MQTT event and calls `stripe.paymentIntents.capture(id, { amount_to_capture: finalAmount })`.
> This ensures the user cannot manipulate the final charge.

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
