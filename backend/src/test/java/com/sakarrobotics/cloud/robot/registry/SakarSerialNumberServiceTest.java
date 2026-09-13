package com.sakarrobotics.cloud.robot.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sakarrobotics.cloud.IntegrationTestSupport;
import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

/**
 * Full-Spring-context (real database) tests for {@link SakarSerialNumberService} — the format,
 * per-prefix monotonic-uniqueness, and concurrency-safety guarantees this class provides are only
 * meaningfully proven against a real sequence, not a mock. {@link RobotModel} fixtures here are
 * plain, unpersisted objects — {@link SakarSerialNumberService#nextSerialNumber(RobotModel)}
 * reads {@code name}/{@code serialPrefix} directly off the object it is given, never re-fetching
 * the model from the database.
 */
class SakarSerialNumberServiceTest extends IntegrationTestSupport {

    @Autowired
    private SakarSerialNumberService sakarSerialNumberService;

    private static RobotModel modelWithPrefix(String name, String prefix) {
        RobotModel model = new RobotModel();
        model.setName(name);
        model.setSerialPrefix(prefix);
        return model;
    }

    // 1: C40 S generates the CB prefix
    @Test
    void nextSerialNumber_c40s_generatesCbPrefix() {
        String serial = sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB"));
        assertThat(serial).matches("SR-CB-\\d{4}-\\d{6}");
    }

    // 2: W3 generates the BT prefix
    @Test
    void nextSerialNumber_w3_generatesBtPrefix() {
        String serial = sakarSerialNumberService.nextSerialNumber(modelWithPrefix("W3", "BT"));
        assertThat(serial).matches("SR-BT-\\d{4}-\\d{6}");
    }

    // 3: S100 generates the PL prefix
    @Test
    void nextSerialNumber_s100_generatesPlPrefix() {
        String serial = sakarSerialNumberService.nextSerialNumber(modelWithPrefix("S100", "PL"));
        assertThat(serial).matches("SR-PL-\\d{4}-\\d{6}");
    }

    @Test
    void nextSerialNumber_includesTheCurrentAssignmentYear() {
        String serial = sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB"));
        assertThat(serial).startsWith("SR-CB-" + Year.now().getValue() + "-");
    }

    // 4 + 5: one prefix's sequence never advances because of another prefix's calls
    @Test
    void nextSerialNumber_differentPrefixSequences_areIndependent() {
        long cbBefore = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB")));
        long btBefore = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("W3", "BT")));
        long plBefore = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("S100", "PL")));

        // Advance CB three more times only.
        sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB"));
        sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB"));
        long cbAfter = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB")));

        long btAfter = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("W3", "BT")));
        long plAfter = sequenceNumberOf(sakarSerialNumberService.nextSerialNumber(modelWithPrefix("S100", "PL")));

        assertThat(cbAfter).isEqualTo(cbBefore + 3);
        // BT and PL each advanced by exactly one (this test's own single call), never by the
        // four CB calls that happened in between.
        assertThat(btAfter).isEqualTo(btBefore + 1);
        assertThat(plAfter).isEqualTo(plBefore + 1);
    }

    // 10: concurrent generation for the SAME prefix never duplicates
    @Test
    void nextSerialNumber_concurrentCallsForSamePrefix_neverProduceDuplicates() throws Exception {
        int threadCount = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return sakarSerialNumberService.nextSerialNumber(modelWithPrefix("C40 S", "CB"));
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();

            Set<String> serials = new HashSet<>();
            for (Future<String> future : futures) {
                serials.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(serials).hasSize(threadCount);
            assertThat(serials).allMatch(s -> s.matches("SR-CB-\\d{4}-\\d{6}"));
        } finally {
            pool.shutdownNow();
        }
    }

    // 11: unknown/unconfigured model fails safely — never invents a prefix, never falls back
    @Test
    void nextSerialNumber_modelWithNoConfiguredPrefix_failsSafely() {
        RobotModel unconfigured = modelWithPrefix("Mystery Model X9000", null);

        assertThatThrownBy(() -> sakarSerialNumberService.nextSerialNumber(unconfigured))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX));
    }

    @Test
    void nextSerialNumber_blankPrefix_failsSafely() {
        RobotModel blankPrefix = modelWithPrefix("Some Model", "   ");

        assertThatThrownBy(() -> sakarSerialNumberService.nextSerialNumber(blankPrefix))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX));
    }

    @Test
    void nextSerialNumber_malformedPrefix_neverBuildsAMalformedSerial() {
        // Lowercase / non-letter characters would otherwise flow straight into a raw SQL
        // sequence-name interpolation — must be rejected outright, never sanitized-and-used.
        RobotModel malformed = modelWithPrefix("Some Model", "cb-1; DROP TABLE robots;--");

        assertThatThrownBy(() -> sakarSerialNumberService.nextSerialNumber(malformed))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX));
    }

    private static long sequenceNumberOf(String serial) {
        return Long.parseLong(serial.substring(serial.length() - 6));
    }
}
