import { Router } from 'express';
import { getDb } from '../firebase';
import { mockCarts } from './carts';
import { eventStore } from '../data/eventStore';
import { sessions } from './auth';
import { stripe } from '../stripe';
import { customerStore } from '../data/customers';
import { orderStore } from '../data/orders';

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

    // Item is added to mock cart. 
    mockCarts.set(cartId, cart);
    console.log(`[SIMULATION] Item added. Updated Cart: ${cartId} (Items: ${cart.items.length}, Total: $${(cart.total / 100).toFixed(2)})`);

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

// POST /simulate/item-removed
// Simulates an item being put back (regular vending) or taken out (reverse vending)
router.post('/item-removed', async (req, res) => {
    const { session_id, item_id, price, deposit } = req.body;

    if (!session_id) {
        return res.status(400).json({ error: 'session_id is required' });
    }

    // Product catalog for proper naming
    const productNames: { [key: number]: string } = {
        1: 'Cola',
        2: 'Bagel',
        3: 'Soup Jar'
    };
    const productName = productNames[item_id as number] || `Product ${item_id}`;

    console.log(`[SIMULATION] Item Removed triggered: item=${productName}, price=${price}, deposit=${deposit || 0}`);

    const session = sessions.get(session_id);
    const cartId = session?.cart_id || `mock_cart_${session_id}`;
    let cart = mockCarts.get(cartId);

    if (!cart || cart.items.length === 0) {
        return res.status(400).json({ error: 'Cart is empty' });
    }

    // Find and remove the item
    const itemIndex = cart.items.findIndex(item => item.title === productName);
    if (itemIndex === -1) {
        return res.status(400).json({ error: `Item ${productName} not found in cart` });
    }

    cart.items.splice(itemIndex, 1);
    cart.total -= price;

    eventStore.add(`🔄 ${productName} removed (-$${(price / 100).toFixed(2)})`, 'warning', 'hardware');

    // Also remove deposit if it matches
    if (deposit && deposit > 0) {
        const depositIndex = cart.items.findIndex(item => item.title === 'Jar Deposit');
        if (depositIndex !== -1) {
            cart.items.splice(depositIndex, 1);
            cart.total -= deposit;
            eventStore.add(`🔄 Jar Deposit removed (-$${(deposit / 100).toFixed(2)})`, 'warning', 'hardware');
        }
    }

    mockCarts.set(cartId, cart);
    console.log(`[SIMULATION] Item removed. Updated Cart: ${cartId} (Items: ${cart.items.length}, Total: $${(cart.total / 100).toFixed(2)})`);

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

    res.json({ status: 'ok', message: `Item ${item_id} removed`, cart });
});

