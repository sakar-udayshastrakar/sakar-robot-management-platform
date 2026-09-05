# Keenon C40 S Map/Scene Integration (Phase 1I)

## 1. Objective

Resolve, live-verify, and implement a correct source for a Keenon C-series robot's map `sceneCode` — the identifier `KeenonMapMetadataSyncService`/`KeenonMapImageSyncService`/`KeenonMapPointSyncService` need to call `GET /api/open/custom/robot/map` and `GET /api/open/custom/robot/map/position` — after live testing proved the previous source (`sceneCode` read off `GET /api/open/custom/clean/robot/status`) does not exist in that endpoint's response at all.

## 2–4. Demo Piece identity

| Field | Value |
|---|---|
| Sakar robot UUID | `27a66d14-bae7-4c9d-8810-ebcc262b0ba7` |
| Robot name | Demo Piece |
| Sakar product name | Sakar CleanBot 5000 Plus |
| Keenon model | C40 S |
| Keenon Store ID | `C00715655` |
| Keenon robotSn / robotId | `94:BA:06:CA:99:F3` |

## 5. Current scene

| Field | Value |
|---|---|
| sceneName | `F` |
| sceneCode | `7ClJPR` |

Live-verified via `GET /api/open/scene/v1/info/list?storeId=C00715655`, cross-checked against `GET /api/open/custom/robot/map(/position)?sceneCode=7ClJPR` both returning `code: 610000`. **This value is specific to Demo Piece only** — it is Sakar-owned, per-robot configuration data (see §16), never a global default, and must not be reused for any other robot.

## 6. Robot-specific area API

`GET /api/open/custom/clean/robot/area/list?storeId={storeId}&robotSn={robotSn}` — robot-specific, returns a vendor `mapId` (32-character hex, e.g. `4c0075859805496eb452187b3cd91107`) and `floor`. Live-verified stable across the entire investigation for Demo Piece. **Not used as `sceneCode`** — never confirmed interchangeable with the `sceneCode` parameter the map endpoints require (different shape: 32-char hex vs. 6-char mixed-case).

## 7. Map position API

`GET /api/open/custom/robot/map/position?sceneCode={sceneCode}&floorInfo={floorInfo}` — returns `data.targetList[]`, each entry carrying `name`, `type`, `positionX`/`Y`, and `mapMd5` (every point sharing one map reports the same `mapMd5`).

## 8. Map image API

`GET /api/open/custom/robot/map?sceneCode={sceneCode}&floorInfo={floorInfo}` — returns `data.content` (base64 PNG) and `data.originPosition` (`width`, `height`, `originX`, `originY`, `isDynamic`).

## 9. Verified mapMd5

`a3cb0d75faa17c9ab12b9a6434173b42` — read from `map/position`'s `targetList[].mapMd5` for `sceneCode=7ClJPR`.

## 10. Map dimensions

`originPosition.width = 570`, `originPosition.height = 763` — read from `map`'s `originPosition`, persisted on `RobotMap.width`/`height`.

## 11. Origin coordinates

