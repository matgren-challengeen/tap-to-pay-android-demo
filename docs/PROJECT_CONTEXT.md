# Venloop Tap-to-Pay Project Context

> **Start Here**: This document provides the high-level context required for any Agent or Developer to pick up the project.

## 1. Project Objective
Build a **Native Android Application** that acts as the UI/Terminal for the Venloop Smart Vending Machine.
*   **Key Feature**: "Tap to Pay" using Stripe Terminal SDK (Native).
*   **Core Flow**: Tap Card -> User Identified (or Guest Created) -> Door Opens -> Shopping -> Door Closes -> Card Charged.
*   **Critical Constraint**: Must be **Stripe Certified** (strictly use standard SDK flows, no raw card reading).

## 2. Strategic "Stub Backend" Approach
We have decided to **NOT** modify the production backend (`venloop-pos-plugin`) immediately.
Instead, we are building a **Simulator** first.

*   **Phase 1**: Build `tap-to-pay-stub-backend` (Node.js).
    *   This Simulator mocks the Medusa API methods (`/store/auth/login-by-card`, `/store/open-venloop`).
    *   It **DOES** connect to the real Stripe Test Mode (to generate valid Client Secrets for the Android SDK).
    *   It **DOES** write to a real Firestore Test Collection (to test the Android app's real-time listeners).
    *   **Constraint**: Do NOT use Railway. This stub runs locally or on a simple cloud function if needed.
*   **Phase 2**: Build the Android App (`tap-to-pay-venloop`) against this Stub.
*   **Phase 3**: Once the App is perfect, port the Stub logic to the real `venloop-pos-plugin`.

## 3. Repository Structure
The workspace `/Users/maciejgren/Documents/Venloop` contains:

1.  **`tap-to-pay-venloop`** (Active Work)
    *   *Type*: Android (Kotlin).
    *   *Status*: Renamed from `tap-to-pay-android-demo`. Ready for refactoring.
    *   *Docs*: `tap-to-pay-venloop/docs/` contains PRD and Plans.
    
2.  **`tap-to-pay-stub-backend`** (To Be Created)
    *   *Type*: Node.js / Express / TypeScript.
    *   *Status*: **NEXT TASK**. Needs to be initialized.

3.  **`venloop-pos-plugin`** (Reference Only)
    *   *Type*: Medusa Backend Plugin (TypeScript).
    *   *Status*: **FROZEN**. Do not modify until Phase 3. Used only for reference.

4.  **`medusa-vending-storefront`** (Reference Only)
    *   *Type*: Next.js.
    *   *Status*: Reference for Firestore data models.

## 4. Key Artifacts
*   **[Requirements/PRD](requirements_prd.md)**: The Source of Truth for User Stories, UI Copy, and Stripe Compliance.
*   **[Implementation Plan](implementation_plan.md)**: The Step-by-Step Technical Plan (now focused on the Stub Backend).
*   **[Task List](task.md)**: Granular checklist of current progress.

## 5. Stripe Tap to Pay Constraints (CRITICAL)
If you work on the Android App, you MUST adhere to:
*   **Android 13+** required.
*   **Release Build** required for full Attestation API check (Debug builds often fail "Tap" initialization).
*   **Background Connection**: Connect to reader on app startup.
*   **SetupIntent**: We use `SetupIntent` to capture the card credential (not PaymentIntent).

## 6. Current Status (as of Jan 2026)
*   **Phase 1 (Stub Backend)**: Complete.
    *   Stub running on port 3000.
    *   Stripe `prepare-setup` and `login-by-card` endpoints verified.
    *   Firestore Mock Writer active.
*   **Phase 2 (Android App)**: In Progress.
*   **Phase 2 (Android App)**: In Progress.
    *   **Completed**: Stub Connection, Cleanup, SetupIntent Login Flow, FireBase SDKs, Data Layer, UI Layer (Shopping & Summary).
    *   **Current Focus**: Verification and Integration Testing.

## 7. Secrets Management Protocol (Security)
To ensure no secrets are leaked to GitHub:
1.  **Android (`tap-to-pay-venloop`)**:
    *   **Storage**: `local.properties` file in the root project folder.
    *   **Format**: `STRIPE_PUBLISHABLE_KEY=pk_test_...`
    *   **Injection**: `build.gradle` reads this file and injects values into `BuildConfig` java class.
    *   **Git**: `local.properties` is **GITIGNORED** by default.
2.  **Stub Backend (`tap-to-pay-stub-backend`)**:
    *   **Storage**: `.env` file in the root folder.
    *   **Format**: `STRIPE_SECRET_KEY=sk_test_...`
    *   **Injection**: `dotenv` library loads these into `process.env`.
    *   **Git**: `.env` MUST be added to `.gitignore`.
