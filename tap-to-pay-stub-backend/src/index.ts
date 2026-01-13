import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

import authRoutes from './routes/auth';
import simulationRoutes from './routes/simulation';

app.use(cors());
app.use(express.json());

app.use('/store/auth', authRoutes);
app.use('/simulate', simulationRoutes);

app.get('/', (req, res) => {
    res.send('Tap to Pay Stub Backend is running!');
});

app.listen(port, () => {
    console.log(`Stub Backend running on http://localhost:${port}`);
});
