import Stripe from 'stripe';

const stripeSecretKey = process.env.STRIPE_SECRET_KEY || '';
if (!stripeSecretKey) {
    console.warn('STRIPE_SECRET_KEY is missing in .env');
}

export const stripe = new Stripe(stripeSecretKey, {
    apiVersion: '2024-12-18.acacia' as any, // Cast to any to avoid strict version mismatch issues during dev
});
