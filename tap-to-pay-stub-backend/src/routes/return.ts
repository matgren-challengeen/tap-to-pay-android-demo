import { Router } from 'express';
import { orderStore } from '../data/orders';
import { sessions } from './auth';

const router = Router();

// GET /store/return/deposits-variants
// Mock the Medusa API used by the storefront to list returnable packaging
router.get('/deposits-variants', (req, res) => {
    try {
        const authHeader = req.headers.authorization;
        if (!authHeader) {
            return res.status(401).json({ error: 'Unauthorized' });
        }

        // Mock token decode (customer_id is in the base64 part)
        const token = authHeader.replace('Bearer ', '');
        const payloadStr = Buffer.from(token.replace('mock_jwt_', ''), 'base64').toString();
        const payload = JSON.parse(payloadStr);
        const customer_id = payload.customer_id;

        if (!customer_id) {
            return res.status(400).json({ error: 'Invalid token' });
        }

        console.log(`[STUB] Fetching deposit variants for customer: ${customer_id}`);

        // Get actual counts from OrderStore
        const smallJarCount = orderStore.getReturnableForVariant(customer_id, 'small_jar');
        const bigJarCount = orderStore.getReturnableForVariant(customer_id, 'big_jar');

        // Create individual items as expected by the storefront (one per returnable unit)
        const deposit_variants = [];

        // Add Small Jars
        for (let i = 0; i < smallJarCount; i++) {
            deposit_variants.push({
                id: `dep_small_${customer_id}_${i}`,
                product: {
                    title: 'Soup (Small Jar)',
                    thumbnail: 'https://placehold.co/400x400?text=Small+Jar'
                },
                product_variant: {
                    id: 'variant_small_jar',
                    title: 'Small Jar',
                    options: [{ value: '0.5' }], // Mock weight/deposit
                    prices: [{ amount: 50, currency_code: 'usd' }]
                }
            });
        }

        // Add Big Jars
        for (let i = 0; i < bigJarCount; i++) {
            deposit_variants.push({
                id: `dep_big_${customer_id}_${i}`,
                product: {
                    title: 'Soup (Big Jar)',
                    thumbnail: 'https://placehold.co/400x400?text=Big+Jar'
                },
                product_variant: {
                    id: 'variant_big_jar',
                    title: 'Big Jar',
                    options: [{ value: '1.0' }],
                    prices: [{ amount: 100, currency_code: 'usd' }]
                }
            });
        }

        res.json({
            deposit_variants,
            count: deposit_variants.length
        });

    } catch (err: any) {
        console.error('Error in deposits-variants:', err);
        res.status(500).json({ error: err.message });
    }
});

export default router;
