# Product Requirements Document (PRD): Venloop Tap-to-Pay (Native)

## 1. Executive Summary
The goal is to build a native Android application ("Tap to Pay") that serves as the access control and authentication terminal for the Venloop smart vending machine. The app will replace the previous "Web Storefront" approach with a simplified, secure, and native flow. It will leverage Stripe Terminal for card reading and the existing Medusa backend for user sessions and transaction management.

## 2. Core Functional Requirements

### 2.1 User Authentication (The "Tap")
*   **REQ-AUTH-01**: The system MUST authorize users via a contactless payment card (Tap).
*   **REQ-AUTH-02**: The system MUST NOT process or store raw Primary Account Numbers (PAN) to maintain minimal PCI-DSS scope and Stripe Certification.
*   **REQ-AUTH-03**: The system MUST use Stripe's "Update Payment Method" or "SetupIntent" flow to securely capture a `PaymentMethod` handle.

### 2.2 Identity Resolution
*   **REQ-ID-01**: The backend MUST identify existing users by their **Card Fingerprint** (derived from the `PaymentMethod`).
*   **REQ-ID-02**: If a user is identified, the system MUST log them in and start a vending session.
*   **REQ-ID-03**: If a user is **not** identified (new card), the system MUST automatically:
    1.  Create a new "Guest" Customer in Medusa (e.g., `guest_{uuid}@venloop.com`).
    2.  Attach the captured `PaymentMethod` to this customer for future "Off-Session" charges.
    3.  Store the card fingerprint for future recognition.
    4.  Log them in and start a vending session.

### 2.3 Localization & Currency
*   **REQ-LOC-01**: The system MUST fetch product names in the correct language for the active region (if supported by backend).
*   **REQ-LOC-02**: The system MUST display prices in the Currency defined by the backend Region (e.g., PLN, EUR).
*   **REQ-LOC-03**: The Android App MUST NOT hardcode currency symbols; it must use formatting provided by the API `currency_code`.

### 2.4 Return Mode (Deposit Refund)
*   **REQ-RET-01**: The system MUST support a "Return Mode" for returning empty containers.
*   **REQ-RET-02**: The user MUST select "Return" before tapping (or the system must infer it).
*   **REQ-RET-03**: In Return Mode, the system MUST:
    *   Open the door.
    *   Count returned items (via backend/sensors).
    *   Refund the deposit amount to the user's saved card or store credit.

### 2.4 Session Management
*   **REQ-SESS-01**: Upon successful auth, the Android App MUST call the backend `POST /store/open-venloop` with `type: 'buy'` or `'return'`.
*   **REQ-SESS-02**: The Android App MUST display the following native screens with the defined copy:
    *   **Idle**: "Tap to Open" / "Return Containers" (Icon).
    *   **Processing**: "Unlocking..." (Animation).
    *   **Active Session**:
        *   Header: "Shopping..."
        *   Display active list of picked items.
    *   **Summary**:
        *   Header: "Receipt" or "Summary".
        *   Total Amount.
        *   "Thank you!"

## 3. User Stories

### Story 1: Returning User Access & Shopping
> As a **Returning User**, I want clear instructions so I know exactly what to do.

**Acceptance Criteria:**
*   User taps card -> Door unlocks.
*   Screen: **"Unlocking..."** -> **"Door Open"**.
*   User picks "Coke" -> Screen: **"Coke - 12.00 PLN"**.
*   User puts "Coke" back -> Screen removes item.
*   User closes door -> Screen: **"Total: 12.00 PLN"** -> **"Thank You!"**.

### Story 2: Returning Containers (Deposit Refund)
> As a **Shopper**, I want to **return empty containers** to get my deposit back.

**Acceptance Criteria:**
*   **Idle Screen**: Shows **"Tap to Open"** (Main) AND **"Return Containers"** (Secondary Button).
*   **Action**: User presses "Return Containers".
*   **Prompt**: Screen says **"Tap to Start Return"**.
*   **Tap**: User taps card -> Door unlocks.
*   **Action**: User puts 2 containers inside.
*   **Feedback**: Screen updates: **"Returned: 2 x Container (+10.00 PLN)"**.
*   **Close**: Door closes -> Screen: **"Refund Processed"**.

### Story 3: New Guest Access
> As a **New Customer**, I want to **tap my card** to unlock the fridge instantly, without registering beforehand.

**Acceptance Criteria:**
*   User taps unknown card.
*   System creates a Guest account properly in the background (hidden from user).
*   Door unlocks within 2-3 seconds.
*   Card is saved for the final payment when the door closes.

### Story 3: Card Read Failure
> As a **User**, I want the app to tell me if my card wasn't read correctly, so I can try again.

**Acceptance Criteria:**
*   User taps card too quickly.
*   Stripe Terminal SDK returns "Read Failed".
*   Android App shows clear red error: "Hold card longer" or "Try again".
*   System resets to "Idle" state after 3 seconds.

### Story 4: Card Declined / Blocked
> As a **Merchant**, I want to **reject cards** that are known to be blocked or reported lost, to prevent theft.

**Acceptance Criteria:**
*   User taps card.
*   Backend checks `payment_method_id` against Stripe (setup intent check) or internal blocklist.
*   If Declined: Door *does not* unlock.
*   Android App shows: **"Card Declined / Try Another Card"** (Red).

### Story 5: Disputed Amount (Exceptional Flow)
> As a **Shopper**, if I see a wrong amount on the summary screen, I want to know how to resolve it.

