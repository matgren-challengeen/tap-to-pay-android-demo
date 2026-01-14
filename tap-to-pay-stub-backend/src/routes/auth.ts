import { Router } from 'express';
import { getDb } from '../firebase';
import { stripe } from '../stripe';
import { eventStore } from '../data/eventStore';

const router = Router();

// POST /store/auth/prepare-setup
router.post('/prepare-setup', async (req, res) => {
    try {
        console.log('Received /store/auth/prepare-setup request');

        // Create a SetupIntent to collect a PaymentMethod
        const setupIntent = await stripe.setupIntents.create({
            usage: 'off_session',
            payment_method_types: ['card'],
        });

        console.log(`Created SetupIntent: ${setupIntent.id}`);

        res.json({
            secret: setupIntent.client_secret,
            id: setupIntent.id
        });
    } catch (err: any) {
        console.error('Error creating SetupIntent:', err);
        res.status(500).json({ error: err.message });
    }
});

// POST /store/auth/login-return
router.post('/login-return', async (req, res) => {
    try {
        const { payment_method_id } = req.body;
        console.log(`Received /store/auth/login-return request (PM: ${payment_method_id})`);

        // Mock Session
        const session_id = 'stub_session_return_' + Date.now();
        const customer_id = 'cust_stub_returner';

        // Mock History: 2 jars to return
        const returnable_count = 2;

        // Log Event for Dashboard
        eventStore.add(`Return Mode Active (Limit: ${returnable_count}) - User Authenticated`, 'info', 'app');
        eventStore.add(`Transaction Open: ${session_id}`, 'info', 'backend');

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

// POST /store/auth/login-by-card  
router.post('/login-by-card', async (req, res) => {
    try {
        const { payment_method_id } = req.body;
        console.log(`Received /store/auth/login-by-card with PM: ${payment_method_id}`);

        eventStore.add(`Card Identified: ${payment_method_id}`, 'info', 'app');

        const session_id = 'stub_session_' + Date.now();
        const customer_id = 'cust_stub';

        eventStore.add(`User Identified: ${customer_id} (Guest)`, 'success', 'backend');
        eventStore.add(`Transaction Open: ${session_id}`, 'info', 'backend');

        // For stub, return a mock session
        res.json({
            session_id,
            customer_id
        });
    } catch (err: any) {
        console.error('Error in login-by-card:', err);
        res.status(500).json({ error: err.message });
    }
});

export default router;
