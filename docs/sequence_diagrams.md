# Sequence Diagrams: Venloop Tap to Pay Flow

## 1. Flow: Standard Purchase (Virtual or Real)

This flow covers users buying items (Standard or Jars with Deposit).
Auth provided via Emulator (Simulate Tap) or Real Device (NFC Tap).

```mermaid
sequenceDiagram
    participant User
    participant App
    participant Stub
    participant Sim as Simulator UI

    Note over User, Sim: Phase 1: Authentication
    User->>App: Taps Card (or Simulates Tap)
    App->>Stub: POST /login-by-card
    Stub->>Sim: Event "Transaction Open"
    
    Note over Sim: "OPEN DOOR" Button Unlocks
    
    Note over User, Sim: Phase 2: Shopping
    User->>Sim: Clicks "OPEN DOOR"
    Sim->>Stub: POST /simulate/door-open
    
    rect rgb(240, 240, 255)
        Note right of User: Scenario A: Standard Item
        User->>Sim: Clicks "Pick Cola"
        Sim->>Stub: POST /simulate/item-picked (Price)
        Stub->>Stub: Add "Cola" to Cart
    end

    rect rgb(255, 240, 255)
        Note right of User: Scenario B: Jar with Deposit
        User->>Sim: Clicks "Pick Soup Jar"
        Sim->>Stub: POST /simulate/item-picked (Price + Deposit)
        Stub->>Stub: Add "Soup" + "Deposit" to Cart
    end

    Stub-->>App: Firestore Update
    App->>User: Shows Cart Items

    Note over User, Sim: Phase 3: Completion
    User->>Sim: Clicks "CLOSE DOOR"
    Sim->>Stub: POST /simulate/door-close
    Stub->>Stub: Finalize Session
    App->>User: Shows Summary Screen
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