`originPosition.originX = -15.899999618530273`, `originPosition.originY = -9.25` — observed live for `sceneCode=7ClJPR`. **Not currently persisted** — `RobotMap` deliberately has no `origin_x`/`origin_y` columns (see that entity's own Javadoc); this is documented here as evidence, not as a claim about stored data. Adding origin persistence is out of scope for this change (see §21).

## 12. Charging point

`1_Charging pile2` (`type: charge`) at `positionX: 255.92219426961137`, `positionY: 194.34652657507655` — from `map/position`'s `targetList[]` for `sceneCode=7ClJPR`.

## 13. Why `custom/clean/robot/status` cannot provide sceneCode

Live-captured response (pinned as `KeenonRobotAdapterTest.LIVE_CLEANING_STATUS_RESPONSE`, captured for this exact robot) contains only `hardwareState`, `globalState`, `childState`, `mainState`, `subState`, `robotSn` — **no `sceneCode`, `sceneName`, or `mapId` field at all**. The one endpoint that ever returned `sceneCode` (`GET /api/open/scene/v1/robot/status`) returns `610403` ("insufficient permission") for this account on C-series robots.

## 14. Why historical `dTW2N7` must not be hardcoded

`dTW2N7` was Demo Piece's sceneCode at one earlier point (recorded in the archived `SAKAR_KEENON_C40S_LIVE_API_TESTING_REFERENCE.pdf` test-environment table, alongside the same stable `mapId`). It is absent from the current store scene list (`GET scene/v1/info/list`) and was never re-tested against the map endpoints. Between then and the current `7ClJPR`, this robot's sceneCode changed at least once — proof that sceneCode is not stable over time and must never be a compiled-in constant.

## 15. Why mapId and mapMd5 must remain separate concepts

- `mapId` (e.g. `4c0075859805496eb452187b3cd91107`) — a robot's cleaning-area binding, from `custom/clean/robot/area/list`. Never confirmed accepted as a `sceneCode` value.
- `mapMd5` (e.g. `a3cb0d75faa17c9ab12b9a6434173b42`) — a content hash of the currently-loaded map image/points, from `map/position`'s `targetList[].mapMd5`. Used purely for change detection (`KeenonMapImageSyncService`already did this before this change and is unmodified here).

These are unrelated identifiers from unrelated Keenon subsystems that happen to both be hex strings — never conflated in code.

## 16. Sakar Cloud synchronization flow

```
robotId (Sakar)
  -> KeenonRobotSceneConfig.findByRobotId   (Sakar-owned config; PUT /api/v1/robots/{id}/keenon/scene-config)
  -> sceneCode, sceneName
  -> KeenonMapMetadataSyncService.sync      (writes RobotMap.vendorMapId/name)
  -> KeenonMapImageSyncService.sync         (GET map/position for mapMd5, GET map for PNG; writes width/height/mapMd5/imageUrl)
  -> KeenonMapPointSyncService.sync         (GET map/position for targetList[]; writes MapPoint rows)
```

A manual on-demand trigger, `POST /api/v1/robots/{id}/keenon/map/sync`, was added alongside this change (mirroring the existing area/cleaning-history sync pattern) since no prior endpoint could sync map metadata/image on demand — only the disabled-by-default scheduler could.

## 17. Failure handling

Unchanged from the existing, already-tested behavior: a vendor call failure or validation failure (bad base64, non-PNG bytes) leaves the existing `RobotMap` row and stored PNG file completely untouched — never a partial write. A robot with no `KeenonRobotSceneConfig` row resolves to "no map synced" (`Optional.empty()`), the same terminal state as "vendor reported no scene" did before this change — never a fabricated fallback.

## 18. Security considerations

- `KeenonRobotSceneConfig` write access is gated on `ROBOT_CONFIGURE`; read access on `ROBOT_VIEW` — same RBAC pattern as every other Keenon admin action.
- Tenant scoping is enforced identically via `RobotService.getAccessibleOrThrow` (`ROBOT_NOT_FOUND`, never `403`, for both nonexistent and cross-tenant robots).
- No vendor credential, access token, or client secret is stored, logged, or returned by any new endpoint.
- Map image storage paths remain built exclusively from Sakar-owned UUIDs (`robotId`/`mapId`) — no vendor string (`sceneCode` included) ever participates in filesystem path construction.

## 19. Read-only live verification evidence

All of the following were live-verified via direct, read-only `GET` requests run by the operator (never executed by an automated agent, and no credential was ever exposed in any tool output during this investigation):

| Call | Result |
|---|---|
| `GET custom/clean/robot/area/list` | `code: 610000`, `mapId: 4c0075859805496eb452187b3cd91107`, `floor: 1` |
| `GET scene/v1/info/list?storeId=C00715655` | 4 current scenes; `7ClJPR`/`F` identified as Demo Piece's |
| `GET custom/robot/map/position?sceneCode=7ClJPR&floorInfo=1` | `code: 610000`, `mapMd5: a3cb0d75faa17c9ab12b9a6434173b42` |
| `GET custom/robot/map?sceneCode=7ClJPR&floorInfo=1` | `code: 610000`, valid PNG (`iVBORw0KGgo...`), `width: 570`, `height: 763` |
| `GET custom/robot/map(/position)?sceneCode=YLlqC0` | both `610403` — confirmed dead, never used |

The exact same values are asserted in `KeenonMapSyncControllerTest`/`KeenonMapImageSyncServiceTest`/`KeenonMapPointSyncServiceTest` (full Spring context, real security/tenant/persistence layers, only `KeenonApiClient` mocked with these live-evidenced response bodies) as this change's automated regression coverage.

## 20. Known limitations

- The current C40 S `custom/clean/robot/status` endpoint does not return any scene/map identity field — confirmed by direct inspection of a real captured response.
- `sceneCode` therefore must come from Sakar-controlled, per-robot configuration (`KeenonRobotSceneConfig`) unless/until Keenon exposes a documented, authoritative robot → scene endpoint.
- `originX`/`originY` are live-evidenced (§11) but not yet persisted anywhere in the schema — a future slice's concern, not addressed here.
- Nothing in this change re-enables the `map-metadata-sync`/`map-image-sync`/`map-point-sync` schedulers (all remain disabled by default) — configuring a robot's scene only makes the manual trigger and the schedulers (if enabled) resolve correctly; it does not itself trigger anything.

## 21. Future improvement

If Keenon documents or exposes an authoritative robot → scene endpoint (or confirms `mapId` is accepted as a `sceneCode` value), integrate that instead of relying on manually configured `sceneCode` — `KeenonRobotSceneConfig` can then become a cache/override rather than the sole source of truth, without changing its schema or the downstream sync services that consume `KeenonMapMetadataSyncService`'s output.
