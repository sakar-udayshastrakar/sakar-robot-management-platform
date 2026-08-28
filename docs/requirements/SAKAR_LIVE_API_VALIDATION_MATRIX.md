# Sakar Live API Validation Matrix — Keenon C40 S (Keenon Open Platform)

**Status:** documentation only — this matrix records evidence, it does not implement anything. **Companion to:** `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 40, and the archived source document `SAKAR_KEENON_C40S_LIVE_API_TESTING_REFERENCE.pdf` in this same directory.

**Test environment:** Keenon Store ID `C00715655` ("Sakar robotics office"), Robot Name "Demo Piece", Robot ID/SN `94:BA:06:CA:99:F3`, Keenon Model C40 S (Sakar product name: **Sakar CleanBot 5000 Plus**), Scene `dTW2N7` ("SR Cleaning"), Map ID `4c0075859805496eb452187b3cd91107`, Charging Point `39` ("1_Charging pile"), base URL `https://cloud.robotkeenon.com`.

**Grading columns, defined precisely (do not conflate):**
- **Live Test Result** — what the supplied evidence actually shows: `Accepted` (API returned a success code), `Accepted + Logged` (API accepted **and** a corresponding history/log entry confirms it), `Documented only` (cURL supplied, no captured response), `Read confirmed` (a GET returning real, non-empty data).
- **Sakar Mapping** — the generic capability this maps to in the Sakar Robot Capability Abstraction (`SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` §6.A): `GET_STATUS`, `GET_BATTERY`, `GET_TELEMETRY`, `START_TASK`, `STOP_TASK`, `PAUSE_TASK`, `RESUME_TASK`, `RETURN_TO_DOCK`.
- **Dependency** — `KEENON-CLOUD DEPENDENT` (reached only via `https://cloud.robotkeenon.com` in this evidence) vs `SAKAR-OWNED` (n/a for everything in this matrix — nothing here was tested through a Sakar-owned path).
- **Status** — `CONFIRMED` / `DOCUMENTED` / `REQUIRES PHYSICAL TEST` / `UNKNOWN`, per the vocabulary in Part 40.

---

## 1. Authentication

| Field | Value |
|---|---|
| API | `POST /api/open/oauth/token` |
| Purpose | Obtain a bearer access token via `client_credentials` grant |
| Input | `client_id`, `client_secret` (form-urlencoded; **placeholders only in the source document — no real credential was disclosed**), `grant_type=client_credentials` |
| Output | `access_token`, stored client-side (Postman) as the `token` environment variable |
| Live Test Result | Documented only — the source document does not include a captured token response, only the request shape and the Postman convention for storing the result |
| Sakar Mapping | N/A — authentication itself is not a robot capability; the equivalent Sakar concern is Keenon credential custody, see `SAKAR_SECURITY_REQUIREMENTS.md` §13.A |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `DOCUMENTED` |
| Notes | `client_id`/`client_secret` must never reach a frontend or mobile client — server-side only (§13.A). |

## 2. Store List

| Field | Value |
|---|---|
| API | `GET /api/open/data/v1/store/list` |
| Purpose | List stores accessible to the authenticated integration |
| Input | Bearer token |
| Output | Store records, including `C00715655` |
| Live Test Result | Read confirmed |
| Sakar Mapping | N/A (fleet/organization scoping concern, not a per-robot capability) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | Maps to Sakar `organizations`/`sites`, not stored verbatim. |

## 3. Robot List

| Field | Value |
|---|---|
| API | `GET /api/open/data/v1/store/robot/list?storeId={{store_id}}` |
| Purpose | List robots registered under a store |
| Input | `store_id`, bearer token |
| Output | Robot records, including robot `94:BA:06:CA:99:F3` |
| Live Test Result | Read confirmed |
| Sakar Mapping | N/A (robot registry concern — see `SAKAR_ROBOT_PLATFORM_DATABASE.md` `robots`) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | External robot ID/SN stored as `robots.robot_id_external`, never as the Sakar primary key. |

## 4. Robot Status

