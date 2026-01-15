# Sequence Diagrams: Venloop Tap to Pay Flow

## 1. Flow: Standard Purchase with Pre-Auth + Capture (CORRECT)

This flow shows the **Stripe-certified** pattern: Pre-authorize at tap, Capture at door close.

> [!IMPORTANT]
> The card MUST be present during authorization (tap). Capture happens server-side when door closes.

```mermaid
sequenceDiagram
    participant User
    participant App as Android App
    participant Stripe as Stripe API
    participant Backend as Medusa/Backend
    participant HW as Hardware (MQTT)
    participant FB as Firebase

    Note over User, FB: Phase 1: Pre-Authorization (Card Present)
    
    User->>App: Opens app, sees "Tap to Pay"
    App->>Backend: POST /store/auth/create-payment-intent<br/>{amount: 5000, capture_method: 'manual'}
    Backend->>Stripe: paymentIntents.create()
    Stripe-->>Backend: {client_secret, id}
    Backend-->>App: {client_secret, payment_intent_id}
    
    App->>App: Terminal.retrievePaymentIntent(client_secret)
    App->>App: Terminal.collectPaymentMethod(paymentIntent)
    User->>App: 💳 Taps Card
    App->>App: Terminal.confirmPaymentIntent()
    App->>Stripe: Confirm (via Terminal SDK)
    Stripe-->>App: PaymentIntent status = requires_capture
    
    Note over App, Stripe: ✅ $50 blocked on card (Pre-Auth)
    
    App->>Backend: POST /store/auth/login-by-payment<br/>{payment_intent_id}
    Backend->>Stripe: paymentIntents.retrieve(id)
    Stripe-->>Backend: {fingerprint, payment_method_details}
    Backend->>Backend: Find/Create Customer by fingerprint
    Backend->>FB: Create session/{session_id}
    Backend->>HW: MQTT: unlock_door
    Backend-->>App: {session_id, customer_id}
    
    Note over User, FB: Phase 2: Shopping (Realtime Updates)
    
    App->>FB: Subscribe to sessions/{session_id}
    HW-->>User: 🚪 Door Unlocks
    User->>HW: Opens door, picks items
    HW->>Backend: MQTT: item_picked {item, price}
    Backend->>FB: Update cart in session
    FB-->>App: Cart update event
    App->>User: Shows updated cart
    
    Note over User, FB: Phase 3: Capture (Backend-Driven)
    
    User->>HW: Closes door
    HW->>Backend: MQTT: door_closed
    Backend->>Backend: Calculate final amount ($12.50)
    Backend->>Stripe: paymentIntents.capture(id, {amount: 1250})
    Stripe-->>Backend: PaymentIntent status = succeeded
    
    Note over Backend, Stripe: ✅ $12.50 captured, $37.50 released
    
    Backend->>FB: Update session: status='completed', total=1250
    FB-->>App: Status = completed
    App->>User: Shows Summary: "Thank you! $12.50"
```

## 1b. Flow: Zero Amount Session (User Opened Door But Took Nothing)

```mermaid
sequenceDiagram
    participant Backend as Medusa/Backend
    participant Stripe as Stripe API
    
    Note over Backend, Stripe: Door closed, cart is empty
    
    Backend->>Backend: Final amount = $0
    Backend->>Stripe: paymentIntents.cancel(id)
    Stripe-->>Backend: PaymentIntent status = canceled
    
    Note over Backend, Stripe: ✅ Pre-auth released, no charge
```


## 2. Flow: Timeout Sequence (No Action)

This flow occurs if the user taps their card but walks away or fails to open the door within 15 seconds.

```mermaid
sequenceDiagram
    participant User
    participant App
    participant Stub
    participant Sim as Simulator UI

    User->>App: Taps Card
    App->>Stub: Login
    Stub->>Sim: Event "Transaction Open"
    
    Note over Sim: "OPEN DOOR" Button Unlocks
    Note over Sim: Timer Started (15s)
    
    rect rgb(255, 240, 240)
        Note right of User: User waits > 15 seconds
    end
    
    Note over Sim: Timer Expires
    Note over Sim: "OPEN DOOR" Button Re-Locks (Gray)
    
    opt Backend Timeout (Future)
        Sim->>Stub: POST /simulate/timeout
        Stub-->>App: Cancel Session
    end
```

## 3. Flow: Return Containers (Deposit Return)

This flow allows users to return used jars. The backend verifies if they have "eligibility" (previously purchased jars) to determine if they get a cash refund or just a "Foreign Container" count.

```mermaid
sequenceDiagram
    participant User
    participant App
    participant Stub
    participant Sim as Simulator UI

    Note over User, App: Phase 1: Initiation & Auth
    User->>App: Clicks "Return Containers"
    App->>User: Prompts "Tap Card to Identify"
    
    User->>Reader: Taps Physical Card (or Simulator)
    Reader->>App: Card Data
    App->>Stub: POST /auth/login-return (PM ID)
    
    Stub->>Stub: Check User History (Has previous jars?)
    Stub->>Sim: Event "Return Mode Active"
    Stub-->>App: 200 OK (Session ID, ReturnableCount: 2)

    Note over App: Shows "Containers to return: 2"
    Note over Sim: Simulator Switches to "Return View" (2 Shelves)
    Note over Sim: "OPEN DOOR" Button Unlocks
    
    User->>Sim: Clicks "OPEN DOOR"
    Sim->>Stub: POST /simulate/door-open
    
    Note over User, Sim: Phase 2: User Places Jars
    
    rect rgb(240, 255, 240)
        Note right of Stub: Case A: Eligible Jar (e.g., Scale 1)
        User->>Sim: Clicks "Place Jar" (Scale 1)
        Sim->>Stub: POST /simulate/item-returned {scale: 1}
        Stub->>Stub: Add "Deposit Refund" (-$0.50) to Cart
    end
    
    rect rgb(255, 245, 240)
        Note right of Stub: Case B: Foreign Jar (e.g., Scale 2)
        User->>Sim: Clicks "Place Jar" (Scale 2)
        Sim->>Stub: POST /simulate/item-returned {scale: 2}
        Stub->>Stub: Increment "Foreign Container Count"
        Note right of Stub: NO Item added to Cart
    end
    
    Stub-->>App: Firestore Update
    App->>User: Shows Refund or Container Count

    Note over User, Sim: Phase 3: Completion
    User->>Sim: Clicks "CLOSE DOOR"
    Sim->>Stub: POST /simulate/door-close
    Stub->>Stub: Finalize Session (Issue Credit/Payout)
    App->>User: Shows "Containers Returned" Summary
```

## 2. Deposit Flow: Jar Purchase

```mermaid
sequenceDiagram
    participant User
    participant App as Android App
    participant Stub as Stub Backend
    participant Sim as Simulator UI (Web)

    User->>App: Transaction Initiated
    Stub->>Sim: Event "Transaction Open"
    User->>Sim: Clicks "OPEN DOOR"
    
    Note over User, Sim: User picks a Jar
    User->>Sim: Clicks "Pick Soup Jar"
    Sim->>Stub: POST /simulate/item-picked (ItemId: soup, Price: 500, Deposit: 50)
    
    Stub->>Stub: Add Item: Soup (5.00)
    Stub->>Stub: Add Item: Jar Deposit (0.50)
    Stub-->>App: Firestore Update (2 Items)
    
    App->>User: App Shows:<br/>1. Soup Jar<br/>2. Jar Deposit
    
    User->>Sim: Clicks "CLOSE DOOR"
    Stub->>Stub: Finalize Session
    App->>User: Summary: $5.50
```
