import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

import authRoutes from './routes/auth';
import simulationRoutes from './routes/simulation';
import cartRoutes from './routes/carts';
import { stripe } from './stripe';

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true })); // Parse form data from Android app
app.use(express.static('public'));

app.use('/store/auth', authRoutes);
app.use('/simulate', simulationRoutes);
app.use('/store/carts', cartRoutes);

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

app.get('/', (req, res) => {
    res.send('Tap to Pay Stub Backend is running!');
});

app.listen(port, '0.0.0.0', () => {
    console.log(`Stub Backend running on http://0.0.0.0:${port}`);
});
