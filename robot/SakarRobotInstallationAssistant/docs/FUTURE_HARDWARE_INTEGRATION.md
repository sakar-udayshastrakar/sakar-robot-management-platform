# Future hardware integration - prerequisites

**Status: NOT IMPLEMENTED. NOT ENABLED.** Release 0.1.0-sim contains no robot, ROS, ADB, SSH or
network code. Every robot-dependent result in the app is produced by a local simulation and is
labelled SIMULATED on screen.

The only seam for future hardware is `core/.../hardware/RobotHardwarePort.kt`. Its single
implementation, `DisabledRobotHardwarePort`, reports `enabled = false` and nothing in the app
composition root (`AssistantServices`) references it. Adding a real implementation requires
every prerequisite below; none of them is satisfied today.

## 1. Authorization and licensing

- Written authorization from the robot vendor for any use of its SDK, services or protocols in
  an installation tool, including redistribution terms.
- No reuse of vendor assets, branding, internal APIs or undocumented interfaces unless that
  authorization explicitly covers them. The Phase 0 audit is a read-only document and is not
  permission to call anything it describes.

## 2. Interface contract

- A vendor-published or vendor-approved contract (endpoints, message formats, error codes,
  versioning) for readiness checks, sensor status, mapping control and map retrieval.
- A firmware / software compatibility matrix, with the app refusing to run against versions
  outside it.
- A decision on map formats. Simulated maps are Sakar-local occupancy grids and make **no
  claim of compatibility** with any vendor map format.

## 3. Security and access

- Authentication and authorization model for the tablet-to-robot link, agreed with the vendor.
- Secrets handled via the platform keystore, never in source, logs, reports or backups
  (`allowBackup` is already false). Reports must stay free of personal and credential data.
- Transport security requirements for the robot LAN, with certificate or identity pinning
  where the contract allows it.

## 4. Safety gating

- Mapping and any motion command must be behind explicit operator confirmation, an emergency
  stop path, and a documented safe-area procedure.
- Failure handling: loss of link, low battery and sensor faults must stop or hold the robot
  and be shown to the operator.
- A distinct, un-fakeable on-screen REAL/SIMULATED indicator driven by the active port, so a
  simulated session can never be mistaken for a real one (and vice versa). Capability states
  must come from the hardware port, never be hard-coded.

## 5. Engineering work

- Implement the real port in its own module so the simulated build contains no hardware code.
- Contract tests that run the same suite against the simulated and real ports.
- Build-flavor or feature-flag separation reviewed by a second engineer before it can be
  enabled in a release build.
- A hardware-in-the-loop test plan on a robot that is not at a customer site.

## 6. Process

- Installation on a physical robot or its tablet only by an authorized person following a
  written procedure. This repository's tooling never installs or launches anything on one.
