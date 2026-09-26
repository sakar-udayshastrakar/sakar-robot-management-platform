# Sakar Robot Software Architecture

**This document describes architecture PREPARATION, not a live integration.** Every new interface
and class introduced alongside this document returns an explicit `NOT_AVAILABLE`/`NOT_VERIFIED`
result for anything not already backed by a confirmed, real data path. No ROS topic, service,
action, port, protocol, or IP address is implemented or guessed anywhere in this pass. No existing
`SakarC40Agent` behavior, UI, or test changes as a result of this document or its accompanying code
- see §9 for the exact list of what remains deliberately unimplemented.

---

## 1. Application boundaries

`SakarC40Agent` (this app) keeps exactly the scope it already has, per Phase 3's application-
separation findings (`docs/architecture/SAKAR_APPLICATION_BOUNDARIES.md`,
`docs/engineering/05_KEENON_TO_SAKAR_ARCHITECTURE.md` §"Application layer"):

- Operator/Cleaning
- Manual Drive
- Scheduled Cleaning
- Teaching
- Super User
- Robot Debugging

**Installation, live mapping, map management, and localization are explicitly NOT added back into
`SakarC40Agent`'s UI.** The existing `installation`/`maps` screens
(`operator-ui/.../installation/InstallationScreens.kt`, `operator-ui/.../maps/MapsScreens.kt`)
remain exactly as documented in `docs/ui/ROUTE_REGISTRY.md` -
**`ORPHANED`, unchanged by this pass.** Nothing in this architecture work reconnects them to the
visible navigation.

Instead, a **separate application boundary, `SakarInstallationAssistant`,** is prepared for:
Installation, Mapping, Map management, Localization, Map deployment - matching Keenon's own
precedent of a separate Installation Assistant APK
(`docs/architecture/KEENON_APPLICATION_SEPARATION_MAP.md` §2).

**This phase creates the shared contracts only, not the second APK**, per this task's own explicit
instruction. The contracts a future `SakarInstallationAssistant` module would depend on already
exist in `:domain` today, exactly as `:operator-ui` depends on `:domain` today:

- `com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway`
- `com.sakarrobotics.c40agent.domain.repository.RobotMappingRepository`
- `com.sakarrobotics.c40agent.domain.repository.RobotMapRepository` (existing, file-level)
- `com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter`

A future `SakarInstallationAssistant` Gradle module would add `implementation project(':domain')`
(and, if it needs the legacy diagnostics shell the way `:operator-ui` does, `:ui`) and nothing else
new-stack - it would never depend on `:data`, `:robot`, or `:sdk` directly, mirroring
`:operator-ui`'s own existing boundary. No such module is created in this pass.

---

## 2. Robot Gateway

`com.sakarrobotics.c40agent.domain.gateway.SakarRobotGateway` (`:domain`) is the seam between Use
Cases and the individual Capability Interfaces:

```
UI
 v
ViewModel
 v
Use Cases
 v
SakarRobotGateway
 v
Capability Interfaces (RobotConnectionRepository, RobotNavigationRepository, ...)
 v
Real Adapter (C40RobotController-backed)  /  Simulation Adapter (SimulatedMotorController, ...)
```

**Deliberately not yet wired into any existing use case, ViewModel, or screen.** `AppContainer`
gained one new property, `robotGateway: SakarRobotGateway`
(`domain/di/AppContainer.kt`), and `DefaultAppContainer` constructs a `SakarRobotGatewayImpl`
(`:data`) that forwards to the *same* repository instances it already constructs for its existing
individual properties - see `data/di/DefaultAppContainer.kt`. Every existing `AppContainer`
property (`connectionRepository`, `batteryRepository`, ...) is completely unchanged; nothing that
currently reads them needs to change, and nothing does in this pass.

