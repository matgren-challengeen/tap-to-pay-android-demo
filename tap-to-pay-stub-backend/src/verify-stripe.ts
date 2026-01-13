import Stripe from 'stripe';
import dotenv from 'dotenv';

dotenv.config();

const key = process.env.STRIPE_SECRET_KEY;
console.log(`Testing Key: ${key?.substring(0, 8)}...`);

if (!key || !key.startsWith('sk_test_')) {
    console.error("WARNING: Key does not start with 'sk_test_'. It might be the wrong key type.");
}

const stripe = new Stripe(key || '', {
    apiVersion: '2024-12-18.acacia' as any,
});

async function verify() {
    try {
        const balance = await stripe.balance.retrieve();
        console.log("SUCCESS: Auth works! Balance available:", balance.available);
    } catch (e: any) {
        console.error("ERROR: Auth failed.", e.message);
        process.exit(1);
    }
}

verify();
