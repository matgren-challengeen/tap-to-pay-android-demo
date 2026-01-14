import { Router } from 'express';

const router = Router();
// Simple in-memory storage for mock carts (reset on restart is fine for testing)
// Map<cart_id, CartObject>
export const mockCarts = new Map<string, any>();

// GET /store/carts/:id
router.get('/:id', (req, res) => {
    const { id } = req.params;
    console.log(`[STUB] GET /store/carts/${id}`);

    const cart = mockCarts.get(id);

    if (cart) {
        res.json({ cart });
    } else {
        res.status(404).json({ message: "Cart not found" });
    }
});

export default router;
