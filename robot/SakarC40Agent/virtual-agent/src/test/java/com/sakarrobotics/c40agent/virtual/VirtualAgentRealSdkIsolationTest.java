package com.sakarrobotics.c40agent.virtual;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Automated architectural-fitness test (Roadmap Phase 9 §16 "Real SDK
 * isolation test", see ../../VIRTUAL_C40_SIMULATOR.md). Proves — by
 * reading the actual {@code .java} source of this entire module, not by
 * trusting a Javadoc claim — that {@code :virtual-agent}:
 * <ul>
 *   <li>never imports or references {@code com.keenon.*} (the real Peanut SDK);</li>
 *   <li>never references {@code PeanutSdkBridge}/{@code PeanutSDK}/{@code C40RobotController};</li>
 *   <li>never contains a physical C40's known local network address, CoAP
 *   endpoint, or serial device path (the exact values documented in
 *   {@code C40_S_LS_M014C00_RW_F00_V246_ROS_INTERFACE_ANALYSIS.md});</li>
 *   <li>never references rosbridge or a Keenon Cloud domain.</li>
 * </ul>
 *
 * <p>This is a regression guard, not a one-off manual grep: it fails the
 * build the moment anyone adds a forbidden reference to this module,
 * exactly like Gradle's own dependency graph already prevents this module
 * from resolving {@code com.keenon.*} classes at compile time (this
 * module's {@code build.gradle} declares no dependency on {@code :sdk}/
 * {@code :robot}/{@code :navigation}/{@code :charging}) - this test is
 * the second, independent layer described in the simulator's own
 * documentation.
 */
class VirtualAgentRealSdkIsolationTest {

    /**
     * Case-insensitive substrings that must never appear in this module's
     * own CODE (comments are stripped before scanning - see {@link
     * #stripComments}, deliberately, so this module's Javadoc remains free
     * to explain what it does NOT do by naming the real classes/protocols
     * it is distinct from, without that documentation itself tripping this
     * check).
     */
    private static final String[] FORBIDDEN_SUBSTRINGS = {
            "com.keenon",
            "peanutsdk",
            "peanutsdkbridge",
            "c40robotcontroller",
            "coap://",
            "coapcommond",
            "192.168.64.20",
            "192.168.64.10",
            "192.168.64.100",
            "/navigation/dst",
            "/charge/auto",
            "/dev/ttys",
            "/dev/ttyusb",
            "rosbridge",
            "ws://192.168",
            "keenonrobot.com",
            "robotkeenon.com",
            "ikeenon.com",
    };

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void mainSourceTree_containsNoRealSdkOrPhysicalRobotReference() {
        Path sourceRoot = findSourceRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> scan(path, violations));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(violations.isEmpty(), "Found forbidden real-SDK/physical-robot references in :virtual-agent's own "
                + "source (this module must be completely independent of the physical SDK path):\n"
                + String.join("\n", violations));
    }

    @Test
    void findSourceRoot_actuallyFoundRealFiles_soTheAboveTestIsNotVacuouslyPassing() {
        // A safety net for the test above: if the source root resolution ever breaks (e.g. a
        // future change to how/where Gradle runs this test), the isolation test must not silently
        // pass just because it scanned zero files.
        Path sourceRoot = findSourceRoot();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            long javaFileCount = files.filter(path -> path.toString().endsWith(".java")).count();
            assertFalse(javaFileCount == 0, "Expected to find at least one .java file under " + sourceRoot
                    + " - the isolation test above would otherwise pass vacuously.");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void scan(Path file, List<String> violations) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String codeOnly = stripComments(content).toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_SUBSTRINGS) {
            if (codeOnly.contains(forbidden.toLowerCase(Locale.ROOT))) {
                violations.add(file + " contains forbidden reference in actual code (not a comment): \"" + forbidden + "\"");
            }
        }
    }

    private static String stripComments(String javaSource) {
        String withoutBlockComments = BLOCK_COMMENT.matcher(javaSource).replaceAll(" ");
        return LINE_COMMENT.matcher(withoutBlockComments).replaceAll(" ");
    }

    private static Path findSourceRoot() {
        Path candidate = Paths.get("src", "main", "java");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // Fall back to resolving relative to this module's own directory, in case the test JVM's
        // working directory is ever not the module root.
        Path moduleRelative = Paths.get("virtual-agent", "src", "main", "java");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        fail("Could not locate :virtual-agent's src/main/java directory from working directory "
                + Paths.get("").toAbsolutePath() + " - fix findSourceRoot() rather than skip this test.");
        throw new IllegalStateException("unreachable");
    }
}
