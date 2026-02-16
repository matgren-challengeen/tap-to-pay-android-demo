import { eventStore } from './eventStore';

export interface OrderItem {
    variant_id: 'small_jar' | 'big_jar';
    quantity: number;
    returned_quantity: number;
}

export interface Order {
    id: string;
    customer_id: string;
    items: OrderItem[];
    created_at: string;
}

class OrderStore {
    // customer_id -> Array of Orders
    private orders: Map<string, Order[]> = new Map();

    addOrder(customer_id: string, items: Array<{ id: string, quantity: number }>) {
        const orderItems: OrderItem[] = items
            .filter(item => item.id === '3' || item.id === '4') // Only jars
            .map(item => ({
                variant_id: item.id === '3' ? 'small_jar' : 'big_jar',
                quantity: item.quantity,
                returned_quantity: 0
            }));

        if (orderItems.length === 0) return;

        const order: Order = {
            id: `order_${Math.random().toString(36).substr(2, 9)}`,
            customer_id,
            items: orderItems,
            created_at: new Date().toISOString()
        };

        const customerOrders = this.orders.get(customer_id) || [];
        customerOrders.push(order);
        this.orders.set(customer_id, customerOrders);

        console.log(`[STUB] Added order ${order.id} for customer ${customer_id} with ${orderItems.length} jar items`);
        eventStore.add(`Recorded order ${order.id} for customer ${customer_id}`, 'success', 'backend');
    }

    getReturnableCount(customer_id: string): number {
        const customerOrders = this.orders.get(customer_id) || [];
        return customerOrders.reduce((total, order) => {
            return total + order.items.reduce((sum, item) => sum + (item.quantity - item.returned_quantity), 0);
        }, 0);
    }

    getReturnableForVariant(customer_id: string, variant_id: 'small_jar' | 'big_jar'): number {
        const customerOrders = this.orders.get(customer_id) || [];
        return customerOrders.reduce((total, order) => {
            const item = order.items.find(i => i.variant_id === variant_id);
            return total + (item ? (item.quantity - item.returned_quantity) : 0);
        }, 0);
    }

    consumeReturn(customer_id: string, variant_id: 'small_jar' | 'big_jar', qty: number): { eligible: number, foreign: number } {
        const customerOrders = this.orders.get(customer_id) || [];
        let remainingToReturn = qty;
        let eligibleCount = 0;

        // FIFO: Consume from oldest orders first
        for (const order of customerOrders) {
            const item = order.items.find(i => i.variant_id === variant_id);
            if (item) {
                const availableInOrder = item.quantity - item.returned_quantity;
                const canConsume = Math.min(availableInOrder, remainingToReturn);

                if (canConsume > 0) {
                    item.returned_quantity += canConsume;
                    eligibleCount += canConsume;
                    remainingToReturn -= canConsume;
                    eventStore.add(`Consumed ${canConsume}x ${variant_id} from order ${order.id}`, 'info', 'backend');
                }
            }
            if (remainingToReturn <= 0) break;
        }

        const foreignCount = remainingToReturn;
        return { eligible: eligibleCount, foreign: foreignCount };
    }
}

export const orderStore = new OrderStore();
