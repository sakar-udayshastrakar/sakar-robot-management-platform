# C40 Replica — Runtime Verification Checklist

Everything that is `CONFIRMED_STATIC` / `INFERRED` / `UNKNOWN` today and can only be upgraded to
`CONFIRMED_RUNTIME` with a physical C40 (or a reachable ROS/robot computer). **No item here may be
performed on a real robot without explicit authorization.** Read-only checks first; any actuation is
gated behind `OperatingMode.HARDWARE_TEST` and must stay off unless a human authorizes it.

## A. Read-only ROS verification (safe, if a robot/ROS PC is reachable)
- [ ] `ROS_MASTER_URI=http://192.168.64.20:11311 rosnode list` — confirm live node set vs static inventory.
- [ ] `rostopic list -v` — confirm advertised topics, pub/sub direction, types (upgrades `ROS_TOPICS_INVENTORY.md` rows).
- [ ] `rosservice list` + `rosservice type` — confirm services vs static list.
- [ ] `rostopic hz <topic>` on key topics — confirm frequencies (currently UNKNOWN).
- [ ] Confirm which launch profile actually runs on the unit (C40 vs package default).

## B. SDK / CoAP (read-only observes only)
- [ ] Confirm CoAP `coap://192.168.64.20:5683` reachable and which `/clean/*`, `/charge/*` observe topics are live.
- [ ] Confirm battery/status/telemetry read paths return real data through the Peanut SDK bridge (`:sdk`) in `DIAGNOSTIC_ONLY`.

## C. Map rendering (needs a synced real map)
- [ ] Sync a real map via backend (`POST /robots/{id}/keenon/map/sync`), fetch `GET /robots/{id}/map` (resolution/origin/size) + `/map/image` (PNG).
- [ ] Verify the ROS world↔pixel transform (COVERAGE_AND_GAP_REPORT §4) against a known point on the real map.
- [ ] Confirm robot-pose overlay alignment once live pose is available.

## D. Actuation (authorization + HARDWARE_TEST required — DO NOT run casually)
- [ ] With written authorization + a safe test area: enable `HARDWARE_TEST`, verify RETURN_TO_DOCK via the real executor, then restore `DIAGNOSTIC_ONLY`.
- [ ] Start/pause/resume/stop cleaning end-to-end (web → backend → Keenon adapter) against a test robot.
- [ ] Confirm MQTT command→ack→physical-effect loop (agent START_TASK executor is currently a labeled simulation).

## E. Visual parity (needs original-robot reference screenshots)
- [ ] Obtain authoritative original-robot screenshots for each UNCONFIRMED screen (see `docs/ui/REFERENCE_UI_MAP.md`), add a reference row **before** adjusting UI, then re-capture replica screenshots and compare.
