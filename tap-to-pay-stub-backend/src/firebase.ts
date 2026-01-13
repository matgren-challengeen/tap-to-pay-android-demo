import * as admin from 'firebase-admin';
import dotenv from 'dotenv';

dotenv.config();

// We will need a service account key file path or JSON content
// For now, we'll assume it's provided via GOOGLE_APPLICATION_CREDENTIALS or similar env var
// OR we can mock it for now if we just want to run the server without crashing

let db: admin.firestore.Firestore | null = null;

try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
        const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        db = admin.firestore();
        console.log('Firebase initialized successfully from ENV');
    } else {
        // Try to load from default path if exists
        try {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const serviceAccount = require('../service-account.json');
            admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
            db = admin.firestore();
            console.log('Firebase initialized successfully from service-account.json');
        } catch (e) {
            console.warn('No service account provided. Firestore is disabled.', e);
        }
    }
} catch (error) {
    console.error('Error initializing Firebase:', error);
}

export const getDb = () => db;