Why introduce it now, unused: so a future mapping use case (this app's own, or
`SakarInstallationAssistant`'s) has one narrow, purpose-built interface to depend on instead of the
full `AppContainer` grab-bag (which also carries purely-local state - schedules, consumables,
routes, logs, auth, preferences - that will never be "plugged into" a real ROS/Robot Computer).

---

## 3. Capability interfaces

Nine capability boundaries were requested. Two are genuinely new; seven already exist and are
reused as-is rather than duplicated, per this project's own "one feature, one owner" rule
(`docs/ui/CLAUDE_UI_RULES.md` rule 5, extended here to the backend layer for the same reason: a
second interface for something that already has one is drift waiting to happen, not preparation).

| Requested boundary | Interface | Status | Notes |
|---|---|---|---|
| RobotConnection | `RobotConnectionRepository` (existing) + `RosRobotAdapter` (**new**) | Existing = REAL (Peanut SDK link); new = stub | Two distinct connections to two distinct systems - never conflated. See §4. |
| RobotDrive | `RobotNavigationRepository.jog(linear, angular)` (existing) | REAL/SIMULATED via existing `RobotMotionController` (`:data`-internal) | No new interface. Manual low-level movement is already a domain-level capability exposed by `jog()`; `RobotMotionController` itself is intentionally `:data`-internal (only `RobotNavigationRepositoryImpl` calls it) and was left that way. |
| RobotNavigation | `RobotNavigationRepository` (existing) | REAL/GATED | Unchanged. |
| RobotSensors | `RobotSensorsRepository` (existing) | REAL | Already covers LiDAR/depth/sonar/IMU via `SensorReadings`. Unchanged. |
| RobotMapping | `RobotMappingRepository` (**new**) | Always `NOT_AVAILABLE` | Live SLAM session control - start/stop/save/load/localize/pose/metadata. See §6. |
| RobotCleaning | `CleaningRepository` (existing) | SIMULATED | Unchanged - already explicitly labeled simulated end-to-end (no `CleanComponent` in the licensed SDK). |
| RobotCharging | `RobotChargingRepository` (existing) | REAL/GATED | Unchanged. |
| RobotDiagnostics | `RobotDiagnosticsRepository` (existing) | REAL/UNAVAILABLE per test | Unchanged. |
| RobotTelemetry | `RobotTelemetryRepository` (**new**) | REAL | A thin re-projection of `RobotConnectionRepository`/`RobotBatteryRepository`, not a new data source. See §5. |

File-level map management (`RobotMapRepository` - list/download/deploy already-built maps via the
public Peanut SDK) is unchanged and is exposed via the gateway as `SakarRobotGateway.maps`,
distinct from `SakarRobotGateway.mapping` (the new, live-SLAM boundary) - see §6 for why these are
two different things, not a duplicate.

---

## 4. ROS adapter boundary

`com.sakarrobotics.c40agent.domain.repository.RosRobotAdapter` (`:domain`) supports, per this
task's explicit scope, only:

- Connection state (`connectionState: Flow<RosConnectionState>`)
- Connect/disconnect lifecycle (`connect(config: RosEndpointConfig)`, `disconnect()`)
- Capability discovery/status (`discoverCapabilities(): RobotCapabilityResult<List<RosCapabilityDescriptor>>`)

**No ROS topic, service, or action method exists on this interface, and none should be added until
a real ROS/Robot Computer connection is physically confirmed** (see
`docs/engineering/03_HARDWARE_ARCHITECTURE.md` §2.1/§6 and
`docs/engineering/02_EVIDENCE_REGISTER.md` E-052/E-060 for exactly what remains unconfirmed).

`RosEndpointConfig` (`host`, `primaryPort`, `secondaryPort`) is a plain data class with **null
defaults** - no code path anywhere defaults to `192.168.64.20`/`9090`/`9091`. Those values are
recorded, and remain, historical reverse-engineering references only
(`docs/engineering/04_SLAM_INVESTIGATION_STATUS.md` §4) - the config type exists specifically so
nothing can silently fall back to them.

The only implementation, `RosRobotAdapterStub` (`:data`), always reports `RosConnectionState.
DISCONNECTED` and returns `RobotCapabilityResult.Unavailable` from every suspend function,
regardless of what `RosEndpointConfig` it's given - it does not attempt any real connection, does
not open a socket, and does not import a WebSocket/rosbridge client. Replacing this one class (not
extending `RosRobotAdapter` itself) is the intended integration point once a real endpoint and
protocol are confirmed.

---

## 5. Real vs. simulation

Every capability's status is one of four values -
`com.sakarrobotics.c40agent.domain.model.RobotCapabilityStatus`:

| Status | Meaning |
|---|---|
| `REAL` | Backed by a confirmed, live data path. |
| `SIMULATED` | No real hardware/ROS path exists; an explicitly-labeled local simulation. |
| `NOT_AVAILABLE` | Confirmed absent - e.g. no SDK/ROS API exists for this at all. |
| `NOT_VERIFIED` | Not yet confirmed either way - requires physical/ROS verification. |

This is a **new, separate** enum from the existing `com.sakarrobotics.c40agent.domain.model.
Capability` (`REAL`/`SIMULATED`/`LOCAL`/`GATED`/`UNAVAILABLE`) already used throughout the operator
UI's `CapabilityBadge`. It is not a replacement, and no existing screen's badge logic changes.
It exists because the ROS/mapping/telemetry seam needs to distinguish "confirmed absent"
(`NOT_AVAILABLE`) from "not yet investigated" (`NOT_VERIFIED`) - a distinction the rest of the app
has never needed, matching this project's own existing evidence-status discipline
(`docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`'s status vocabulary).

**The "never fake success" rule is enforced at the type level, not just by convention:**
`RobotCapabilityResult<T>` is a sealed class with exactly two cases:

```kotlin
sealed class RobotCapabilityResult<out T> {
    data class Available<T>(val value: T, val status: RobotCapabilityStatus) : RobotCapabilityResult<T>()
    data class Unavailable(val status: RobotCapabilityStatus, val reason: String) : RobotCapabilityResult<Nothing>()
}
```

`Available`'s constructor rejects (`IllegalArgumentException`, checked by
`RobotCapabilityResultTest`) any status other than `REAL`/`SIMULATED`; `Unavailable`'s constructor
rejects any status other than `NOT_AVAILABLE`/`NOT_VERIFIED`. There is no way to construct an
`Available<RobotPose>` tagged `NOT_AVAILABLE`, and no way to obtain a `T` value at all from an
`Unavailable` result - a caller is forced to handle the "not available" case explicitly rather than
being able to silently unwrap a placeholder value.

---

## 6. Mapping boundary

`RobotMappingRepository` (`:domain`) is deliberately distinct from the existing, already-real
`RobotMapRepository`:

| | `RobotMapRepository` (existing, unchanged) | `RobotMappingRepository` (new) |
|---|---|---|
| Scope | File-level: list/download already-built maps, push a map file | Live session: start/stop mapping, save/load, localize, pose, metadata |
| Backing | Public Peanut SDK (`MapComponent`/`MapManager`) | None yet - no confirmed SDK or ROS API |
| Status today | `REAL`/`GATED` per operation | Always `NOT_AVAILABLE` |

`NotAvailableMappingRepository` (`:data`) is the only implementation. Every method - `startMapping`,
`stopMapping`, `saveMap`, `loadMap`, `localize`, `currentPose`, `mapMetadata` - returns
`RobotCapabilityResult.Unavailable(NOT_AVAILABLE, reason)`, never a fabricated `RobotPose` or
`RobotMapMetadata`. `mappingStatus` (a `Flow<RobotCapabilityResult<MappingSessionState>>`) starts,
and stays, `Unavailable` for the same reason. This matches
`docs/engineering/04_SLAM_INVESTIGATION_STATUS.md`'s own conclusion: live mapping/SLAM has no
confirmed SDK or ROS API as of this session, and
`docs/engineering/09_KEENON_OFFICIAL_C40_DOCUMENTATION_AUDIT.md` §5 independently confirms official
documentation says nothing about it either.

---

## 7. Hardware capability registry

Full table: [`SAKAR_CAPABILITY_REGISTRY.md`](SAKAR_CAPABILITY_REGISTRY.md).

---

## 8. Future physical integration flow

Once a real ROS/Robot Computer endpoint and protocol are physically confirmed (per
`docs/engineering/07_NEXT_PHYSICAL_ROBOT_AUDIT.md` Phase G), the intended integration sequence is:

1. Replace `RosRobotAdapterStub` with a real implementation (new class, same `RosRobotAdapter`
   interface) that actually opens a connection using the confirmed protocol/endpoint, sourced from
   `RosEndpointConfig` supplied externally (settings/remote-config) - never a hardcoded default.
2. Extend `RosRobotAdapter` only with methods backed by confirmed, physically-verified ROS
   topics/services - never guessed ones.
3. Replace `NotAvailableMappingRepository` with a real implementation backed by that confirmed ROS
   connection, once the SCM-IoT-vs-ROS-Robot-Computer topology question
   (`docs/engineering/02_EVIDENCE_REGISTER.md` E-052) and the specific mapping-control protocol are
   both resolved by physical verification - not before.
4. Update `RobotTelemetryRepositoryImpl` to fold in ROS-sourced telemetry once available, still
   never reporting `REAL` unless every constituent reading is itself real.
5. Only then, build `SakarInstallationAssistant` as its own Gradle module, depending on `:domain`
   exactly as `:operator-ui` does, and wire its UI through `SakarRobotGateway.mapping`/`.ros`.
6. Existing `SakarC40Agent` screens are rewired through `SakarRobotGateway` only if/when there is a
   concrete reason to (e.g. Manual Drive wanting to observe `SakarRobotGateway.ros.connectionState`
   directly) - not automatically, and not as part of this preparation phase.

---

## 9. Unsupported / unverified capabilities

Explicitly NOT implemented in this pass, per this task's own instruction:

- Live ROS/SLAM control of any kind.
- Any ROS topic, service, or action - guessed or otherwise.
- CAN bus communication.
- LoRa communication (the "USB LoRa"/E22-900T22S module documented in
  `docs/engineering/10_PHYSICAL_HARDWARE_PHOTO_EVIDENCE.md` PH-05 has no software counterpart here).
- Any camera driver (USB or internal-wired).
- Any unknown hardware protocol.
- A second APK (`SakarInstallationAssistant` remains contracts-only).
- Any change to existing UI, ViewModels, or use cases.

---

## 10. Data ownership

- **`:domain`** owns every new interface and model introduced in this pass (`SakarRobotGateway`,
  `RosRobotAdapter`, `RobotMappingRepository`, `RobotTelemetryRepository`, `RobotCapabilityStatus`,
  `RobotCapabilityResult`, and their supporting models) - zero Android/SDK dependency, exactly like
  every other `:domain` type.
- **`:data`** owns every new implementation (`RosRobotAdapterStub`, `NotAvailableMappingRepository`,
  `RobotTelemetryRepositoryImpl`, `SakarRobotGatewayImpl`) and is the only place that constructs
  them, via `DefaultAppContainer` - matching the existing rule that `:data` is the only new-stack
  module allowed to depend on `:robot`.
- **No new module** was created. `RosRobotAdapterStub` living in `:data` rather than a dedicated
  future `:ros` module is a deliberate, lower-risk choice for this preparation phase (avoids Gradle/
  KSP configuration risk for a class that does nothing yet) - a dedicated module is a reasonable
  future refactor once real ROS work begins, not a decision made here.
- **Ownership of "which computer/board is which"** (the RK3288, the candidate "ARM IPC" board, the
  SCM-IoT motor controller) remains exactly as documented in
  `docs/engineering/03_HARDWARE_ARCHITECTURE.md` - this document does not re-decide, and does not
  need to re-decide, any of that to prepare the software seam above it.
