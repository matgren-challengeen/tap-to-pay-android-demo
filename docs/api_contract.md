# Tap-to-Pay API Contract & Migration Plan

## Overview

This document defines the **API contract** that both the Stub Backend and the Medusa.js Backend (with venloop-pos-plugin) must implement. The goal is:

1. Develop the Android app against the Stub Backend
2. When ready, change only `BACKEND_URL` to switch to Medusa
3. Both backends must implement the same API

---

## API Contract (Endpoints)

### 1. Stripe Terminal Connection

| Endpoint | Method | Request | Response |
|----------|--------|---------|----------|
| `/connection_token` | POST | `{}` | `{ secret: string }` |

**Purpose**: Required by Stripe Terminal SDK to authenticate before any reader operations.

**Stub**: ✅ Implemented  
**Medusa**: ❌ Must add to `venloop-pos-plugin`

---

### 2. Payment Intent (Pre-Auth)

| Endpoint | Method | Request | Response |
|----------|--------|---------|----------|
| `/create_payment_intent` | POST | `{ amount, currency, capture_method? }` | `{ secret: string, id: string }` |
| `/capture_payment_intent` | POST | `{ payment_intent_id, amount_to_capture? }` | `{}` |
| `/cancel_payment_intent` | POST | `{ payment_intent_id }` | `{}` |

**Purpose**: Create pre-auth at tap, capture/cancel at door close.

**Stub**: ⚠️ Partially implemented (create exists, capture/cancel need work)  
**Medusa**: ⚠️ Must add specific endpoints for Tap-to-Pay flow

---

### 3. Authentication by Payment

| Endpoint | Method | Request | Response |
|----------|--------|---------|----------|
| `/store/auth/login-by-payment` | POST | `{ payment_intent_id }` | `{ session_id, customer_id, fingerprint? }` |
| `/store/auth/login-return` | POST | `{ payment_method_id }` | `{ session_id, customer_id, returnable_count }` |

**Purpose**: After successful pre-auth, identify/create customer by card fingerprint.

> [!IMPORTANT]
> **Change from current implementation**: We switch from `login-by-card` (which used SetupIntent) to `login-by-payment` (which uses authorized PaymentIntent).

**Stub**: ⚠️ Has `login-by-card`, must rename/update to `login-by-payment`  
**Medusa**: ❌ Must add new endpoint

---

### 4. Cart

| Endpoint | Method | Request | Response |
|----------|--------|---------|----------|
| `/store/carts/{id}` | GET | - | `{ cart: { id, items, total, currency_code } }` |

**Purpose**: Fetch current cart state (for display in app).

**Stub**: ✅ Implemented  
**Medusa**: ✅ Already exists (standard Medusa endpoint)

---

### 5. Session Management (Firebase Realtime)

| Firebase Path | Structure |
|---------------|-----------|
| `sessions/{session_id}` | `{ status, cart_id, payment_intent_id, updated_at }` |

**Purpose**: Real-time updates to Android app.

**Stub**: ✅ Writes to Firestore  
**Medusa**: ❌ Must add Firestore writes to venloop-pos-plugin workflows

---

## Changes Required

### A. Stub Backend Changes

| File | Change | Priority |
|------|--------|----------|
| `routes/auth.ts` | Rename `/login-by-card` → `/login-by-payment`, accept `payment_intent_id` instead of `payment_method_id` | 🔴 High |
| `routes/auth.ts` | Remove `/prepare-setup` (SetupIntent - no longer needed) | 🟡 Medium |
| `index.ts` | Add `/create_payment_intent` endpoint with `capture_method: 'manual'` | 🔴 High |
| `index.ts` | Add `/capture_payment_intent` endpoint | 🔴 High |
| `index.ts` | Add `/cancel_payment_intent` endpoint | 🔴 High |
| `routes/simulation.ts` | Update `door-close` to call capture logic | 🟡 Medium |
| Firebase writes | Include `payment_intent_id` in session document | 🟡 Medium |

### B. Android App Changes

| File | Change | Priority |
|------|--------|----------|
| `BackendService.kt` | Remove `prepareSetup()` | 🟡 Medium |
| `BackendService.kt` | Rename `loginByCard()` → `loginByPayment(payment_intent_id)` | 🔴 High |
| `ApiClient.kt` | Update `loginByPayment()` to use new endpoint | 🔴 High |
| `MainActivity.kt` | Implement Pre-Auth flow (create → collect → confirm → login) | 🔴 High |
| Response models | Add models for new responses if needed | 🟢 Low |

### C. Medusa Backend (venloop-pos-plugin) Changes - For Later

| Location | Change | Notes |
|----------|--------|-------|
| `api/store/connection-token/route.ts` | NEW: Return Stripe connection token | Simple Stripe API call |
| `api/store/auth/login-by-payment/route.ts` | NEW: Accept `payment_intent_id`, extract fingerprint, find/create customer | Core identification logic |
| `workflows/payments/` | Add workflow for Tap-to-Pay capture | Triggered by MQTT door-close |
| Firebase integration | Add Firestore writes when session status changes | Real-time app updates |

---

## Migration Checklist

### Phase 1: Update Stub Backend (Current)
- [ ] Implement `/create_payment_intent` with `capture_method: 'manual'`
- [ ] Implement `/capture_payment_intent`
- [ ] Implement `/cancel_payment_intent`
- [ ] Rename `/login-by-card` → `/login-by-payment`
- [ ] Update simulation `door-close` to call capture

### Phase 2: Update Android App (Current)
- [ ] Implement Pre-Auth flow in `MainActivity.kt`
- [ ] Update `BackendService.kt` with new endpoints
- [ ] Remove SetupIntent code paths
- [ ] Test full flow with stub

### Phase 3: Stripe Certification (Before Medusa)
- [ ] Pass all certification tests with stub
- [ ] Get app approved in Play Store (internal track)

### Phase 4: Implement Medusa Endpoints (Later)
- [ ] Add `/connection_token` to venloop-pos-plugin
- [ ] Add `/store/auth/login-by-payment` to venloop-pos-plugin
- [ ] Add Tap-to-Pay capture workflow
- [ ] Add Firestore integration
- [ ] Test migration: change only `BACKEND_URL`

---

## API Response Formats (Contract)

### `/connection_token`
```json
{
  "secret": "pst_test_YWNjdF8..."
}
```

### `/create_payment_intent`
```json
{
  "id": "pi_3ABC123",
  "secret": "pi_3ABC123_secret_XYZ"
}
```

### `/store/auth/login-by-payment`
```json
{
  "session_id": "sess_abc123",
  "customer_id": "cust_xyz789",
  "fingerprint": "fp_card123"
}
```

### `/store/carts/{id}`
```json
{
  "cart": {
    "id": "cart_abc",
    "items": [
      { "id": "item_1", "title": "Cola", "quantity": 1, "unit_price": 250 },
      { "id": "item_2", "title": "Jar Deposit", "quantity": 1, "unit_price": 50 }
    ],
    "total": 300,
    "currency_code": "usd"
  }
}
```

### Firebase `sessions/{session_id}`
```json
{
  "status": "shopping" | "completed" | "cancelled",
  "cart_id": "cart_abc",
  "payment_intent_id": "pi_3ABC123",
  "customer_id": "cust_xyz789",
  "updated_at": "2025-01-15T14:00:00Z"
}
```
