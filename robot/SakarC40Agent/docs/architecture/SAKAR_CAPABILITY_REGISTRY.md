# Sakar Capability Registry

Single source of truth for every robot-hardware-facing capability exposed through
`com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway` and its constituent capability
interfaces. See [`SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md`](SAKAR_ROBOT_SOFTWARE_ARCHITECTURE.md) for
the full design rationale. Status vocabulary: `REAL` / `SIMULATED` / `NOT_AVAILABLE` /
`NOT_VERIFIED` (`com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus`, distinct from the
existing UI `Capability` enum - see that document §5 for the mapping between the two).

This table does not duplicate `docs/engineering/02_EVIDENCE_REGISTER.md` - it records the
*software capability's* current status, cross-referencing the evidence that justifies it, not the
raw evidence itself.

| Capability | Owner (module) | Implementation | Status | Source | Hardware dependency | ROS dependency | Physical verification required |
|---|---|---|---|---|---|---|---|
| Robot connection (Peanut SDK link) | `:domain` (`RobotConnectionRepository`) | `RobotConnectionRepositoryImpl` (`:data`) | REAL | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §8 | RK3288 UIB (Android/application computer) | No | No |
| Robot connection (ROS/Robot Computer link) | `:domain` (`RosRobotAdapter`) | `RosRobotAdapterStub` (`:data`) | NOT_VERIFIED | `docs/engineering/03_HARDWARE_ARCHITECTURE.md` §2.1/§6 | Separate ROS/Robot Computer (candidate: "ARM IPC" enclosure, PH-08 - not confirmed) | Yes | Yes - endpoint, protocol, and identity all unconfirmed |
| Drive (manual jog) | `:domain` (`RobotNavigationRepository.jog`) | `RealMotorController` / `SimulatedMotorController` (`:data`, selected by `ManualDriveBackendMode`) | REAL (when `ManualDriveBackendMode.REAL`) / SIMULATED (default) | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §16 | RK3288 -> Peanut SDK `MotorComponent` -> SCM-IoT motor controller (E-016/E-052, unresolved) | No (current path is Peanut SDK, not ROS) | Real backend: physical motor response NOT VERIFIED |
| Navigation (go-to-point/pause/resume/stop) | `:domain` (`RobotNavigationRepository`) | `RobotNavigationRepositoryImpl` (`:data`) | REAL/GATED | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §8 | RK3288 -> Peanut SDK `NavigationComponent` | No | GATED behind `OperatingMode.HARDWARE_TEST`, never auto-enabled |
| Sensors (LiDAR/depth/sonar/IMU reads) | `:domain` (`RobotSensorsRepository`) | `RobotSensorsRepositoryImpl` (`:data`) | REAL (per-sensor `Rated<String>`) | `docs/engineering/09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §2.1 (LiDAR), E-051 (full sensor list) | LiDAR/stereo vision/ultrasonic - physically confirmed connected to ROS/Robot Computer (E-051), not the RK3288 | Unconfirmed whether current SDK reads pass through ROS or a separate path | Sensor-to-computer wiring topology NOT VERIFIED |
| Mapping (live SLAM session) | `:domain` (`RobotMappingRepository`) | `NotAvailableMappingRepository` (`:data`) | NOT_AVAILABLE | `docs/engineering/04_SLAM_INVESTIGATION_STATUS.md`, `09` §5 | Separate ROS/Robot Computer (unconfirmed identity) | Yes - no confirmed ROS mapping API exists | Yes - entire capability |
| Map management (file-level list/download/deploy) | `:domain` (`RobotMapRepository`) | `RobotMapRepositoryImpl` (`:data`) | REAL/GATED per operation | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §8 | RK3288 -> Peanut SDK `MapComponent`/`MapManager` | No | Live-deploy path NOT VERIFIED |
| Cleaning (start/pause/resume/stop, zones) | `:domain` (`CleaningRepository`) | `SimulatedCleaningRepository` (`:data`) | SIMULATED | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §14 | Fan/pump/brush motor assemblies exist physically (PH-01 through PH-03, PH-07) but no `CleanComponent` API in the licensed SDK | No | Physical cleaning-hardware control entirely unverified from software |
| Charging | `:domain` (`RobotChargingRepository`) | `RobotChargingRepositoryImpl` (`:data`) | REAL/GATED | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §17 | Charging-contact plates (PH-09, photographically confirmed) | No | Electrical spec of contacts NOT VERIFIED |
| Diagnostics (actuator self-tests) | `:domain` (`RobotDiagnosticsRepository`) | `RobotDiagnosticsRepositoryImpl` (`:data`) | REAL (reads) / UNAVAILABLE (unsupported actuator tests) | `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §19 | Varies per test | No | Per-test, see `RobotDiagnosticsRepository.actuatorTests` |
| Telemetry (unified snapshot) | `:domain` (`RobotTelemetryRepository`) | `RobotTelemetryRepositoryImpl` (`:data`) | REAL (re-projection of connection + battery, only when both are real) | This document + `RobotConnectionRepository`/`RobotBatteryRepository`'s own status | Same as constituent repositories | No | No new dependency beyond constituents |
| Workstation | `:domain` (`RobotWorkstationRepository`) | `RobotWorkstationRepositoryImpl` (`:data`) | Per existing impl (unchanged by this pass) | `docs/engineering/09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §2.6 | Workstation water-filling/charging hardware (headings-only official coverage) | No | Electrical/interface spec NOT DOCUMENTED officially |

## Capabilities named in the task with no corresponding software interface (by design)

These were explicitly requested as architectural boundaries but map onto existing interfaces
rather than new ones - listed here so the registry is a complete answer to "where did each
requested boundary go," not just a list of new types:

- **RobotConnection** -> split across the two connection rows above (existing Peanut-SDK link +
  new ROS/Robot Computer link) - these are architecturally different systems and are recorded as
  two rows, not one.
- **RobotDrive** -> the "Drive (manual jog)" row above - no separate `RobotDrive` interface exists;
  `RobotNavigationRepository.jog()` already serves this purpose at the domain layer.

## Hardware/ROS dependency cross-reference

Every "Hardware dependency" cell above that names a physically-observed component traces back to
`docs/engineering/10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md` and
`docs/engineering/02_EVIDENCE_REGISTER.md` E-051/E-053 through E-061. Nothing in this registry
upgrades any of those findings' status - a capability marked `NOT_VERIFIED` here stays
`NOT_VERIFIED` until the corresponding evidence register entry is updated with new, physically-
confirmed evidence, per this project's own rule against silently upgrading `UNKNOWN`/`INFERRED`
findings.
