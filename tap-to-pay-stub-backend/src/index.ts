import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const port = parseInt(process.env.PORT || '3000', 10);

import authRoutes from './routes/auth';
import simulationRoutes from './routes/simulation';
import cartRoutes from './routes/carts';
import returnRoutes from './routes/return';
import { stripe } from './stripe';

// In-memory store for active sessions (maps session_id to payment_intent_id)
export const activeSessions = new Map<string, { payment_intent_id: string, customer_id: string }>();

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true })); // Parse form data from Android app
app.use(express.static('public'));

app.use('/store/auth', authRoutes);
app.use('/simulate', simulationRoutes);
app.use('/store/carts', cartRoutes);
app.use('/store/return', returnRoutes);

// POST /connection_token - Required by Stripe Terminal SDK
app.post('/connection_token', async (req, res) => {
    try {
        const connectionToken = await stripe.terminal.connectionTokens.create();
        res.json({ secret: connectionToken.secret });
    } catch (err: any) {
        console.error('Error creating connection token:', err);
        res.status(500).json({ error: err.message });
    }
});

// POST /create_payment_intent - Create pre-auth PaymentIntent for vending
app.post('/create_payment_intent', async (req, res) => {
    try {
        const { amount, currency } = req.body;
        const amountInt = parseInt(amount) || 5000; // Default $50 max pre-auth
        const currencyStr = currency || 'usd';

        console.log(`[STUB] Creating PaymentIntent: ${amountInt} ${currencyStr} (capture_method: manual)`);

        const paymentIntent = await stripe.paymentIntents.create({
            amount: amountInt,
            currency: currencyStr,
            payment_method_types: ['card_present'],
            capture_method: 'manual', // Pre-auth: authorize now, capture later
        });

        console.log(`[STUB] Created PaymentIntent: ${paymentIntent.id}`);

        res.json({
            id: paymentIntent.id,
            secret: paymentIntent.client_secret
        });
    } catch (err: any) {
        console.error('Error creating PaymentIntent:', err);
        res.status(500).json({ error: err.message });
    }
});

// POST /capture_payment_intent - Capture authorized PaymentIntent (called on door close)
app.post('/capture_payment_intent', async (req, res) => {
    try {
        const { payment_intent_id, amount_to_capture } = req.body;

        if (!payment_intent_id) {
            return res.status(400).json({ error: 'payment_intent_id is required' });
        }

        console.log(`[STUB] Capturing PaymentIntent: ${payment_intent_id}, amount: ${amount_to_capture || 'full'}`);

        let result: any;
        if (payment_intent_id.startsWith('emulator_')) {
            console.log(`[STUB] Emulator session: skipping Stripe capture`);
            result = {
                id: payment_intent_id,
                status: 'succeeded',
                amount_received: parseInt(amount_to_capture) || 0
            };
        } else {
            const captureParams: any = {};
            if (amount_to_capture) {
                captureParams.amount_to_capture = parseInt(amount_to_capture);
            }

            const paymentIntent = await stripe.paymentIntents.capture(payment_intent_id, captureParams);
            result = {
                id: paymentIntent.id,
                status: paymentIntent.status,
                amount_received: paymentIntent.amount_received
            };
        }

        console.log(`[STUB] Captured Result: ${result.id}, status: ${result.status}`);

        res.json(result);
    } catch (err: any) {
        console.error('Error capturing PaymentIntent:', err);
        res.status(500).json({ error: err.message });
    }
});

// POST /cancel_payment_intent - Cancel authorized PaymentIntent (if cart is empty)
app.post('/cancel_payment_intent', async (req, res) => {
    try {
        const { payment_intent_id } = req.body;

        if (!payment_intent_id) {
            return res.status(400).json({ error: 'payment_intent_id is required' });
        }

        console.log(`[STUB] Canceling PaymentIntent: ${payment_intent_id}`);

        let result: any;
        if (payment_intent_id.startsWith('emulator_')) {
            console.log(`[STUB] Emulator session: skipping Stripe cancel`);
            result = {
                id: payment_intent_id,
                status: 'canceled'
            };
        } else {
            const paymentIntent = await stripe.paymentIntents.cancel(payment_intent_id);
            result = {
                id: paymentIntent.id,
                status: paymentIntent.status
            };
        }

        console.log(`[STUB] Canceled Result: ${result.id}, status: ${result.status}`);

        res.json(result);
    } catch (err: any) {
        console.error('Error canceling PaymentIntent:', err);
        res.status(500).json({ error: err.message });
    }
});

app.get('/', (req, res) => {
    res.send('Tap to Pay Stub Backend is running!');
});

app.listen(port, '0.0.0.0', () => {
    console.log(`Stub Backend running on http://0.0.0.0:${port}`);
});
