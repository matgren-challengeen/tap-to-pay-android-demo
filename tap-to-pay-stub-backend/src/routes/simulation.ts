import { Router } from 'express';
import { getDb } from '../firebase';
import { mockCarts } from './carts';
import { eventStore } from '../data/eventStore';
import { sessions } from './auth';
import { stripe } from '../stripe';

const router = Router();

// POST /simulate/reset - Clear all events, carts, and sessions for a fresh start
router.post('/reset', (req, res) => {
    eventStore.clear();
    mockCarts.clear();
    sessions.clear();
    console.log('[SIMULATION] Event store, carts, and sessions cleared');
    res.json({ status: 'ok', message: 'Simulation reset - all data cleared' });
});

// GET /simulate/events
router.get('/events', (req, res) => {
    const since = parseInt(req.query.since as string) || 0;
    const events = eventStore.getSince(since);
    res.json({ events });
});

// GET /simulate/sessions - List all active sessions
router.get('/sessions', (req, res) => {
    const sessionList = Array.from(sessions.entries()).map(([id, data]) => ({
        session_id: id,
        ...data
    }));
    res.json({ sessions: sessionList });
});

// POST /simulate/door-open
// Simulates the vending machine door unlocking and opening
router.post('/door-open', async (req, res) => {
    const { session_id } = req.body;

    if (!session_id) {
        return res.status(400).json({ error: 'session_id is required' });
    }

    console.log(`[SIMULATION] Door Open triggered for session: ${session_id}`);

    // Update session status
    const session = sessions.get(session_id);
    if (session) {
        session.status = 'shopping';
        sessions.set(session_id, session);
    }

    eventStore.add(`🚪 Door Opened`, 'success', 'hardware');

    const db = getDb();
    if (db) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'shopping',
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> status: shopping`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: 'Door open simulated' });
});

// POST /simulate/item-picked
router.post('/item-picked', async (req, res) => {
    const { session_id, item_id, price, deposit } = req.body;

    if (!session_id) {
        return res.status(400).json({ error: 'session_id is required' });
    }

    console.log(`[SIMULATION] Item Picked: item=${item_id}, price=${price}, deposit=${deposit || 0}`);

    // Product catalog for proper naming
    const productNames: { [key: number]: string } = {
        1: 'Cola',
        2: 'Bagel',
        3: 'Soup Jar'
    };
    const productName = productNames[item_id as number] || `Product ${item_id}`;

    // Get session to find cart_id
    const session = sessions.get(session_id);
    const cartId = session?.cart_id || `mock_cart_${session_id}`;

    // Retrieve existing or create new cart
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

    eventStore.add(`📦 ${productName} added ($${(price / 100).toFixed(2)})`, 'info', 'hardware');

    // Add deposit item if exists
    if (deposit && deposit > 0) {
        cart.items.push({
            id: `item_${Date.now()}_dep`,
            title: 'Jar Deposit',
            quantity: 1,
            unit_price: deposit
        });
        cart.total += deposit;
        eventStore.add(`🫙 Jar Deposit added ($${(deposit / 100).toFixed(2)})`, 'info', 'hardware');
    }

    mockCarts.set(cartId, cart);
    console.log(`[SIMULATION] Updated Cart: ${cartId} (Items: ${cart.items.length}, Total: $${(cart.total / 100).toFixed(2)})`);

    const db = getDb();
    if (db) {
        try {
            await db.collection('sessions').doc(session_id).set({
                cart_id: cartId,
                cart_total: cart.total,
                updatedAt: new Date().toISOString()
            }, { merge: true });
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: `Item ${item_id} picked`, cart });
});

// POST /simulate/door-close
// This is the KEY endpoint - triggers capture or cancel of the PaymentIntent
router.post('/door-close', async (req, res) => {
    const { session_id } = req.body;

    if (!session_id) {
        return res.status(400).json({ error: 'session_id is required' });
    }

    console.log(`[SIMULATION] Door Close triggered for session: ${session_id}`);

    const session = sessions.get(session_id);
    if (!session) {
        eventStore.add(`⚠️ Session not found: ${session_id}`, 'error', 'backend');
        return res.status(404).json({ error: 'Session not found' });
    }

    const cart = mockCarts.get(session.cart_id);
    const total = cart?.total || 0;
    const payment_intent_id = session.payment_intent_id;

    eventStore.add(`🚪 Door Closed`, 'info', 'hardware');
    eventStore.add(`💰 Cart Total: $${(total / 100).toFixed(2)}`, 'info', 'backend');

    let captureResult: any = null;

    // Capture or Cancel based on cart total
    if (total > 0 && payment_intent_id) {
        try {
            console.log(`[SIMULATION] Capturing PaymentIntent ${payment_intent_id} for $${(total / 100).toFixed(2)}`);

            const paymentIntent = await stripe.paymentIntents.capture(payment_intent_id, {
                amount_to_capture: total
            });

            eventStore.add(`✅ Payment Captured: $${(total / 100).toFixed(2)}`, 'success', 'backend');
            console.log(`[SIMULATION] Captured: ${paymentIntent.id}, status: ${paymentIntent.status}`);

            captureResult = {
                type: 'captured',
                amount: total,
                payment_intent_id: paymentIntent.id,
                status: paymentIntent.status
            };
        } catch (e: any) {
            console.error('[SIMULATION] Capture error:', e.message);
            eventStore.add(`❌ Capture Failed: ${e.message}`, 'error', 'backend');
            captureResult = { type: 'error', error: e.message };
        }
    } else if (payment_intent_id) {
        // Cart is empty - cancel the pre-auth
        try {
            console.log(`[SIMULATION] Canceling PaymentIntent ${payment_intent_id} (empty cart)`);

            const paymentIntent = await stripe.paymentIntents.cancel(payment_intent_id);

            eventStore.add(`🔄 Pre-auth Released (empty cart)`, 'info', 'backend');
            console.log(`[SIMULATION] Canceled: ${paymentIntent.id}, status: ${paymentIntent.status}`);

            captureResult = {
                type: 'canceled',
                payment_intent_id: paymentIntent.id,
                status: paymentIntent.status
            };
        } catch (e: any) {
            console.error('[SIMULATION] Cancel error:', e.message);
            eventStore.add(`⚠️ Cancel Failed: ${e.message}`, 'warning', 'backend');
            captureResult = { type: 'error', error: e.message };
        }
    }

    // Update session status
    session.status = 'completed';
    sessions.set(session_id, session);

    // Update Firestore
    const db = getDb();
    if (db) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'completed',
                final_total: total,
                capture_result: captureResult,
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> completed`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({
        status: 'ok',
        message: 'Door closed, session completed',
        session_id,
        final_total: total,
        capture_result: captureResult
    });
});

// POST /simulate/item-returned (for return flow)
router.post('/item-returned', async (req, res) => {
    const { session_id, scale_id } = req.body;
    console.log(`[SIMULATION] Item Returned on Scale ${scale_id}`);

    const cartId = `mock_cart_${session_id}`;
    let cart: any = mockCarts.get(cartId) || {
        id: cartId,
        items: [],
        total: 0,
        currency_code: 'usd',
        foreign_container_count: 0
    };

    if (cart.foreign_container_count === undefined) cart.foreign_container_count = 0;

    if (scale_id === 1) {
        // Eligible jar - refund
        const refundAmount = -50;
        cart.items.push({
            id: `return_${Date.now()}`,
            title: 'Jar Deposit Refund',
            quantity: 1,
            unit_price: refundAmount
        });
        cart.total += refundAmount;
        eventStore.add(`🫙 Eligible Jar Returned (-$0.50)`, 'success', 'hardware');
    } else {
        // Foreign jar - no refund
        cart.foreign_container_count += 1;
        eventStore.add(`🫙 Foreign Jar (Count: ${cart.foreign_container_count})`, 'warning', 'hardware');
    }

    mockCarts.set(cartId, cart);
    res.json({ success: true, cart });
});

export default router;

