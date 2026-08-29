package com.sakarrobotics.c40agent.api.mqtt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A fixed-capacity, drop-oldest queue (Phase 3 Part 12: "if offline
 * queueing is implemented, enforce bounded storage - do not create an
 * unbounded telemetry queue"). Used to hold outgoing publishes attempted
 * while the MQTT client is disconnected, flushed in order on reconnect.
 * Not thread-safe by itself - {@code AgentMqttClient} is responsible for
 * synchronizing access.
 */
final class BoundedOfflineQueue<T> {

    private final int capacity;
    private final ArrayDeque<T> items = new ArrayDeque<>();
    private long droppedCount;

    BoundedOfflineQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    void offer(T item) {
        if (items.size() >= capacity) {
            items.pollFirst(); // drop the oldest, not the new one - most-recent state matters more
            droppedCount++;
        }
        items.addLast(item);
    }

    List<T> drainAll() {
        List<T> drained = new ArrayList<>(items);
        items.clear();
        return drained;
    }

    int size() {
        return items.size();
    }

    long droppedCount() {
        return droppedCount;
    }
}
