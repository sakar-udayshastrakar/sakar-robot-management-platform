# api module

The integration surface between `SakarC40Agent` and the Sakar Cloud
backend over MQTT (see the second architecture diagram in the root
`README.md`). This module has real networking code as of Roadmap Phase 3
(telemetry/heartbeat/presence/events/errors) and Phase 7 (inbound command
consumption + result reporting) — it is **not** a placeholder.

This module never imports `com.keenon.*` and never depends on `:sdk`/
`:robot`; it only depends on the plain-data types it defines itself
(`AgentIdentity`, `SakarMqttConfig`) plus interfaces the `app` module
implements (`TelemetrySnapshotProvider`, `RobotCommandExecutor`). No MQTT
broker or physical robot has been used to verify this module — only
automated tests (JUnit 5 + Mockito, no embedded broker; see the test
sources under `src/test`).

## Key classes

- `AgentMqttClient` — the Eclipse Paho MQTT client wrapper. Connects,
  publishes `PRESENCE`/`HEARTBEAT`/`TELEMETRY`/`EVENT`/`ERROR` envelopes,
  subscribes to this agent's own `ack` and (Phase 7) `commands` topics.
- `AgentMqttTopics` — builds this agent's topics, matching the backend's
  `{prefix}/{organizationId}/{siteId|_}/{robotId}/{kind}` scheme exactly.
- `MqttEnvelope` / `MqttMessageType` — the agent-authored envelope shape
  (`PRESENCE, HEARTBEAT, TELEMETRY, EVENT, ERROR` only — `ACK` is
  cloud-authored and only ever parsed here, never published).
- `CommandDispatcher` (Phase 7) — consumes inbound `CommandPayload`
  messages (a deliberately flat, non-`MqttEnvelope`-nested JSON shape —
  see that class's Javadoc for why), drives a `RobotCommandExecutor`, and
  reports RECEIVED/EXECUTING/COMPLETED/DISPATCHED/FAILED/TIMEOUT lifecycle
  results back over the *existing* EVENT channel (`eventType="COMMAND_RESULT"`)
  — no new MQTT message type or topic was added for this. Guards against
  double-execution (bounded "seen commandId" set) and executing an
  already-expired command.
- `RobotCommandExecutor` / `RobotCommandResultReporter` (Phase 7) — the
  seam this module depends on but never implements, exactly like
  `TelemetrySnapshotProvider`. `RobotCommandResultReporter.reportDispatched(String)`
  (added in the `RETURN_TO_DOCK` pass) is for when an executor has
  evidence the local control interface accepted a command but NOT
  evidence its real-world effect completed — never call `reportCompleted`
  for that case.
- `CompositeRobotCommandExecutor` (Phase 7, `RETURN_TO_DOCK` pass) —
  routes a command to the `RobotCommandExecutor` registered for its
  `commandType`, so `CommandDispatcher` itself never needs to change as
  more command types get real executors.
- `ReturnToDockGateway` / `PeanutSdkReturnToDockExecutor` (Phase 7,
  `RETURN_TO_DOCK` pass) — the REAL (non-simulated) executor for
  `RETURN_TO_DOCK`, calling the officially-distributed Peanut SDK's
  `BatteryComponent.autoCharge()` through the `app` module's
  `RealReturnToDockGateway` (which wraps `C40RobotController.returnToDock()`,
  itself gated by `OperatingMode.HARDWARE_TEST`). See
  `PeanutSdkReturnToDockExecutor`'s own Javadoc for the exact verified SDK
  contract and why it reports `DISPATCHED`, not `COMPLETED`.
- `app` still supplies `SimulatedRobotCommandExecutor` for `START_TASK` —
  an explicitly-labeled software placeholder, not a real robot call,
  because no supported cleaning-control API exists (see the root
  `README.md`'s Current Phase note and
  `../../../ROBOT_AGENT_COMMAND_LOOP_INVESTIGATION_AND_DESIGN.md`).

## What this module deliberately does not do

It never decides *whether* a command is safe to execute — that remains
`C40RobotController`'s `OperatingMode` guard (`:robot` module).
`SimulatedRobotCommandExecutor` bypasses that guard entirely simply by
never calling `C40RobotController` at all (there is nothing to bypass
when nothing real is called). `PeanutSdkReturnToDockExecutor` is the
proof this project's stated policy holds in practice: its real
`ReturnToDockGateway` implementation goes straight through
`C40RobotController.returnToDock()`'s existing guard, unmodified — it
does not, and could not without editing `:robot` itself, route around it.
