package com.sakarrobotics.cloud.robot.registry;

import java.time.Year;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakarrobotics.cloud.common.error.ApiException;
import com.sakarrobotics.cloud.common.error.SakarErrorCode;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

/**
 * Generates the Sakar-owned robot serial number — {@code SR-<PREFIX>-YYYY-NNNNNN} (e.g. {@code
 * SR-CB-2026-000001} for a C40 S) — for any newly registered robot, regardless of which
 * vendor/adapter it came from. This is Sakar's own identity, deliberately independent of any
 * vendor field (a Keenon {@code robotId}/{@code mftCode}, the robot's display name, or the Sakar
 * row's own UUID) — see {@code KeenonRobotSyncService}'s Javadoc for the full identity-model
 * rationale.
 *
 * <p><strong>Prefix is per robot-model "family," not global:</strong> {@code PREFIX} comes from
 * {@link RobotModel#getSerialPrefix()} — Sakar-owned metadata (e.g. C40 S → {@code CB}, W3 →
 * {@code BT}, S100 → {@code PL}), never derived from the vendor's own model name or invented
 * here. A model with no configured prefix throws {@link
 * SakarErrorCode#UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX} rather than falling back to any default
 * — see {@link #nextSerialNumber(RobotModel)}.
 *
 * <p><strong>Concurrency safety:</strong> each prefix owns its own native database sequence
 * ({@code sakar_robot_serial_seq_<prefix>}, e.g. {@code sakar_robot_serial_seq_cb}) — {@code
 * nextval()} is atomic and non-transactional at the database engine level, so two concurrent
 * registrations (even of the same model) can never observe or claim the same number, and one
 * model family's numbering never contends with or depends on another's. This is deliberately NOT
 * a {@code SELECT max(serial_number) + 1}-style read-then-write, which is a classic race under
 * concurrent inserts. A prefix's sequence is created lazily (`CREATE SEQUENCE IF NOT EXISTS`) the
 * first time that prefix is used, since future prefixes are not known in advance — this replaces
 * the single global {@code sakar_robot_serial_seq} the Seventeenth pass introduced, which is left
 * in place unused (harmless) rather than dropped.
 *
 * <p><strong>Year behavior (unchanged since the Seventeenth pass):</strong> {@code YYYY} is
 * always the CURRENT calendar year at the moment of generation (the "assignment year"), but
 * {@code NNNNNN} is one single monotonic sequence PER PREFIX that is never reset at a year
 * boundary — e.g. {@code SR-CB-2026-000001}, {@code SR-CB-2026-000002}, then {@code
 * SR-CB-2027-000003} the following year, independently of whatever {@code SR-BT-...}/{@code
 * SR-PL-...} are doing. No requirement calls for a yearly reset, so none was invented.
 */
@Service
@RequiredArgsConstructor
public class SakarSerialNumberService {

    private static final String SEQUENCE_NAME_PREFIX = "sakar_robot_serial_seq_";
    private static final Pattern VALID_PREFIX = Pattern.compile("^[A-Z]{2,6}$");

    private final EntityManager entityManager;

    /**
     * Generates the next serial number for a robot of the given model. Throws {@link
     * SakarErrorCode#UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX} — never a malformed serial, never a
     * fallback to some default prefix, never the vendor's own model name — if the model has no
     * configured {@link RobotModel#getSerialPrefix()}.
     */
    @Transactional
    public String nextSerialNumber(RobotModel robotModel) {
        String prefix = robotModel.getSerialPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new ApiException(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX,
                    "Robot model '" + robotModel.getName() + "' has no configured Sakar serial prefix");
        }
        if (!VALID_PREFIX.matcher(prefix).matches()) {
            // Defensive: also guards the sequence-name interpolation below, since a sequence
            // name cannot be passed as a bind parameter.
            throw new ApiException(SakarErrorCode.UNSUPPORTED_ROBOT_MODEL_SERIAL_PREFIX,
                    "Robot model '" + robotModel.getName() + "' has an invalid Sakar serial prefix: " + prefix);
        }

        String sequenceName = SEQUENCE_NAME_PREFIX + prefix.toLowerCase(Locale.ROOT);
        entityManager.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS " + sequenceName + " START WITH 1 INCREMENT BY 1")
                .executeUpdate();
        Number sequenceValue = (Number) entityManager
                .createNativeQuery("SELECT nextval('" + sequenceName + "')")
                .getSingleResult();
        return "SR-%s-%d-%06d".formatted(prefix, Year.now().getValue(), sequenceValue.longValue());
    }
}
