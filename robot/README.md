# robot/

Contains `SakarC40Agent` — the current concrete implementation of the generic **Sakar Robot Agent** role, the robot-resident Android application that bridges the Peanut SDK (local, on-robot) to Sakar Cloud (remote). It targets the first product, **Sakar CleanBot 5000 Plus**, built on the Keenon C40 / C40 S hardware platform. See [`docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](../docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md) for naming conventions and future multi-robot-model plans.

## SakarC40Agent/

A **copy** of the existing, already-built `SakarC40Agent` project (originally at `D:\Sakar Robotics Projects\SakarC40Agent`), brought under this workspace root per the project-location rule. What was copied and what was deliberately excluded:

| Included | Excluded (regenerable or machine-specific — matches the project's own `.gitignore`) |
|---|---|
| All 9 module source trees (`api`, `app`, `charging`, `diagnostics`, `logging`, `navigation`, `robot`, `sdk`, `telemetry`, `ui`) | Every `build/` directory (10 total) |
| All `build.gradle` / `settings.gradle` / `gradle.properties` files | `.gradle/` (Gradle cache) |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | `local.properties` (machine-specific SDK path) |
| `secrets.properties.example` (placeholder only — no real secret) | `.idea/`, `*.iml` (IDE state) |
| `sdk/libs/peanut-sdk-release.aar` (the vendored SDK binary this project depends on — **kept in the filesystem copy because the build requires it, but excluded from Git tracking, see `.gitignore`**) | `captures/`, `.externalNativeBuild/`, `.cxx/` |
| `README.md`, `COMPATIBILITY_REPORT.md`, `.gitignore` | — |

**The original project at `D:\Sakar Robotics Projects\SakarC40Agent` was not modified, moved, or deleted.** It remains available as a reference/fallback until this copy is confirmed as the project's sole working copy going forward.

**External vendor SDK note:** the standalone `peanut-sdk-v1.3.0` reference directory (`D:\Sakar Robotics Projects\peanut-sdk-v1.3.0`, containing the decompiled study material and the vendor's sample app) is a separate, external, read-only vendor dependency. It is **not** part of this workspace and was not copied, moved, or modified — it is referenced only by the technical study documents in `docs/`.

**Current phase: DOCUMENTATION / ARCHITECTURE PREPARATION.** No new code has been added to this copy; no feature implementation, no Peanut SDK modification, and no connection to a physical C40 has occurred as part of this workspace-organization task.
