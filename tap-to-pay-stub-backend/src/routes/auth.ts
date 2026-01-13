import { Router } from 'express';
import { stripe } from '../stripe';

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
            client_secret: setupIntent.client_secret,
            id: setupIntent.id
        });
    } catch (err: any) {
        console.error('Error creating SetupIntent:', err);
        res.status(500).json({ error: err.message });
    }
});

export default router;
