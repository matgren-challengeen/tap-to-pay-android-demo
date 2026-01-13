import { Router } from 'express';
import { getDb } from '../firebase';

const router = Router();

// POST /simulate/door-open
// Simulates the door opening event which typically creates a session or updates status
router.post('/door-open', async (req, res) => {
    const { session_id } = req.body;

    console.log(`[SIMULATION] Door Open triggered for session: ${session_id}`);

    const db = getDb();
    if (db && session_id) {
        try {
            await db.collection('sessions').doc(session_id).set({
                status: 'door_open',
                updatedAt: new Date().toISOString()
            }, { merge: true });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id} -> status: door_open`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: 'Door open simulated' });
});

// POST /simulate/item-picked
// Simulates adding an item to the cart
router.post('/item-picked', async (req, res) => {
    const { session_id, item_id, price } = req.body;

    console.log(`[SIMULATION] Item Picked: ${item_id} (${price}) for session: ${session_id}`);

    const db = getDb();
    if (db && session_id) {
        try {
            // Logic to update cart would go here. For now, just logging the event to the session doc
            // In a real app, this would likely update a subcollection "cart_items" or similar
            await db.collection('sessions').doc(session_id).collection('events').add({
                type: 'item_picked',
                item_id,
                price,
                timestamp: new Date().toISOString()
            });
            console.log(`[SIMULATION] Firestore updated: sessions/${session_id}/events`);
        } catch (e) {
            console.error("Error updating firestore", e);
        }
    }

    res.json({ status: 'ok', message: `Item ${item_id} picked` });
});

export default router;