| Field | Value |
|---|---|
| API | `GET /api/open/scene/v1/robot/status?robotId={{robot_id}}` |
| Purpose | Current robot status |
| Input | `robot_id`, bearer token |
| Output | Status payload (state semantics per §13 below) |
| Live Test Result | Documented / part of the verification flow (§12 of the source document) |
| Sakar Mapping | `GET_STATUS` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | — |

## 5. Battery

| Field | Value |
|---|---|
| API | `GET /api/open/custom/robot/battery/level?robotSn={{robot_sn}}` |
| Purpose | Battery level |
| Input | `robot_sn`, bearer token |
| Output | Battery percentage |
| Live Test Result | Read confirmed |
| Sakar Mapping | `GET_BATTERY` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | — |

## 6. Cleaning Status

| Field | Value |
|---|---|
| API | `GET /api/open/custom/clean/robot/status?robotSn={{robot_sn}}` |
| Purpose | Current cleaning-task status |
| Input | `robot_sn`, bearer token |
| Output | Cleaning status payload |
| Live Test Result | Read confirmed |
| Sakar Mapping | `GET_TELEMETRY` (cleaning-specific slice) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | — |

## 7. Area List

| Field | Value |
|---|---|
| API | `GET /api/open/custom/clean/robot/area/list?storeId=...&robotSn=...&currentPage=1&pageSize=...` |
| Purpose | List cleanable areas and their current live area IDs |
| Input | `store_id`, `robot_sn`, pagination |
| Output | Areas: Conference carpet (`141af8448ffc4ddaa204259c02456eb0`), Work area (`5d5bc7a20f984f45ada7450fac4be972`), Lobby (`5efd6bfea62b4fc29c425bd36997071a`) |
| Live Test Result | Read confirmed |
| Sakar Mapping | `GET_TELEMETRY` / map data (`SAKAR_ROBOT_PLATFORM_DATABASE.md` `maps`/`map_points`) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | **Area IDs are live configuration values — must not be hardcoded.** The Conference-carpet ID had previously gone stale; sync this list per robot rather than compiling any ID into application code. |

## 8. Cleaning Modes

| Field | Value |
|---|---|
| API | `GET /api/open/custom/clean/robot/strategy/clean/model?robotSn={{robot_sn}}` |
| Purpose | List supported cleaning modes |
| Input | `robot_sn`, bearer token |
| Output | Modes 101–105 (Sweep & Mop, Water Suction, Sweep & Vacuum, Sweep & Push, Sweep) |
| Live Test Result | Read confirmed; mode 105 (Sweep) additionally confirmed via successful task execution (§10 below) |
| Sakar Mapping | `GET_TELEMETRY` / task parameters for `START_TASK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | — |

## 9. Return / Charging Points

| Field | Value |
|---|---|
| API | `GET /api/open/custom/clean/robot/strategy/back/point?robotSn={{robot_sn}}` |
| Purpose | List configured return/charging points |
| Input | `robot_sn`, bearer token |
| Output | Charging point `39` ("1_Charging pile") |
| Live Test Result | Read confirmed |
| Sakar Mapping | `RETURN_TO_DOCK` (parameter source) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | — |

## 10. Temporary Cleaning Task (Conference Carpet)

| Field | Value |
|---|---|
| API | `POST /api/open/custom/clean/robot/strategy/temporary/task` |
| Purpose | Start an immediate/temporary cleaning task on a specified area |
| Input | `robotSn`, `areaIdList: [conference_area_id]`, `cleanModelId: 105`, `cleanTimes: 1`, `backPointId: 39` |
| Output | Code `610000`, `bizType CleanStrategyTemporary` |
| Live Test Result | Accepted |
| Sakar Mapping | `START_TASK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` (API accepted only — the source document itself notes this must be verified through status/logs, not marked complete from the receipt alone; no corresponding log entry for this specific Conference-carpet call is included in the evidence) |
| Notes | A `610000` receipt means *accepted*, not *completed* — see §11 for the run that was independently confirmed complete. |

## 11. Temporary Cleaning Task (Lobby) — Fully Confirmed

| Field | Value |
|---|---|
| API | `POST /api/open/custom/clean/robot/strategy/temporary/task` |
| Purpose | Start an immediate/temporary cleaning task on the Lobby area |
| Input | `robotSn`, `areaIdList: [lobby_area_id]`, `cleanModelId: 105`, `cleanTimes: 1`, `backPointId: 39` |
| Output | Code `610000` (accepted); log entry: `cleanArea 13.24`, `cleanTiming 229 sec`, `mState 1`, `failDescCode 0` |
| Live Test Result | **Accepted + Logged** — the only capability in this matrix confirmed at all three levels (API accepted, robot executed, verified in history) |
| Sakar Mapping | `START_TASK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | This is the strongest evidence in the entire testing reference and the only run that should be cited as "verified complete" rather than merely "accepted." |