**Acceptance Criteria:**
*   User closes door -> Summary Screen appears.
*   Bottom of Summary screen displays:
    *   **"Need Help?"**
    *   **"Transaction ID: TXN-12345"**
    *   **"Call: +48 123 456 789"**

### Story 6: Currency & Language
> As a **Shopper**, I want to see prices in my local currency (e.g., PLN) and product names in my language (if available).

**Acceptance Criteria:**
*   App fetches `Region` settings from Backend on startup.
*   Prices are displayed as `12.50 PLN` (or `€3.00`) based on `currency_code`.
*   Product Titles are displayed in the configured language (e.g., "Napój Gazowany" vs "Sparkling Drink") if backend supports translation.

### Story 7: Shopping Loop (Visual Feedback)
> As a **Shopper**, I want to receive immediate visual feedback when I pick up an item.

**Acceptance Criteria:**
*   User opens door (Active Session).
*   User grabs "Sandwich".
*   Within < 1s, Android App updates list: "+ Sandwich".
*   User is reassured the system works.

### Story 7: Empty Transaction (User Changed Mind)
> As a **Shopper**, I want to be able to **close the door without buying anything** and not be charged.

**Acceptance Criteria:**
*   User unlocks door.
*   User looks inside but takes nothing.
*   User closes door.
*   Android App shows "Summary".
*   Total is $0.00.
*   No charge is made to the card.

### Story 8: Stripe Certification Compliance
> As a **Product Owner**, I want the app to use **Stripe Certified flows** (SetupIntents/Terminal SDK) so that our application is **approved by Stripe** for production use.

**Acceptance Criteria:**
*   No raw PAN reads in codebase.
*   Uses `collectPaymentMethod` (with SetupIntent) or `readReusableCard`.
*   Transaction is flagged as "Merchant Initiated" (MIT) for the final charge.

## 4. Technical Constraints & Risks
*   **Stripe SDK**: We must use `SetupIntent` if `readReusableCard` is not supported by our specific reader hardware/SDK combination for "Tap to Pay on Android".
*   **Connectivity**: Android device must have reliable internet to communicate with Medusa Backend.
*   **Hardware**: Android device acts as the "POS".

## 5. Stripe Compliance Checklist
To ensure fast review and certification:
1.  **Hardware**:
    *   [ ] Device running **Android 13+**.
    *   [ ] **Google Mobile Services** installed.
    *   [ ] **NOT Rooted**. Bootloader locked.
    *   [ ] Hardware Keystore (ECDH support).
2.  **Security**:
    *   [ ] App signed with **Release Key** (Debug builds are rejected/unsupported for Tap to Pay).
    *   [ ] `ACCESS_FINE_LOCATION` permission enabled.
3.  **UX/UI**:
    *   [ ] Do **NOT** show custom "Insert Card" UI; let SDK handle payment sheet.
    *   [ ] Connect to reader in **Background** on startup (speed up latency).
    *   [ ] Handle "Decline" gracefully (Story 4).

## 6. Security & Failover Protocols

### 6.1 Data Security
*   **REQ-SEC-01**: **No Hardcoded Keys**. API Keys (Stripe Publishable Key, Backend URL) MUST be stored in `local.properties` (not committed to Git) and injected via file `BuildConfig`.
*   **REQ-SEC-02**: **Authentication**. All calls to Medusa Backend MUST be authenticated via JWT or Session Token obtained during Login.
*   **REQ-SEC-03**: **Logging**. Application logs MUST NOT contain PII or truncated card numbers.

### 6.2 Connectivity Failover
*   **Scenario A: Internet Loss BEFORE Tap**
    *   **Action**: App shows "Offline / Out of Order" banner.
    *   **Result**: Door stays locked. (Fail Secure).
*   **Scenario B: Internet Loss DURING Shopping (Door Open)** (CRITICAL)
    *   **Action 1**: Allow user to finish.
    *   **Action 2**: When door closes, App attempts to send "Close Transaction" signal.
    *   **Action 3**: If network fails, App MUST **Queue** the request locally (encrypted) and retry indefinitely until success.
    *   **Result**: Zero data loss.
*   **Scenario C: Backend 500 Error**
    *   **Action**: Same as Scenario B (Queue & Retry).
    *   **User Feedback**: Show "Saved locally. Will sync later." on Summary screen if possible.

## 7. Artifacts to Deliver
1.  **Backend Logic (`venloop-pos-plugin`)**:
    *   `POST /store/auth/login-by-card`: Main entry point.
    *   `Workflow`: Fingerprint Lookup -> Guest Creation -> Session Start.
2.  **Android App (`tap-to-pay-android-demo` fork)**:
    *   `MainActivity`: Payment Flow Logic.
    *   `ApiClient`: Backend Integration.
    *   `UI`: Simple efficient native screens (Compose or Views).

## 7. Technical Architecture

### 7.1 Data Synchronization (Hybrid)
We will replicate the Storefront's data flow to ensure consistency.
1.  **Signal**: Android App listens to **Firebase Firestore** document: `sessions/{session_id}`.
2.  **Trigger**: When the physical machine detects an item pick, the Backend updates the Firestore document (likely `cart_id` or timestamp).
3.  **Fetch**: Upon Firestore update, the Android App calls the **Medusa REST API** to fetch the full Cart details (items, prices).
4.  **Render**: App updates the Native UI list.

### 6.2 Authentication Flow
1.  **Tag**: `Terminal.readReusableCard` (or SetupIntent).
2.  **Auth**: `POST /store/auth/login-by-card`.
3.  **Session**: Backend looks up active session for this user/device or creates new one.
4.  **Subscribe**: App subscribes to the returned `session_id` in Firestore.
