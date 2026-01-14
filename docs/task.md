# Tasks

- [x] Fetch repo https://github.com/matgren-challengeen/tap-to-pay-android-demo into Venloop workspace <!-- id: 0 -->
- [x] Verify existence and origin of `medusa-vending-storefront` (FOUND MISMATCH) <!-- id: 1 -->
- [x] Verify existence and origin of `medusa-vending-plugin` (CORRECT) <!-- id: 2 -->
- [x] Verify `tap-to-pay-android-demo` (already fetched) (CORRECT) <!-- id: 3 -->
- [x] Clone `medusa-vending-storefront` to new folder <!-- id: 4 -->
- [x] Checkout and pull latest for all 3 repositories <!-- id: 5 -->
- [x] Clone `medusa-vending` to new folder <!-- id: 6 -->

## Implementation: Login by Card
- [x] Review Product Requirements Document (PRD) (Updated) <!-- id: 12 -->
- [x] Approve Technical Architecture (User Approved) <!-- id: 13 -->
- [x] **Security & Failover Check** (Completed) <!-- id: 20 -->
- [x] **Project Reorg** (Renamed repo, moved docs) (Completed) <!-- id: 21 -->

## Implementation Phase 1: Stub Backend (Simulator)
- [x] Create `tap-to-pay-stub-backend` project (Node/Express) <!-- id: 22 -->
- [x] Implement `POST /store/auth/prepare-setup` (Stripe Token) <!-- id: 23 -->
- [x] Implement `POST /store/auth/login-by-card` (Mock Auth) <!-- id: 24 -->
- [x] Implement Firestore Mock Writer (Skeleton Implemented - Verified) <!-- id: 25 -->

## Implementation Phase 2: Android (Native)
- [x] Connect App to Stub Backend <!-- id: 26 -->
- [x] Strip out "Payment Intent" flow from `MainActivity` <!-- id: 14 -->
- [x] Implement `SetupIntent` flow (Prepare -> Collect -> Confirm -> Login) <!-- id: 15 -->
- [x] **Technical Foundation**: Add Firebase/Firestore SDKs & Permissions <!-- id: 16 -->
- [x] **Data Layer**: Create `SessionRepository` (Firestore Listener) & `CartRepository` (Medusa API) <!-- id: 17 -->
- [x] **UI Layer**: Build Native Shopping Screen (RecyclerView/Compose) <!-- id: 18 -->
- [x] **UI Layer**: Build Native Summary Screen <!-- id: 19 -->





