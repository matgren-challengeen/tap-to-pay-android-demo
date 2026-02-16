import { eventStore } from './eventStore';

export interface CustomerData {
    customer_id: string;
    fingerprint: string;
    email?: string;
    purchases: {
        small_jar: number;
        big_jar: number;
    };
}

class CustomerStore {
    private customers: Map<string, CustomerData> = new Map();

    getOrCreate(fingerprint: string): CustomerData {
        if (this.customers.has(fingerprint)) {
            return this.customers.get(fingerprint)!;
        }

        const newCustomer: CustomerData = {
            customer_id: `cust_${Math.random().toString(36).substr(2, 9)}`,
            fingerprint,
            purchases: {
                small_jar: 0,
                big_jar: 0
            }
        };

        this.customers.set(fingerprint, newCustomer);
        eventStore.add(`Created new customer profile for fingerprint: ${fingerprint}`, 'info', 'backend');
        return newCustomer;
    }
}

export const customerStore = new CustomerStore();