// POST /simulate/door-close
// This is the KEY endpoint - triggers capture or cancel of the PaymentIntent
// For return sessions, it processes the refund instead
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
    const isReturnSession = session_id.startsWith('return_sess_') || session.status === 'return_mode';

    eventStore.add(`🚪 Door Closed`, 'info', 'hardware');

    let result: any = null;

    if (isReturnSession) {
        // RETURN FLOW: Process refund
        console.log(`[SIMULATION] Return session detected. Refund amount: $${(total / 100).toFixed(2)}`);
        eventStore.add(`💰 Refund Amount: $${(total / 100).toFixed(2)}`, 'success', 'backend');

        if (total > 0) {
            // In simulator mode, we just log the refund
            if (session_id.startsWith('return_sess_') || payment_intent_id?.startsWith('emulator_')) {
                console.log(`[SIMULATION] Emulator session: skipping Stripe refund, logging $${(total / 100).toFixed(2)} refund`);
                eventStore.add(`✅ Refund Processed: $${(total / 100).toFixed(2)} (Simulated)`, 'success', 'backend');
            } else {
                // In production, would call Stripe refund API here
                // const refund = await stripe.refunds.create({ payment_intent: payment_intent_id, amount: total });
                console.log(`[SIMULATION] Would process Stripe refund here for ${payment_intent_id}`);
            }

            result = {
                type: 'refunded',
                amount: total,
                message: `Refund of $${(total / 100).toFixed(2)} processed`
            };
        } else {
            eventStore.add(`ℹ️ No containers returned`, 'info', 'backend');
            result = {
                type: 'no_refund',
                message: 'No eligible containers were returned'
            };
        }
    } else {
        // PURCHASE FLOW: Capture or Cancel based on cart total
        eventStore.add(`💰 Cart Total: $${(total / 100).toFixed(2)}`, 'info', 'backend');

        if (total > 0 && payment_intent_id) {
            try {
                console.log(`[SIMULATION] Processing PaymentIntent ${payment_intent_id} for $${(total / 100).toFixed(2)}`);

                let paymentIntent: any = { id: payment_intent_id, status: 'succeeded' };
                if (payment_intent_id.startsWith('emulator_')) {
                    console.log(`[SIMULATION] Emulator session: skipping Stripe capture`);
                } else {
                    paymentIntent = await stripe.paymentIntents.capture(payment_intent_id, {
                        amount_to_capture: total
                    });
                    console.log(`[SIMULATION] Captured: ${paymentIntent.id}, status: ${paymentIntent.status}`);
                }

                // Record the purchase as a completed Order in our history
                orderStore.addOrder(session.customer_id, cart.items.map(item => ({
                    id: item.title === 'Soup (Small Jar)' || item.title === 'Soup Jar' ? '3' :
                        item.title === 'Soup (Big Jar)' ? '4' : 'unknown',
                    quantity: item.quantity
                })));

                eventStore.add(`✅ Payment Captured: $${(total / 100).toFixed(2)}`, 'success', 'backend');

                result = {
                    type: 'captured',
                    amount: total,
                    payment_intent_id: paymentIntent.id,
                    status: paymentIntent.status
                };
            } catch (e: any) {
                console.error('[SIMULATION] Capture error:', e.message);
                eventStore.add(`❌ Capture Failed: ${e.message}`, 'error', 'backend');
                result = { type: 'error', error: e.message };
            }
        } else if (payment_intent_id) {
            // Cart is empty - cancel the pre-auth
            try {
                console.log(`[SIMULATION] Closing session with PaymentIntent ${payment_intent_id} (empty cart)`);

                let paymentIntent: any = { id: payment_intent_id, status: 'canceled' };
                if (payment_intent_id.startsWith('emulator_')) {
                    console.log(`[SIMULATION] Emulator session: skipping Stripe cancel`);
                } else {
                    paymentIntent = await stripe.paymentIntents.cancel(payment_intent_id);
                    console.log(`[SIMULATION] Canceled: ${paymentIntent.id}, status: ${paymentIntent.status}`);
                }

                eventStore.add(`🔄 Pre-auth Released (empty cart)`, 'info', 'backend');

                result = {
                    type: 'canceled',
                    payment_intent_id: paymentIntent.id,
                    status: paymentIntent.status
                };
            } catch (e: any) {
                console.error('[SIMULATION] Cancel error:', e.message);
                eventStore.add(`⚠️ Cancel Failed: ${e.message}`, 'warning', 'backend');
                result = { type: 'error', error: e.message };
            }
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
                is_return: isReturnSession,
                result: result,
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> completed`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({
        status: 'ok',
        message: isReturnSession ? 'Return session completed' : 'Door closed, session completed',
        session_id,
        final_total: total,
        is_return: isReturnSession,
        result: result
    });
});

// POST /simulate/timeout
// Handles session timeout (e.g., user didn't open door in 15s)
router.post('/timeout', async (req, res) => {
    const { session_id } = req.body;

    if (!session_id) {
        return res.status(400).json({ error: 'session_id is required' });
    }

    console.log(`[SIMULATION] Timeout triggered for session: ${session_id}`);

    const session = sessions.get(session_id);
    if (!session) {
        return res.status(404).json({ error: 'Session not found' });
    }

    const payment_intent_id = session.payment_intent_id;

    // 1. Cancel the pre-auth if it exists
    if (payment_intent_id) {
        try {
            console.log(`[SIMULATION] Handling timeout for PaymentIntent ${payment_intent_id}`);

            if (payment_intent_id.startsWith('emulator_')) {
                console.log(`[SIMULATION] Emulator session: skipping Stripe cancel (timeout)`);
            } else {
                await stripe.paymentIntents.cancel(payment_intent_id);
            }

            eventStore.add(`🔄 Pre-auth Released (Timeout: door not opened)`, 'info', 'backend');
        } catch (e: any) {
            console.error('[SIMULATION] Cancel error during timeout:', e.message);
            eventStore.add(`⚠️ Timeout: Cancel Failed: ${e.message}`, 'warning', 'backend');
        }
    }

    // 2. Update session status to error/timeout (code 403 matches storefront expectation)
    session.status = 'error';
    // @ts-ignore - code is a custom property we use for Medusa v2/storefront compatibility
    session.code = 403;
    sessions.set(session_id, session);

    eventStore.add(`⏰ TIMEOUT: Session expired (door not opened)`, 'error', 'backend');

    // 3. Update Firestore to notify storefront
    const db = getDb();
    if (db) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'error',
                code: 403,
                last_error: {
                    message: "Timeout – no response in the given time.",
                    code: 403,
                    timestamp: Date.now()
                },
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> error (403)`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: 'Timeout processed' });
});

// POST /simulate/item-returned (for return flow)
router.post('/item-returned', async (req, res) => {
    const { session_id, jar_type, qty = 1 } = req.body;

    const session = sessions.get(session_id);
    if (!session || !session.fingerprint) {
        return res.status(400).json({ error: 'Session not found or not authenticated' });
    }

    console.log(`[SIMULATION] Item Returned: type=${jar_type}, qty=${qty} for customer ${session.customer_id}`);

    const cartId = session.cart_id || `mock_cart_${session_id}`;
    let cart: any = mockCarts.get(cartId) || {
        id: cartId,
        items: [],
        total: 0,
        currency_code: 'usd'
    };

    // Check eligibility in backend history using FIFO order consumption
    const type = jar_type === 'big_jar' ? 'big_jar' : 'small_jar';
    const { eligible, foreign } = orderStore.consumeReturn(session.customer_id, type, qty);

    if (eligible > 0) {
        const refundAmount = type === 'big_jar' ? 70 : 50; // cents (positive for refund)
        const totalRefund = refundAmount * eligible;

        cart.items.push({
            id: `return_${Date.now()}`,
            title: `${type === 'big_jar' ? 'Big' : 'Small'} Jar Refund`,
            quantity: eligible,
            unit_price: refundAmount
        });
        cart.total += totalRefund;

        eventStore.add(`🫙 ${eligible}x Eligible ${type} Returned (+$${(totalRefund / 100).toFixed(2)})`, 'success', 'hardware');
    }

    if (foreign > 0) {
        eventStore.add(`🫙 ${foreign}x Foreign Jar (No history for ${type})`, 'warning', 'hardware');
    }

    mockCarts.set(cartId, cart);

    // Update Firestore so Android app can display returned items
    const db = getDb();
    if (db) {
        try {
            await db.collection('sessions').doc(session_id).set({
                cart_id: cartId,
                cart_total: cart.total,
                returned_items: cart.items,
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated with return data for session ${session_id}`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ success: true, cart, eligible, foreign });
});

export default router;

