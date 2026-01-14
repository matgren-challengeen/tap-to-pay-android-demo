import { Router } from 'express';
import { getDb } from '../firebase';
import { mockCarts } from './carts';
import { eventStore } from '../data/eventStore';

const router = Router();

// POST /simulate/reset - Clear all events and carts for a fresh start
router.post('/reset', (req, res) => {
    eventStore.clear();
    mockCarts.clear();
    console.log('[SIMULATION] Event store and carts cleared');
    res.json({ status: 'ok', message: 'Simulation reset - events and carts cleared' });
});

// GET /simulate/events
router.get('/events', (req, res) => {
    const since = parseInt(req.query.since as string) || 0;
    const events = eventStore.getSince(since);
    res.json({ events });
});

// POST /simulate/door-open
// Simulates the door opening event which typically creates a session or updates status
router.post('/door-open', async (req, res) => {
    const { session_id } = req.body;

    console.log(`[SIMULATION] Door Open triggered for session: ${session_id}`);

    const db = getDb();
    if (db && session_id) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'door_open',
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> status: door_open`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: 'Door open simulated' });
});

// POST /simulate/door-close
router.post('/door-close', async (req, res) => {
    const { session_id } = req.body;
    console.log(`[SIMULATION] Door Close triggered for session: ${session_id}`);
    const db = getDb();
    if (db && session_id) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'completed', // Or 'processing'
                updatedAt: new Date().toISOString()
            }, { merge: true });
        } catch (e) { console.error(e); }
    }
    res.json({ status: 'ok', message: 'Door closed simulated' });
});

// POST /simulate/item-picked
router.post('/item-picked', async (req, res) => {
    const { session_id, item_id, price, deposit } = req.body;
    console.log(`[SIMULATION] Item Picked: ${item_id} (${price}) Deposit: ${deposit || 0}`);

    // Product catalog for proper naming
    const productNames: { [key: number]: string } = {
        1: 'Cola',
        2: 'Bagel',
        3: 'Soup Jar'
    };
    const productName = productNames[item_id as number] || `Product ${item_id}`;

    const db = getDb();
    if (db && session_id) {
        // 1. Update/Create Mock Cart
        const cartId = `mock_cart_${session_id}`;

        // Retrieve existing or create new
        let cart = mockCarts.get(cartId) || {
            id: cartId,
            items: [],
            total: 0,
            currency_code: 'usd'
        };

        // Add main item
        cart.items.push({
            id: `item_${Date.now()}_prod`,
            title: productName,
            quantity: 1,
            unit_price: price
        });
        cart.total += price;

        // Add deposit item if exists
        if (deposit && deposit > 0) {
            cart.items.push({
                id: `item_${Date.now()}_dep`,
                title: 'Jar Deposit',
                quantity: 1,
                unit_price: deposit
            });
            cart.total += deposit;
            console.log(`[SIMULATION] Added Deposit: ${deposit}`);
        }

        mockCarts.set(cartId, cart);
        console.log(`[SIMULATION] Updated Mock Cart: ${cartId} (Items: ${cart.items.length}, Total: ${cart.total})`);

        try {
            // 2. Update Firestore Parent Document (Trigger App Listener)
            await db.collection('sessions').doc(session_id).set({
                cart_id: cartId,
                updatedAt: new Date().toISOString()
            }, { merge: true });

            // 3. Log event (Optional)
            await db.collection('sessions').doc(session_id).collection('events').add({
                type: 'item_picked',
                item_id,
                price,
                timestamp: new Date().toISOString()
            });

            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> cart_id: ${cartId}`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: `Item ${item_id} picked` });
});

// POST /simulate/item-returned
router.post('/item-returned', async (req, res) => {
    const { session_id, scale_id } = req.body;
    console.log(`[SIMULATION] Item Returned on Scale ${scale_id}`);

    const cartId = `mock_cart_${session_id}`;
    let cart: any = mockCarts.get(cartId) || {
        id: cartId,
        items: [],
        total: 0,
        currency_code: 'usd',
        foreign_container_count: 0 // New Field
    };

    // Ensure field exists if cart retrieved from cache
    if (cart.foreign_container_count === undefined) cart.foreign_container_count = 0;

    // Mock Logic
    if (scale_id === 1) {
        // Case A: Eligible (Standard Refund)
        const refundAmount = -50;
        cart.items.push({
            id: `return_${Date.now()}`,
            title: 'Jar Deposit Refund',
            quantity: 1,
            unit_price: refundAmount
        });
        cart.total += refundAmount;
        console.log(`[SIMULATION] Eligible Return: Refunded -0.50`);
        eventStore.add(`Eligible Jar Returned (-$0.50)`, 'success', 'hardware');
    } else {
        // Case B: Foreign (No Refund, just Count)
        cart.foreign_container_count += 1;
        console.log(`[SIMULATION] Foreign Return: Count ${cart.foreign_container_count}`);
        eventStore.add(`Foreign Jar Returned (Total: ${cart.foreign_container_count})`, 'warning', 'hardware');
    }

    mockCarts.set(cartId, cart);
    res.json({ success: true, cart });
});

// POST /simulate/return-close
// DEPRECATED: Use /simulate/door-close
router.post('/return-close', async (req, res) => {
    res.json({ success: true, message: "Use /door-close" });
});

// POST /simulate/door-close
router.post('/door-close', async (req, res) => {
    const { session_id } = req.body;
    console.log(`[SIMULATION] Door Closed for session: ${session_id}`);

    if (session_id && session_id.includes('return')) {
        eventStore.add(`Return Session Finalized (Door Closed)`, 'info', 'backend');
    } else {
        eventStore.add(`Purchase Session Finalized (Door Closed)`, 'info', 'backend');
    }

    res.json({ success: true });
});

export default router;
