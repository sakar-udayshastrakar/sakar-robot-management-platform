# Sakar Robot Management Platform — Naming and Multi-Robot-Model Strategy

**Status:** documentation-only. Defines terminology and the intended future architecture. No code was renamed and no new abstraction was implemented as part of producing this document — see [Migration Notes](#9-migration-notes-this-pass) for exactly what was and was not changed.

---

## 1. Product: Sakar CleanBot 5000 Plus

**Sakar CleanBot 5000 Plus** is Sakar Robotics' first robot product — a cleaning robot initially built on the Keenon C40 / C40 S hardware platform. "C40" is not a product name; it is the name of the underlying OEM reference hardware used to validate the first implementation. Marketing, product, and customer-facing material must always refer to the product as **Sakar CleanBot 5000 Plus**, never as "C40," "the C40 robot," or "Sakar C40."

| Correct | Incorrect |
|---|---|
| "Sakar CleanBot 5000 Plus is initially based on the Keenon C40 hardware platform." | "Sakar C40 is our robot." |
| "The Sakar Robot Agent running on Sakar CleanBot 5000 Plus talks to the Peanut SDK." | "The C40 Agent talks to the Peanut SDK." |
| "Physical C40 validation" (when the claim is specifically about the underlying hardware) | "Sakar C40 platform" |

## 2. Platform: Sakar Robot Management Platform

The **Sakar Robot Management Platform** is the multi-robot system of record described across `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md`, `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md`, `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md`, and the other companion documents. **Sakar CleanBot 5000 Plus is the first target product, while the Sakar Robot Management Platform is designed as a multi-robot platform** — the platform's data model, backend services, and APIs must not hardcode assumptions that only hold for one robot model or one vendor SDK.

## 3. Generic Agent Concept: Sakar Robot Agent

**Sakar Robot Agent** is the generic name for the robot-resident Android application role: the software that runs on a robot's onboard tablet/computer, bridges the robot's local vendor SDK to the Sakar Backend, and enforces local command validation before any actuation. It is a *role*, not a single codebase — every robot model onboarded to the platform gets its own concrete implementation of this role.

**Current concrete implementation:** `SakarC40Agent` (`robot/SakarC40Agent/`) is the Sakar Robot Agent implementation for Sakar CleanBot 5000 Plus, built against the Keenon C40 / C40 S hardware via the Peanut SDK. It is referred to by its concrete name wherever a document describes *this specific codebase's* current state (build results, module graph, security audit findings, compatibility report), and by the generic name "Sakar Robot Agent" wherever a document describes the platform's *general* architecture or requirements that must hold regardless of which robot model is attached.

**Naming consistency confirmed by live evidence:** the newly supplied `SAKAR_KEENON_C40S_LIVE_API_TESTING_REFERENCE.pdf` (`SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 40) independently records the tested robot's Sakar product name as "Sakar CleanBot 5000 Plus" against Keenon model "C40 S" — consistent with this naming strategy without any correction needed.

## 4. Initial Hardware Reference: Keenon C40 / C40 S

Keenon C40 / C40 S is the OEM reference hardware used to validate the first Sakar Robot Agent implementation, accessed through Keenon's **Peanut SDK**. It is a legitimate and necessary technical term wherever a document discusses:
- the physical robot chassis or its OEM-provided capabilities,
- Peanut SDK API/version compatibility,
- hardware validation status (e.g. `REQUIRES PHYSICAL C40 TEST`),
- vendor-specific integration details (link types, SDK constants, Keenon Cloud REST API).

These references must **not** be globally erased or replaced — see §7.

## 5. Future Robot Models

The platform must support additional robot models beyond Sakar CleanBot 5000 Plus / Keenon C40, both:
- **Future Sakar Robots** — additional Sakar-branded products, potentially still on Keenon hardware or on a different OEM chassis, and
- **Future OEM Robots** — robots from other vendors entirely, with their own SDKs.

```
Sakar Robot Management Platform
        |
        +-- Sakar CleanBot 5000 Plus
        |       |
        |       +-- Sakar Robot Agent
        |               |
        |               +-- Robot Adapter (future — see §6)
        |                       |
        |                       +-- Keenon / Peanut SDK
        |
        +-- Future Sakar Robots
        |
        +-- Future OEM Robots
```

The platform must not assume every future robot is a Keenon robot, or that every future agent speaks the Peanut SDK. This is already partially reflected in the existing requirements/architecture documents via the `robot_models` database abstraction (`SAKAR_ROBOT_PLATFORM_DATABASE.md` §6) and the "Multi-Robot-Model Extensibility" section of `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §6.

## 6. Robot Adapter Concept (future architecture, not implemented)

The long-term Sakar Robot Agent architecture should support a **Robot Adapter** abstraction so that vendor-SDK integration is isolated behind a common interface:

```
Sakar Robot Agent
        |
        +-- Robot Adapter
                |
                +-- KeenonAdapter
                |
                +-- SakarAdapter
                |
                +-- FutureOEMAdapter
```

For the current implementation:

```
KeenonAdapter
    |
    +-- Peanut SDK
    |
    +-- Keenon C40 / C40 S hardware
```

**This adapter abstraction does not exist in the current `SakarC40Agent` codebase and is not being introduced by this migration.** Today, `PeanutSdkBridge` (`sdk/`) is the sole chokepoint for Peanut SDK calls and `C40RobotController` (`robot/`) is the sole safety gate — this is functionally similar to what a `KeenonAdapter` would look like, but it is not expressed as a pluggable interface with room for sibling adapters. Introducing a formal `RobotAdapter` interface is future work, scoped to whichever phase first onboards a second robot model or vendor SDK (see `SAKAR_ROBOT_PLATFORM_ROADMAP.md`); it should not be done speculatively ahead of that need.

## 7. Product vs. Hardware Naming Rules

| Rule | Applies to |
|---|---|
| Use **Sakar CleanBot 5000 Plus** for the product, in customer-facing, marketing, product-vision, and requirements-summary contexts. | Product Vision sections, executive summaries, README project-purpose statements |
| Use **Sakar Robot Agent** for the generic agent role in architecture/requirements prose, diagrams, and section titles that describe platform-wide, model-agnostic behavior. | Architecture diagrams, data-flow descriptions, cross-cutting security control tables, roadmap phase titles |
| Use **`SakarC40Agent`** (the concrete name) wherever a document is about *this specific codebase* — its current build state, its actual class/package names, its own source-read findings, or a test scenario that literally exercises it. | Phase 0 documents, compatibility reports, Gherkin acceptance scenarios, "existing code" qualifiers |
| Use **Keenon C40 / C40 S** for the OEM hardware, SDK compatibility, and physical validation status. | Hardware validation tables, SDK study cross-references, `REQUIRES PHYSICAL C40 TEST` rows |
| Never write **"Sakar C40"** as a product or platform name — it conflates the Sakar brand with a specific OEM hardware model. | Everywhere |

## 8. Technical Terminology Rules (status vocabulary — unchanged)

The following status labels are load-bearing test/validation semantics, not product naming, and must always be preserved verbatim wherever they appear: `CONFIRMED`, `LIKELY`, `UNKNOWN`, `REQUIRES PHYSICAL C40 TEST`, `REQUIRES NETWORK TEST`, `REQUIRES VENDOR SUPPORT`, `PLANNED`, `REQUIREMENT`, `DESIGN COMPLETE`, `IMPLEMENTATION PENDING`, `CONFIRMED FALSE`. `REQUIRES PHYSICAL C40 TEST` in particular stays exactly as-is — it correctly names the underlying hardware being tested and must not be renamed to something implying it tests "the product" in the abstract.

### 8.1 Generic robot metadata (future requirement, not implemented)

The platform requirements should eventually support generic per-robot/per-model metadata so a robot's product identity and its underlying hardware are both recorded without conflating the two. None of the following fields exist in the current `robot_models`/`robots` schema (`SAKAR_ROBOT_PLATFORM_DATABASE.md` §6-7) today; they are documented here as a future requirement for whoever extends that schema when a second robot model is onboarded:

| Field | Example |
|---|---|
| `robot_id` | Sakar-internal UUID |
| `robot_serial_number` | OEM serial / `mftCode` |
| `robot_model` | `CleanBot 5000 Plus` |
| `robot_manufacturer` | `Sakar Robotics` |
| `robot_product_name` | `Sakar CleanBot 5000 Plus` |
| `robot_agent_version` | Sakar Robot Agent build version |
| `robot_firmware_version` | OEM firmware version |
| `robot_protocol` | e.g. `Peanut SDK` |
| `robot_adapter` | e.g. `KeenonAdapter` (see §6) |
| `robot_capabilities` | JSON capability flags (already partially present as `robot_models.capabilities`) |

## 9. Migration Notes (this pass)

**Files renamed:** none. `robot/SakarC40Agent/` was **not** renamed to `robot/SakarRobotAgent/` — see §9.1.

**Source code changes:** one non-functional string resource — `robot/SakarC40Agent/ui/src/main/res/values/strings.xml`: the Android app's user-visible display name (`app_name`) changed from `"Sakar C40 Agent"` to `"Sakar Robot Agent"`. No package name, class name, module name, build file, or API surface was touched.

**Documentation changes:** terminology updated across `README.md`, `robot/README.md`, `robot/SakarC40Agent/README.md`, `docs/README.md`, and all files under `docs/requirements/`, `docs/security/`, `docs/architecture/`, `docs/api/` — replacing the concrete `SakarC40Agent`/`SAKAR C40 AGENT` name with the generic **Sakar Robot Agent** wherever a passage described the platform's general architecture or a diagram's generic robot-tablet layer, while leaving every reference that is explicitly about the current codebase, a hardware validation status, or an OEM/SDK technical detail untouched. Two genuine product-naming defects — `"SAKAR C40 AGENT"` appearing as a diagram stage label in `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` and `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` — were corrected to `"SAKAR ROBOT AGENT"`.

### 9.1 Rename NOT performed: `robot/SakarC40Agent/` → `robot/SakarRobotAgent/`

A directory/module rename was evaluated and **intentionally not performed** in this pass. Reasons:

- The directory name is load-bearing across the entire Gradle build: `settings.gradle`'s `rootProject.name`, every module's Android `namespace`/`applicationId` (`com.sakarrobotics.c40agent.*`), the Android manifest's `android:name=".SakarC40Application"`, and 19 Java files' `package` statements would all need to change together, atomically, for the build to keep compiling.
- The task's explicit constraint is that this is a terminology/architecture migration, not a functional or structural change, and any rename must be "safe and mechanically verifiable" — a full package/namespace rename is a substantial, high-blast-radius change better scoped as its own dedicated task with its own build verification pass, not bundled into a documentation migration.
- Renaming only the directory while leaving the Gradle `namespace`/`applicationId`/package statements as `com.sakarrobotics.c40agent` would leave the code and its container inconsistently named — worse than the current state.

**Recommended follow-up (separate task):** if/when the module is renamed, do it as a single atomic change covering: directory rename, `settings.gradle` root project name, every module's `namespace`/`applicationId`, the `AndroidManifest.xml` application `android:name`, every Java file's `package` statement and corresponding directory path, and a full clean rebuild to confirm nothing broke. Java class names that encode "C40" (`C40RobotController`, `C40RobotControllerHolder`, `SakarC40Application`) were similarly left untouched for the same reason — they are current, accurate names for hardware-specific implementation classes, and renaming them is a code change, not a documentation change, out of scope here.
