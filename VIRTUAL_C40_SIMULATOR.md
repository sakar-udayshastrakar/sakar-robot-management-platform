# Virtual C40 Robot Simulator

**Roadmap Phase 9.** Software-only simulation environment for testing the complete Sakar command architecture (backend, MQTT, agent, command lifecycle, fleet dashboard) **before connecting to a physical C40**. Nothing in this document, or in the code it describes, was tested against a physical robot.

**Status:**
```
Virtual C40 Simulator: CODE IMPLEMENTED / SOFTWARE TEST VERIFIED
Physical C40:          NOT PERFORMED
```

**Never confuse the two.** Every claim in this document is about the simulator's own software behavior. `"GO_TO_POINT software flow verified in virtual environment"` is a claim this document makes; `"GO_TO_POINT works on C40"` is not, and never will be from this work alone.

---

## 1. Architecture

```
SAKAR WEB (existing, unmodified)
    │
    ▼
SAKAR BACKEND (existing, unmodified production code — see §11)
    │
   MQTT
    │
    ▼
VIRTUAL C40 AGENT  (new :virtual-agent Gradle module — VirtualC40AgentRunner)
    │
    ▼
VIRTUAL ROBOT ENGINE  (VirtualRobotEngine)
    │
┌───┼────────────┬────────────┐
▼   ▼            ▼            ▼
Map Engine   Navigation    Battery
(VirtualMap) (goToPoint/   (tick())
             returnToDock)
    │            │            │
    └────────────┼────────────┘
                 ▼
           Robot State
        (VirtualRobotState)
```

The real path — **untouched by this work** — remains:
```
SakarC40Agent → PeanutSdkBridge → Peanut SDK → C40 local system
```
The virtual path is a completely separate module with **zero Gradle dependency** on `:sdk`/`:robot`/`:navigation`/`:charging` (the only modules that touch `com.keenon.*`). This is enforced two ways: (1) Gradle's own build graph — `virtual-agent/build.gradle` declares no such dependency, so the module would not even compile if it tried to reference `PeanutSdkBridge`/`PeanutSDK`; (2) `VirtualAgentRealSdkIsolationTest`, an automated JUnit test that reads this module's own `.java` source (with comments stripped) and fails the build if any forbidden reference ever appears in real code.

**Reuse, not duplication.** The virtual agent reuses the SAME vendor-neutral contracts the real agent uses — `CommandDispatcher`, `RobotCommandExecutor`, `RobotCommandResultReporter`, `CompositeRobotCommandExecutor`, `AgentMqttClient`, `AgentMqttTopics`, `AgentIdentity`, `SakarMqttConfig`, `HeartbeatScheduler`, `TelemetryScheduler`, `TelemetrySnapshotProvider` (all from `:api`), and `Destination`/`Pose`/`Position`/`Orientation` (from `:telemetry`, the same types the real `getAllDestPose()` integration returns). This is possible because `:api` and `:telemetry` are both plain `java-library` modules with zero Android/Peanut SDK dependency — a fact confirmed by reading their own `build.gradle` files before writing any simulator code (Phase 1 investigation).

---

## 2. Virtual robot model

`VirtualRobotState` (immutable snapshot returned by `VirtualRobotEngine.getState()`):

| Field | Type | Notes |
|---|---|---|
| `robotId` | `String` | e.g. `VIRTUAL-C40-001` |
| `robotName` | `String` | |
| `online` | `boolean` | toggled via `setOnline()` |
| `batteryPercentage` | `int` | 0-100, clamped |
| `charging` | `boolean` | true after arriving at the Charging Station |
| `currentMapId` | `String` | always `VirtualMap.MAP_ID` |
| `currentMapMd5` | `String` | always `VirtualMap.MAP_MD5` |
| `currentDestinationId` | `Integer` | starts at the Lobby's id |
| `currentX`/`currentY`/`currentZ` | `double` | current position |
| `orientation` | `double[4]` | `[w, x, y, z]` quaternion |
| `navigationState` | `NavigationState` | `IDLE`/`MOVING`/`ARRIVED`/`BLOCKED` — see §14 |
| `lastCommandId` | `String` | the most recent command processed |