## 12. Stop / Finish Task

| Field | Value |
|---|---|
| API | `POST /api/open/custom/clean/robot/finish/task` |
| Purpose | Stop the currently running cleaning task |
| Input | `robotSn` |
| Output | Not captured in the supplied evidence |
| Live Test Result | Documented only |
| Sakar Mapping | `STOP_TASK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `DOCUMENTED` |
| Notes | Do not upgrade to `CONFIRMED` without a captured accepted response. |

## 13. Pause Task

| Field | Value |
|---|---|
| API | `POST /api/open/custom/clean/robot/pause/task` |
| Purpose | Pause the currently running cleaning task |
| Input | `robotSn` |
| Output | Not captured in the supplied evidence |
| Live Test Result | Documented only |
| Sakar Mapping | `PAUSE_TASK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `DOCUMENTED` |
| Notes | Do not upgrade to `CONFIRMED` without a captured accepted response. |

## 14. Recharge Task

| Field | Value |
|---|---|
| API | `POST /api/open/custom/clean/robot/recharge/task` |
| Purpose | Send the robot to its charging station |
| Input | `robotSn` |
| Output | Code `610000`, `bizType CleanRobotRechargeTask` |
| Live Test Result | Accepted (no corresponding log entry supplied to confirm physical docking) |
| Sakar Mapping | `RETURN_TO_DOCK` |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` (API accepted only); physical docking behavior `REQUIRES PHYSICAL TEST` |
| Notes | Do not describe this as "the robot returned to its charger" — only that the command was accepted. |

## 15. Cleaning Logs

| Field | Value |
|---|---|
| API | `GET /api/open/custom/clean/log/list?storeId=...&robotSn=...&currentPage=1&pageSize=5` |
| Purpose | Retrieve cleaning task history |
| Input | `store_id`, `robot_sn`, pagination |
| Output | Log records, including the Lobby run (§11) and historical `failDesc 467` ("no clean water added") entries |
| Live Test Result | Read confirmed |
| Sakar Mapping | `GET_TELEMETRY` / `cleaning_history` (`SAKAR_ROBOT_PLATFORM_DATABASE.md` §22) |
| Dependency | KEENON-CLOUD DEPENDENT |
| Status | `CONFIRMED` |
| Notes | This endpoint is the mechanism that upgraded the Lobby run (§11) from "accepted" to "accepted + logged." |

---

## Additional Findings (not separate API calls)

| Finding | Detail | Status |
|---|---|---|
| Robot state semantics (Open Platform V2.4) | `mainState 3` = Work; `subState 42` = Cleaning; `subState 44` = Returning | `DOCUMENTED` |
| Verification/troubleshooting flow | The source document's §12 defines a 10-step flow (list → status → area/mode/back-point reads → post task → watch status → check logs → verify `mState`/`failDesc`/`cleanArea`/`cleanTiming`) — this is the pattern the Sakar Robot Command Service should reproduce server-side before ever reporting a task as complete to a client | `DOCUMENTED` — adopt as the Sakar-side task-completion verification pattern |
| Historical supply failure | Repeated prior logs showed `failDesc 467` / no clean water added | `CONFIRMED` (historical, not from this session) |

## Not Covered By This Evidence

`LOCK`, `UNLOCK`, `GET_STATUS` for motor/lock state, live position/navigation state, and any Sakar-owned (non-Keenon-Cloud) path are **not** addressed anywhere in this matrix. See `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 11 and Part 38 for the governing status of those items — nothing here changes them.
