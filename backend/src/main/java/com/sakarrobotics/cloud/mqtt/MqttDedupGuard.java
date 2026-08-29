package com.sakarrobotics.cloud.mqtt;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Registers an inbound envelope's {@code (robotId, messageId)} exactly
 * once, in its own transaction (Phase 3 Part 9 idempotency). {@code
 * REQUIRES_NEW} is deliberate: a unique-constraint violation here must
 * roll back only this tiny insert, never the caller's own transaction —
 * the same reason a plain try/catch inside one shared transaction would be
 * wrong (a failed statement poisons the rest of that transaction on
 * Postgres). This is a separate Spring bean rather than a private method
 * on {@link MqttInboundMessageService} specifically so the
 * {@code REQUIRES_NEW} proxy advice actually applies (Spring AOP does not
 * intercept self-invocation).
 */
@Component
@RequiredArgsConstructor
class MqttDedupGuard {

    private static final Logger log = LoggerFactory.getLogger(MqttDedupGuard.class);

    private final MqttInboundMessageRepository repository;

    /**
     * @return {@code true} if this is the first time this (robot, messageId)
     * pair has been seen.
     *
     * <p>Checks for an existing row before inserting — not merely for
     * efficiency: once a JPA flush throws (e.g. on the unique-constraint
     * violation a bare insert-and-catch would rely on), the JPA provider
     * marks that persistence context / transaction unusable regardless of
     * whether the application catches the translated exception, so a plain
     * try/insert/catch here would make this {@code REQUIRES_NEW}
     * transaction fail to commit with {@code UnexpectedRollbackException}
     * even on a "handled" duplicate. The upfront check avoids ever
     * attempting the doomed flush on the (overwhelmingly common,
     * single-threaded-broker-callback) sequential-redelivery path; the
     * insert's unique constraint remains as a last-resort safety net for a
     * genuine concurrent race, which is why the insert is still wrapped
     * defensively — see the caller, {@link MqttInboundMessageService},
     * which treats any exception from this method as "not first delivery".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerIfFirstDelivery(UUID robotId, String messageId, MqttMessageType type, long sequence) {
        if (repository.findByRobotIdAndMessageId(robotId, messageId).isPresent()) {
            log.debug("Duplicate MQTT message ignored: robot={} messageId={}", robotId, messageId);
            return false;
        }
        MqttInboundMessage message = new MqttInboundMessage();
        message.setRobotId(robotId);
        message.setMessageId(messageId);
        message.setMessageType(type);
        message.setSequence(sequence);
        try {
            repository.saveAndFlush(message);
            return true;
        } catch (DataIntegrityViolationException raceLostToAConcurrentDelivery) {
            log.debug("Duplicate MQTT message ignored (concurrent race): robot={} messageId={}", robotId, messageId);
            return false;
        }
    }
}
