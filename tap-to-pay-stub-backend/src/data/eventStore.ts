export interface SimulationEvent {
    id: string;
    timestamp: number; // unix ms
    message: string;
    type: 'info' | 'success' | 'warning' | 'error';
    source: 'app' | 'backend' | 'hardware';
}

class EventStore {
    private events: SimulationEvent[] = [];

    add(message: string, type: 'info' | 'success' | 'warning' | 'error' = 'info', source: 'backend') {
        const event: SimulationEvent = {
            id: Math.random().toString(36).substring(7),
            timestamp: Date.now(),
            message,
            type,
            source
        };
        this.events.push(event);
        // Keep last 100 events
        if (this.events.length > 100) this.events.shift();
        return event;
    }

    getSince(timestamp: number) {
        return this.events.filter(e => e.timestamp > timestamp);
    }

    clear() {
        this.events = [];
    }

    getAll() {
        return [...this.events];
    }
}

export const eventStore = new EventStore();
