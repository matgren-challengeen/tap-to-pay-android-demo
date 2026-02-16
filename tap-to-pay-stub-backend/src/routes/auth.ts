import { Router } from 'express';
import { getDb } from '../firebase';
import { stripe } from '../stripe';
import { eventStore } from '../data/eventStore';
import { mockCarts } from './carts';
import { customerStore } from '../data/customers';

const router = Router();

// In-memory store for sessions
const sessions = new Map<string, {
    payment_intent_id: string;
    customer_id: string;
    fingerprint: string;
    cart_id: string;
    status: string;
}>();

// POST /store/auth/login-by-payment
// Accept authorized PaymentIntent, extract fingerprint, create session
router.post('/login-by-payment', async (req, res) => {
    try {
        const { payment_intent_id, manual_fingerprint } = req.body;

        if (!payment_intent_id && !manual_fingerprint) {
            return res.status(400).json({ error: 'payment_intent_id or manual_fingerprint is required' });
        }

        console.log(`[STUB] login-by-payment: pi=${payment_intent_id}, manual_fp=${manual_fingerprint}`);

        // Extract fingerprint from payment method or use manual fingerprint (for emulator mode)
        let fingerprint = 'fp_unknown';
        let last4 = '****';

        if (manual_fingerprint) {
            // Emulator mode: use the provided fingerprint directly
            fingerprint = manual_fingerprint;
            last4 = 'SIM';
            console.log(`[STUB] Using manual fingerprint (emulator mode): ${fingerprint}`);
        } else {
            // Real mode: retrieve PaymentIntent from Stripe to get fingerprint
            const paymentIntent = await stripe.paymentIntents.retrieve(payment_intent_id, {
                expand: ['payment_method']
            });

            if (paymentIntent.payment_method && typeof paymentIntent.payment_method === 'object') {
                const pm = paymentIntent.payment_method as any;
                if (pm.card_present) {
                    fingerprint = pm.card_present.fingerprint || 'fp_unknown';
                    last4 = pm.card_present.last4 || '****';
                } else if (pm.card) {
                    fingerprint = pm.card.fingerprint || 'fp_unknown';
                    last4 = pm.card.last4 || '****';
                }
            }
        }

        console.log(`[STUB] Extracted fingerprint: ${fingerprint}, last4: ${last4}`);

        // Handle customer identity
        const customer = customerStore.getOrCreate(fingerprint);
        const customer_id = customer.customer_id;

        const session_id = `sess_${Date.now()}`;
        const cart_id = `mock_cart_${session_id}`;

        // Mock JWT token (Medusa v2 format)
        const token = `mock_jwt_${Buffer.from(JSON.stringify({ customer_id, fingerprint })).toString('base64')}`;

        // Initialize empty cart
        mockCarts.set(cart_id, {
            id: cart_id,
            items: [],
            total: 0,
            currency_code: 'usd'
        });

        // Store session
        sessions.set(session_id, {
            payment_intent_id,
            customer_id,
            fingerprint,
            cart_id,
            status: 'authenticated'
        });

        // Log events
        eventStore.add(`Card Identified: ****${last4} (${fingerprint.substring(0, 8)}...)`, 'info', 'app');
        eventStore.add(`Customer: ${customer_id}`, 'success', 'backend');
        eventStore.add(`Session Created: ${session_id}`, 'info', 'backend');

        // Update Firestore for app real-time updates
        const db = getDb();
        if (db) {
            await db.collection('sessions').doc(session_id).set({
                status: 'authenticated',
                cart_id,
                payment_intent_id,
                customer_id,
                fingerprint,
                updatedAt: new Date().toISOString()
            });
            console.log(`[STUB] Firestore session created: ${session_id}`);
        }

        res.json({
            token,
            session_id,
            customer_id,
            fingerprint,
            cart_id
        });

    } catch (err: any) {
        console.error('Error in login-by-payment:', err);
        res.status(500).json({ error: err.message });
    }
});

// POST /store/auth/login-return (for return flow)
router.post('/login-return', async (req, res) => {
    try {
        // Retrieve PaymentIntent from Stripe (or use manual fingerprint if provided)
        const { payment_intent_id, manual_fingerprint } = req.body;

        let fingerprint = manual_fingerprint;
        let last4 = '****';

        if (!fingerprint && payment_intent_id) {
            const paymentIntent = await stripe.paymentIntents.retrieve(payment_intent_id, {
                expand: ['payment_method']
            });
            if (paymentIntent.payment_method && typeof paymentIntent.payment_method === 'object') {
                const pm = paymentIntent.payment_method as any;
                fingerprint = pm.card_present?.fingerprint || pm.card?.fingerprint || 'fp_unknown';
                last4 = pm.card_present?.last4 || pm.card?.last4 || '****';
            }
        }

        if (!fingerprint) {
            return res.status(400).json({ error: 'fingerprint or payment_intent_id required' });
        }

        const customer = customerStore.getOrCreate(fingerprint);
        const session_id = 'return_sess_' + Date.now();

        // Calculate total returnable jars from actual order history
        const { orderStore } = require('../data/orders');
        const returnable_count = orderStore.getReturnableCount(customer.customer_id);

        eventStore.add(`Return Mode: ${customer.customer_id} (Can return: ${returnable_count})`, 'info', 'app');
        eventStore.add(`Session Created: ${session_id}`, 'info', 'backend');

        const db = getDb();
        if (db) {
            await db.collection('sessions').doc(session_id).set({
                status: 'return_mode',
                customer_id: customer.customer_id,
                fingerprint: customer.fingerprint,
                returnable_count,
                payment_intent_id,
                updatedAt: new Date().toISOString()
            });
        }

        res.json({
            token: `mock_jwt_${Buffer.from(JSON.stringify({ customer_id: customer.customer_id, fingerprint })).toString('base64')}`,
            session_id,
            customer_id: customer.customer_id,
            returnable_count
        });
    } catch (err: any) {
        console.error('Error in login-return:', err);
        res.status(500).json({ error: err.message });
    }
});

// Export sessions for use in simulation routes
export { sessions };
export default router;