`NavigationState` is a new, simulator-only enum — it does not collide with any existing domain vocabulary (the real Peanut SDK's `NavigationStatusApi` response carries raw, undocumented integer status codes, not a clean enum — see `C40_S_GO_TO_POINT_SDK_INVESTIGATION.md` §8).

---

## 3. Virtual map

**`VirtualMap.MAP_ID = "VIRTUAL_TEST_MAP"`, `VirtualMap.MAP_MD5 = "VIRTUAL-SIMULATOR-NOT-A-REAL-MD5"`.** Deliberately not shaped like a real map hash (a real `bind_map_md5` is a real MD5 hex string — this one says "NOT-A-REAL-MD5" so it can never be mistaken for one, even out of context).

This is a deterministic, hand-built, clearly-fake test map — never read from, and never resembling, any real C40 production map.

---

## 4. Virtual destinations

| Destination | ID | Type | Position (x, y, z) |
|---|---:|---|---|
| Lobby | **1001** | landmark | (0, 0, 0) — the robot's start position |
| Reception | **1002** | landmark | (3, 0, 0) |
| Room A | **1003** | landmark | (3, 4, 0) |
| Charging Station | **1004** | charger | (0, 4, 0) |

**These destination IDs are simulation-only and must never be used on a physical C40.** They were not read from, and do not correspond to, any real C40 S map — a real destination id requires a live `NavigationComponent.getAllDestPose()` call against a specific physical robot's currently-loaded map (see `C40_S_DESTINATION_DISCOVERY_INVESTIGATION.md`); this table is not that.

`VirtualRobotEngine.getAllDestinations()` returns exactly these 4 as `com.sakarrobotics.c40agent.telemetry.Destination` objects — the identical class the real integration returns, so a consumer cannot tell from the *shape* of the data whether it came from a real or virtual robot (only from the manufacturer/model registration, §11, and the map id/name explicitly saying "VIRTUAL").

---

## 5. GO_TO_POINT simulation

```
VirtualRobotCommandExecutor.execute("GO_TO_POINT", {destinationId}, reporter)
    → extract & validate destinationId (same rule as the real PeanutSdkGoToPointExecutor
      and the backend's RobotCommandService - non-negative integer, never invented)
    → VirtualRobotEngine.goToPoint(destinationId, commandId, reporter)
        → offline?           → reportFailed, done (Phase 10 "robot offline")
        → invalid destination? → reportFailed, done (Phase 10 "invalid destination")
        → insufficient battery? → reportFailed, done (Phase 10 "insufficient battery")
        → blocked (one-shot)?  → reportFailed, navigationState=BLOCKED (Phase 10 "navigation blocked")
        → else: navigationState=MOVING, reportExecuting()
              → N ticks (configurable, default 3×100ms) drain battery deterministically
              → navigationState=ARRIVED, position updated to the destination's pose,
                reportCompleted("SIMULATED — ...")
```

Not an instant teleport — a short, configurable number of ticks (`ticksToArrive`, default 3, at `tickIntervalMillis`, default 100ms) simulate the `START → MOVING → ARRIVED` progression the task specified, fully deterministically: the exact same inputs always produce the exact same tick count and final state.

---

## 6. RETURN_TO_DOCK simulation

`VirtualRobotEngine.returnToDock()` is `goToPoint(VirtualMap.chargingStation().getId(), ...)` with one addition: on arrival, `charging` is set to `true`. The virtual charging station is `VirtualMap.DESTINATION_ID_CHARGING_STATION` (1004) — a known destination on the same virtual map, not a separate concept. **The real Peanut SDK's `BatteryComponent.autoCharge()`/`C40RobotController.returnToDock()` path was not touched, read, or referenced by any executable code in this module** (verified by `VirtualAgentRealSdkIsolationTest`).

---

## 7. Battery simulation

`VirtualRobotEngine.tick()` — one deterministic simulation step, called automatically during a `goToPoint`/`returnToDock` arrival sequence, and callable directly for isolated, instant battery-arithmetic testing:

- **Navigating (`MOVING`)**: battery decreases by `batteryDrainPerTick` (default 2) per tick, clamped at 0.
- **Idle, not charging**: battery is unchanged.
- **Charging**: battery increases by `batteryChargePerTick` (default 5) per tick, clamped at 100; `charging` automatically becomes `false` once 100% is reached.

All rates, the arrival tick count, and the tick interval are constructor parameters — configurable per engine instance, not global constants. **No value here is presented as, or derived from, a measurement of a real C40's battery.**

---

## 8. Offline simulation

`VirtualRobotEngine.setOnline(false)`:
- `goToPoint`/`returnToDock` immediately `reportFailed("... OFFLINE ...")`, never `reportExecuting()`.
- `getAllDestinations()` throws `VirtualRobotOfflineException` rather than returning stale/cached data.
- The robot's position, battery, map, and navigation state are all left exactly as they were — nothing resets or drifts while offline.

`VirtualRobotRegistry` proves, in its own tests, that one robot going offline never affects any other robot's online state.

---

## 9. Failure simulation

All deterministic, none random (Phase 10 explicitly forbids randomness):

| Scenario | Trigger | Result |
|---|---|---|
| Invalid destination | `destinationId` not on `VirtualMap` | `reportFailed("Invalid destinationId=...")` |
| Robot offline | `setOnline(false)` | `reportFailed("... OFFLINE ...")` |
| Insufficient battery | `batteryPercentage < minimumBatteryToNavigate` (default 10%) | `reportFailed("Insufficient battery ...")` |
| Navigation blocked | `setBlocked(true)` (one-shot) | `reportFailed("Navigation blocked (simulated obstacle) ...")`, `navigationState=BLOCKED` |
| Command expired | already-past `expiresAt` on the `CommandPayload` | Handled entirely by the EXISTING `CommandDispatcher` (unmodified) — `TIMEOUT`, executor never called |
| Duplicate command | same `commandId` redelivered | Handled entirely by the EXISTING `CommandDispatcher` (unmodified) — second delivery is a no-op |

---

## 10. Multi-robot simulation

`VirtualRobotRegistry.withDefaultFleet()` creates exactly 3 independent `VirtualRobotEngine` instances: `VIRTUAL-C40-001`, `VIRTUAL-C40-002`, `VIRTUAL-C40-003`. Each owns its own position/battery/map-state/destination/navigation-state/last-command-id as private instance fields on a separate object — there is no shared mutable state anywhere between engines, so a command issued against one robotId structurally cannot affect another (proven, not just asserted, by `VirtualRobotRegistryTest`).

---

## 11. MQTT flow

`VirtualC40AgentRunner` wires one `VirtualRobotEngine` to the Sakar Cloud backend using the exact same MQTT client/topic/scheduler classes `SakarC40Application` uses for the real robot — `AgentMqttClient`, `AgentMqttTopics`, `HeartbeatScheduler`, `TelemetryScheduler` (backed by the new `VirtualTelemetrySnapshotProvider`), `CommandDispatcher` (routing `GO_TO_POINT`/`RETURN_TO_DOCK` to `VirtualRobotCommandExecutor`). It connects **only** to whatever `SakarMqttConfig` the caller supplies — the same broker a real agent would use, configured the same "local development configuration" way (git-ignored `secrets.properties`-style values), never a hardcoded or robot-side address. This class was **not run against a live broker in this pass** (no local MQTT broker was started) — its wiring is structurally identical to `SakarC40Application`'s already-working real-agent wiring, so its correctness rests on that established pattern plus this module's own unit tests of the pieces it composes, not a live end-to-end run.

---

## 12. Web UI

**No frontend code was changed.** The Sakar Web application already renders whatever the robot-registry and command REST APIs return — a virtual robot registered as a normal `Robot` row (see §11's `VirtualRobotSimulatorIntegrationTest`, backend-only, no frontend) already appears in, and is commandable from, the existing Robot Detail screen with zero UI changes, exactly per this task's "do not redesign the entire UI" instruction.

**How "SIMULATED ROBOT" is clearly indicated**: by registering the robot under a manufacturer named `"Sakar Virtual Simulator"` and a model named `"VIRTUAL-C40-SIM"` — both already-existing, already-rendered fields on the Robot Detail screen. This uses the existing data model rather than inventing a new column, flag, or badge component. Additionally, every telemetry reading `VirtualTelemetrySnapshotProvider` publishes is prefixed `simulated.` (e.g. `simulated.battery_percentage`), so raw telemetry can never be mistaken for a physical reading either.

**Not performed this pass**: live-browser verification of the actual rendered screen (no local Postgres/Redis/Docker available in this environment — the same limitation already noted elsewhere in this project's README for other passes). The REST contract itself (registration + command issuance) was exercised for real against a real (test-profile) database in `VirtualRobotSimulatorIntegrationTest`.

---

## 13. Test strategy

| Layer | Test class | What it proves |
|---|---|---|
| Engine | `VirtualRobotEngineTest` (13 tests) | Creation defaults, discovery + map association, full `GO_TO_POINT` lifecycle (valid/invalid/offline/low-battery/blocked), arrival + position update, `RETURN_TO_DOCK` + charging, battery drain/charge arithmetic |
| Multi-robot | `VirtualRobotRegistryTest` (4 tests) | Default fleet composition, isolation between robots, independent online/offline state |
| Executor/dispatcher integration | `VirtualRobotCommandExecutorTest` (6 tests) | Command routing, missing-param rejection, unsupported command type, and — via the REAL `CommandDispatcher` from `:api`, unmodified — duplicate-command and expired-command handling |
| Architectural isolation | `VirtualAgentRealSdkIsolationTest` (2 tests) | No real-SDK/physical-robot reference anywhere in this module's own code; a second test guards against the isolation check itself passing vacuously |
| Backend/API integration | `VirtualRobotSimulatorIntegrationTest` (backend, 1 test) | 3 virtual robots register and accept `GO_TO_POINT`/`RETURN_TO_DOCK` through the existing, unmodified REST API |

**25 new `:virtual-agent` tests + 1 new backend test = 26 new tests, all passing, all software-only.**

---

## 14. Difference between virtual and physical C40

| | Physical C40 (existing, untouched) | Virtual C40 (this work) |
|---|---|---|
| Command reaches | `PeanutSdkBridge` → real Peanut SDK → real CoAP/serial link → real chassis | `VirtualRobotEngine` → in-process Java state machine |
| Completion semantics | `RETURN_TO_DOCK`/`GO_TO_POINT` report **`DISPATCHED`** — SDK acceptance only, real-world effect unconfirmed | Reports **`COMPLETED`**, always prefixed `"SIMULATED"` — the engine has complete ground truth over its own simulated world, so completion is honest, not an overclaim, and the prefix prevents it from being read as physical |
| Destinations | Real, pre-registered ids on a real map, discovered via a live `getAllDestPose()` call — never invented by this project | 4 fixed, clearly-fake ids (1001-1004) on `VIRTUAL_TEST_MAP` — never usable on a real robot |
| Battery/position | Read from the robot, never fabricated | Deterministically simulated, explicitly labeled as such |
| Network | Real robot LAN, CoAP, serial | None — only the Sakar MQTT broker, same as any agent |
| `OperatingMode.HARDWARE_TEST` | Still gates all real actuation, unchanged | Not applicable — the virtual engine has no physical actuation to gate |

---

## 15. Limitations

- Not run end-to-end against a live MQTT broker in this pass (`VirtualC40AgentRunner` is unit-tested via its composed pieces, not via a live connection).
- Not live-browser-verified in the actual Sakar Web application (no local Postgres/Redis/Docker available).
- Only `GO_TO_POINT` and `RETURN_TO_DOCK` are wired to virtual executors — `START_TASK`/`STOP_TASK`/`PAUSE_TASK`/`RESUME_TASK` have no virtual (or real) executor.
- The "insufficient battery" and "blocked" scenarios are exercised via engine-level test setup (a purpose-tuned engine, or `setBlocked()`), not via a dedicated MQTT-level "force a failure" command — this is intentional (a real robot doesn't expose a "make my battery low" API either), but means a fleet-wide chaos-testing tool was not built here.
- No physical C40 was contacted, tested, or referenced by any executable code in this entire pass.
