package com.sakarrobotics.c40agent.api.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Phase 3 Part 12: "do not create an unbounded telemetry queue." */
class BoundedOfflineQueueTest {

    @Test
    void neverGrowsPastItsCapacity_dropsTheOldestFirst() {
        BoundedOfflineQueue<Integer> queue = new BoundedOfflineQueue<>(3);

        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
        queue.offer(4); // should drop 1
        queue.offer(5); // should drop 2

        assertEquals(3, queue.size());
        assertEquals(2, queue.droppedCount());
        assertEquals(List.of(3, 4, 5), queue.drainAll());
    }

    @Test
    void drainAll_emptiesTheQueue() {
        BoundedOfflineQueue<String> queue = new BoundedOfflineQueue<>(5);
        queue.offer("a");
        queue.offer("b");

        List<String> drained = queue.drainAll();

        assertEquals(List.of("a", "b"), drained);
        assertEquals(0, queue.size());
        assertTrue(queue.drainAll().isEmpty());
    }

    @Test
    void aCapacityOfZeroOrLess_isTreatedAsOne() {
        BoundedOfflineQueue<Integer> queue = new BoundedOfflineQueue<>(0);
        queue.offer(1);
        queue.offer(2);

        assertEquals(1, queue.size());
        assertEquals(List.of(2), queue.drainAll());
    }
}
