import { Router } from 'express';
import { getDb } from '../firebase';
import { stripe } from '../stripe';
import { eventStore } from '../data/eventStore';
import { mockCarts } from './carts';

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
        const { payment_intent_id } = req.body;

        if (!payment_intent_id) {
            return res.status(400).json({ error: 'payment_intent_id is required' });
        }

        console.log(`[STUB] login-by-payment: ${payment_intent_id}`);

        // Retrieve PaymentIntent from Stripe to get fingerprint
        const paymentIntent = await stripe.paymentIntents.retrieve(payment_intent_id, {
            expand: ['payment_method']
        });

        // Extract fingerprint from payment method
        let fingerprint = 'fp_unknown';
        let last4 = '****';

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

        console.log(`[STUB] Extracted fingerprint: ${fingerprint}, last4: ${last4}`);

        // In a real implementation, we'd look up customer by fingerprint
        // For stub, we create a mock customer
        const customer_id = `cust_${fingerprint.substring(0, 8)}`;
        const session_id = `sess_${Date.now()}`;
        const cart_id = `mock_cart_${session_id}`;

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
        const { payment_intent_id } = req.body;
        console.log(`[STUB] login-return: ${payment_intent_id}`);

        // Similar to login-by-payment but for return containers flow
        const session_id = 'return_sess_' + Date.now();
        const customer_id = 'cust_returner';
        const returnable_count = 2; // Mock: user has 2 jars to return

        eventStore.add(`Return Mode Active (Limit: ${returnable_count})`, 'info', 'app');
        eventStore.add(`Session Created: ${session_id}`, 'info', 'backend');

        const db = getDb();
        if (db) {
            await db.collection('sessions').doc(session_id).set({
                status: 'return_mode',
                customer_id,
                returnable_count,
                payment_intent_id,
                updatedAt: new Date().toISOString()
            });
        }

        res.json({
            session_id,
            customer_id,
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

