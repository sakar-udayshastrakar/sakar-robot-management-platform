# Keenon Mapping — Deep Reverse Engineering

**Phase 4 audit. Read-only evidence gathering. No application code, UI, or mock SLAM/map data was
created to produce this document.**

All evidence below was extracted by directly opening decompiled source files under:

```
C:\Users\sakar\Downloads\APK-Reverse-Engineering-Suite-main 2\APK-Reverse-Engineering-Suite-main\
  APK-Reverse-Engineering-Suite-main\output\C40_S_LS_M014C00_RW_F00_V246\
```

Every claim is tagged `EVIDENCE:` with the source file (and class/method where applicable).
Anything not directly observed is explicitly marked **[NOT CONFIRMED]** or **[INFERRED — reasoning
given]**. Chinese source comments are translated inline in quotes.

---

## 1. Mapping application architecture (end-to-end, confirmed)

```
robot-installation-assistant-v4.11.0  (com.keenon.peanut.peanutservice)
  └─ SimpleChromeActivity (Chromium WebView shell, launcher activity)
       ├─ starts AndService (embedded HTTP server, AndServer library, port 8080)
       │     └─ serves assets/ros/dist/ (built Vue 3 SPA) at http://localhost:8080/
       ├─ starts WebServerManager → WebServerSocket (org.java_websocket server, port 8888)
       │     └─ JS connects here for the "android.*" bridge AND as a relay to ROS
       ├─ starts WebLogServerManager (port 8899) — structured app/mapping logging channel
       └─ starts RosSocketClientManager
             └─ opens 2 outbound WebSocket client connections to the on-robot compute node:
                   ws://192.168.64.20:9090  (rosbridge_websocket — primary ROS topics/services)
                   ws://192.168.64.20:9091  (a second, Keenon-custom ROS bridge instance)

Browser tab (assets/ros/dist/, Vue 3 SPA)
  ├─ single effective route "/install"; all screens are Vue components/state inside it
  ├─ RosClass (wraps roslib.js Client) ↔ ws://<hostname>:8888?port=PC9090 / ?port=PC9091
  │     (WebServerSocket relays these straight through to RosSocketClientManager → 192.168.64.20)
  ├─ "android.*" bridge object ↔ ws://<hostname>:8888?port=APP8888
  │     (handled natively by WebSocketHelp, NOT a Java @JavascriptInterface — see §6)
  ├─ useGlMapRender() — WebGL (THREE.js) renderer for the occupancy-grid map image
  ├─ zrender — 2D canvas overlay renderer (virtual walls, gates, elevators, VSLAM points)
  └─ axios/HTTP — used only for (a) http://localhost:8080/video/play (local media) and
        (b) cloud map-file upload/download endpoints (region-specific consoles), not for
        map/pose/elevator/gate/label CRUD, which goes over the 8888 WebSocket instead.
```

**EVIDENCE:**
- APK: `robot-installation-assistant-v4.11.0` · CLASS:
  `org.chromium.chrome.browser.keenon.SimpleChromeActivity` · METHOD: `showWebView()`,
  `startWebSocket()`, `initData()` — quoted in full in the source trace below (§2, §3, §6).
- APK: same · CLASS: `com.keenon.peanut.peanutservice.server.http.Service.AndService` — field
  `PORT = 8080`.
- APK: same · CLASS: `server.webSocket.server.WebServerSocket` — field `PORT = 8888`.
- APK: same · CLASS: `server.webSocket.client.RosSocketClientManager` — field
  `ADDRESS = "ws://192.168.64.20:"`, ports `9090`/`9091`.
- FILE: `assets/ros/dist/index.html` and bundle `assets/index-32d09e94.js` /
  `assets/Home-0ebbc05a.js` (Vue 3 + Vite build output).

---

## 2. Exact APK/package ownership

Confirmed in Phase 3 and re-verified here: the mapping feature belongs entirely to
**`com.keenon.peanut.peanutservice`** ("Robot Installation Assistant", v4.11.0). The cleaning app
(`com.keenon.peanut.clean`) contains SLAM-*adjacent* classes (see §26) but does not own the
mapping UI, the WebView, the WebSocket bridge, or `Mapping.db`.

**EVIDENCE:** package name and app name from
`robot-installation-assistant-v4.11.0/reports/Manifest.md` (Phase 3 finding, re-confirmed);
`AppDatabase.java` and `SlamApplication.java` both live under
`com/keenon/peanut/peanutservice/...` in this APK only (§15, §16).

---

## 3. Mapping UI route

**Finding: there is no per-feature route.** The SPA's router table has exactly two path entries:

```js
[{path:"",redirect:t=>({path:"/install"})},
 {path:"/install",name:"Home",component:()=>import("./Home-0ebbc05a.js")},
 {path:"/:currentPath(.*)*",redirect:t=>({path:"/install"})}]
```

Every URL, including a 404 catch-all, redirects to `/install`. Mapping, cleaning-zone editing,
virtual walls, elevators, etc. are **not separate routes** — they are internal Vue
component/state transitions within the single `Home` component tree (a 5,434-line bundled file,
`Home-0ebbc05a.js`).

**EVIDENCE:** FILE: `assets/index-32d09e94.js` (router table, quoted above). Native side confirms
the same design — `SimpleChromeActivity` builds the loaded URL as
`pageUrl + "#/install?time=...&env=production&rosVersion=...&isCloud3d=...&modifyPoint=..."`
(query params carry state into the single route, not a different path per feature).

---

## 4. JS components

- `Home-0ebbc05a.js` (2.9 MB) — contains essentially all feature logic: mapping/SLAM UI, cleaning
  zones, virtual walls, elevators/gates, the robot state store, the `RosClass` ROS client, the
  `android` bridge call sites, and the map renderer composables (`useGlMapRender`,
  `useMapping`, `useRosConnect`, `useAndroidMsg`, `useSceneStore`).
- `index-32d09e94.js` (4.2 MB) — app shell: router, i18n, the `ROSNAME`→ROS-topic/service lookup
  table, the WebSocket wrapper classes, axios setup.
- `worker-6b29a5b0.js` — a Web Worker bundling lodash (general utility offloading, not
  mapping-specific).
- `de-8247df91.js`, `fr-387b5b08.js` — locale strings only.

**EVIDENCE:** file sizes/line counts and content summaries as reported by the research agent
after directly opening each file under `assets/ros/dist/assets/` (apktool copy).

---

## 5. JavaScript API calls (mapping-relevant, confirmed present)

| Symbol found | Where | Meaning |
|---|---|---|
| `ROSNAME.BUILDMAPTYPE` | `index-32d09e94.js` config table | A ROS **service** call (`f.value.service(ROSNAME.BUILDMAPTYPE,...)`) selecting build-map type |
| `setBuildMapType(pt.data)` | `Home-0ebbc05a.js` | `pt.data` observed as `"dynamic"` → `updateRosName("CONFIGMAP",...)` |
| `ROSNAME.CONFIGINITPOSE` | both bundles | Service; payload `option:"select"\|"delete"`, `initPose:...` |
| `ROSNAME.VIRTUALWALLAUTOSAVE`, `MAPTYPE.VIRTUALWALL` | `Home-0ebbc05a.js` | Virtual-wall layer type + autosave service |
| `getElevatorConfig`, `getElevatorList`, `ROSNAME.ELEVATORCONFIG` | `Home-0ebbc05a.js` | Service; `option:"select"\|"update"`, payload `{elevator:[{elevator_id,robot_id}]}` |
| `GATE_GROUP_IDX`, `addGateList`, layer arrays incl. `"gate"` | `Home-0ebbc05a.js` | Gate render/config |
| `currentFloor`, `getFloorParam`, `whichFloorSwitched`, `selectMapByFloorAndType`, `deleteDynamicMapInfo` | `Home-0ebbc05a.js` | Floor/multi-floor handling |
| `VSLAM_GROUP_IDX`, `useGlMapRender`, `getVSlamRobot`, `me("vslam_available")`, `CameraType.MxStereo` | `Home-0ebbc05a.js` | Camera-based Visual SLAM support and rendering |
| `"map_saveRecord"` icon key, `clean.stopRecord` | both bundles | UI affordance for stopping a mapping/route "recording" session — see §12 for why this matters |

**Explicitly searched for and NOT found** (literal identifiers): `BUILD_MAP` (upper snake case),
`build_map`, `startMapping`/`start_mapping`/`StartMapping`, `createMap`/`create_map`,
`operatingMode`, `mapFinish`/`map_finish`, `occupancyGrid`/`occupancy_grid` (as an identifier —
the actual grid metadata lives in the `dynamic_map_info` DB table, see §15),
`robotPose`/`robot_pose` (position is read via `getRobotLocalPosition`/`getRobotPosition`
instead), `relocalization`, `restrictedArea`/`restricted_area`, `cleaningZone`/`cleaning_zone`
(camelCase form absent; a `cleanZoneTypes` import exists instead), `multiFloor` (literal).

**Parameter/message-type evidence** — the `ROSNAME` table maps logical names to real ROS
topic/service names and message types, e.g.:
```
CONFIGINITPOSE:["/database/config_init_pose","keenon_database_msgs/initPoseConfig",""]
```
confirming a **custom Keenon ROS message package** (`keenon_database_msgs`), not stock ROS
navigation messages.

**EVIDENCE:** all rows quoted verbatim from `assets/index-32d09e94.js` / `assets/Home-0ebbc05a.js`
by the research agent; "not found" claims are from explicit grep passes over both files.

---

## 6. WebView bridge

**Finding: there is no `addJavascriptInterface`-based bridge at all.** A full search of the
`jadx/.../sources/` tree for `addJavascriptInterface` returned zero files; the JS bundles have no
`window.android`, `window.Android`, `AndroidBridge`, `webkit.messageHandlers`, or `jsBridge`
references either.

Instead, the bridge is a **home-grown WebSocket RPC protocol**:

```js
// index-32d09e94.js — action-map table generating the "android.*" JS object
getRobotLockState:{wsType:"service",name:"ws:getRobotLockState",action:"getRobotLockState",
                    actionType:"push",requestType:"appPush"},
...
Qjt=t=>(e,r)=>{
  if(t.wsType==="subscribe"||t.wsType==="unsubscribe") v5[t.wsType]({action:t.action,...},r);
  else return v5 && v5[t.wsType]({action:t.action,actionType:t.actionType,
                                   requestType:t.requestType,data:e})
}
```
`v5` is `new Zkt({url:"ws://"+hostname+":8888"})`. Calling e.g. `android.postDBOperation(...)`
from JS = sending a JSON `{action,actionType,requestType,data}` frame over this one WebSocket.

Native side receives it in **`WebServerManager.dispatchMessage()`**:
```java
public void lambda$dispatchMessage$2$WebServerManager(String str) {
    JSONObject parseObject = JSON.parseObject(str);
    String id = parseObject.getString("id");
    String requestType = parseObject.getString("requestType");
    if (id == null || id.isEmpty()) {
        if (requestType == null || requestType.isEmpty())
            EventBus.getDefault().post(JSON.parseObject(str));
        else
            WebSocketHelp.getINS().onHandleMessage(str);   // "android.*" bridge calls land here
    } else {
        RosSocketClientManager.getINS().send(str);          // has "id" → real rosbridge traffic
    }
}
```
This single dispatcher is the fork point between "this is a bridge/DB call" (no `id`, routed to
`WebSocketHelp`) and "this is raw rosbridge protocol traffic" (has `id`, forwarded unmodified to
the real ROS WebSocket at `192.168.64.20`).

**EVIDENCE:** FILE: `assets/index-32d09e94.js` (JS action-map, quoted); CLASS:
`server.webSocket.manager.WebServerManager` · METHOD: `dispatchMessage` (native dispatcher,
quoted); `addJavascriptInterface` grep across all 5 apps' `jadx` trees: zero hits.

---

## 7. Android services

| Service | Class | Role |
|---|---|---|
| Embedded HTTP server | `server.http.Service.AndService` (port 8080, AndServer library) | Serves `assets/ros/dist/` and a small set of file/video/pic/login routes — **no map CRUD routes** (see §5/§15) |
| WebSocket bridge+relay server | `server.webSocket.server.WebServerSocket` (extends `org.java_websocket.server.WebSocketServer`, port 8888) | Accepts browser-tab connections, tags them by `port=PCxxxx`/`APP8888` query param |
| WebSocket dispatcher | `server.webSocket.manager.WebServerManager` (singleton) | Routes to `WebSocketHelp` (bridge/DB) or `RosSocketClientManager` (rosbridge relay) per §6 |
| Outbound ROS client | `server.webSocket.client.RosSocketClientManager` | Maintains 2 client WebSocket connections to `192.168.64.20:9090`/`:9091` |
| Structured logging channel | `WebLogServerManager` / `SocketServerManager.LOG_PORT` (port 8899) | Logging channel explicitly tagged `"mapping-app"` in the JS |
| Wi-Fi hotspot | `service.WifiHotpotService` | Started/stopped around commissioning; body not fully traced (ancillary, not on the mapping control path) |
| File upload | `service.UploadService` | Started at app init; ancillary (map/file transfer support) |
| Broadcast receiver | `receiver.APPReceiver` (**exported=true**) | Declares 3 mapping-relevant broadcast actions any app can send: `android.peanut.action.MAPPING_RESTART_ROS`, `...MAPPING_RESTART_ROS_SUC`, `...MAP_DOWNLOAD_START`, plus `com.keenon.action.DISCONNECT_ROBOT` |

**EVIDENCE:** class names, port constants, and the `APPReceiver` intent-filter block all quoted
directly from `apktool/.../AndroidManifest.xml` and the corresponding `jadx` Java sources for
`robot-installation-assistant-v4.11.0`.

---

## 8. Robot-side service

The actual SLAM/occupancy-grid computation happens **off the Android device entirely**, on a
separate onboard Linux/ROS compute node at fixed LAN address **`192.168.64.20`**
(`HubConstant.LINUX_IP` in the decompiled source), running standard **rosbridge_suite**
(`rosbridge_websocket`) on port 9090 plus a second, Keenon-customized bridge instance on port
9091 handling specific services/topics:
```java
public static final String[] service_91 = {"/switch_dest_floor_map", "/republish_tfs"};
public static final String[] topic_91   = {"/scan_base_map", "/motor_lock"};
public static final String[] RECEIVE_TOPIC_91 = {"/tf2_web_republishe"};
```
No SLAM/ROS/LiDAR native library (e.g. cartographer, rtabmap) is bundled inside the
`robot-installation-assistant` APK itself — only `libkeenon_serial.so` (custom serial, not SLAM)
and ARCore libraries (`libarcore_sdk_c.so`, `libarcore_sdk_jni.so`, `libgvr.so`, for the VSLAM/
camera-pose path noted in §5). This is direct, positive confirmation that the Android app is a
**relay and UI/DB layer**, not the SLAM computation itself.

**EVIDENCE:** CLASS: `server.webSocket.client.RosSocketClientManager` (address/port
constants and topic/service arrays, quoted); native-library listing from
`extraction/.../lib/` for this APK.

---

## 9. SLAM communication (protocol detail)

Standard **rosbridge protocol** (JSON messages with `op`, `topic`/`service`, `id` fields) is used
end-to-end for the ROS-facing half of the bridge:

- Browser: `RosClass` wraps `roslib.js`'s `Client`, e.g.
  `this.ros=new Client({url:this.config.rosIp})` with fallback
  `rosIp="ws://192.168.64.20:9090"`, `rosCtrlIp="ws://192.168.64.20:9091"`.
- Runtime (on-device) connection actually made by the WebView is
  `ws://${window.location.hostname}:8888?port=PC9090` /
  `ws://${window.location.hostname}:8888?port=PC9091"` — i.e. **the browser never talks to
  192.168.64.20 directly**; it talks to the local relay (port 8888) which is bridged by
  `RosSocketClientManager` to the real robot ROS box.
- `WebServerManager.dispatchMessage()` (quoted in §6) forwards any frame carrying an `id` field
  straight to `RosSocketClientManager.getINS().send(str)` — i.e. rosbridge `call_service`/
  `subscribe`/`publish` JSON frames pass through largely unmodified.

**EVIDENCE:** as quoted in §6, §8; `roslib.js`/`Client` identified in `Home-0ebbc05a.js`
(`class RosClass{...new Client({url:...})}`).

---

## 10. Live map data flow

- **Occupancy-grid image**: rendered client-side via a custom **WebGL (THREE.js-based)**
  composable, `useGlMapRender()`, exposing coordinate-transform helpers
  `rosPositionToPixelPosition` / `rosPositionToPixelWith`. The grid's metadata (resolution, width,
  height, origin pose) is persisted in the local `dynamic_map_info` table (see §15) — a shape
  matching ROS's `nav_msgs/OccupancyGrid` `MapMetaData` (resolution/width/height/origin) even
  though no class is literally named `OccupancyGrid`.
- **Vector overlays** (virtual walls, gates, elevators, VSLAM point markers): rendered via bundled
  **zrender** (2D canvas), e.g. VSLAM points are drawn as literal 2.5px circles:
  `new zrenderExports.Circle({shape:{x:...,y:...,width:2.5,height:2.5}})`.
- **Update frequency**: **[NOT CONFIRMED]** — no explicit polling interval or ROS topic
  publish-rate configuration was found in the JS in the time available; this would require tracing
  the specific `subscribe` call parameters for `/scan_base_map` etc., not done in this pass.
- **Coordinate system / transform**: confirmed to be ROS-standard (topic name
  `/tf2_web_republishe`, i.e. the standard `tf2_web_republisher` ROS package used specifically to
  bridge TF transform trees to web clients) — meaning the app relies on ROS's own TF tree for
  coordinate transforms rather than reimplementing transform math client-side.

**EVIDENCE:** `useGlMapRender`/zrender usage quoted from `Home-0ebbc05a.js`; `dynamic_map_info`
schema from `database/tables/DynamicMapInfo.java` (§15); `/tf2_web_republishe` topic name from
`RosSocketClientManager.RECEIVE_TOPIC_91`.

---

## 11. Robot pose data flow

- Pose values are read via JS functions `getRobotLocalPosition`/`getRobotPosition` (not a
  `robotPose` object) — **[NOT FULLY TRACED]** exactly which ROS topic backs these calls was not
  confirmed in this pass beyond the general `/scan_base_map`/rosbridge topic surface in §8.
- Persisted pose data uses a rich schema (table `pose` — `position_x/y/z`,
  `orientation_x/y/z/w`, i.e. a full 3D position + quaternion, standard ROS `geometry_msgs/Pose`
  shape) plus Keenon-specific fields (`elevator_id`, `macAddress`, `disableOrientation`,
  `phoneStr`) — see §15.
- Initial-pose / relocalization: `ROSNAME.CONFIGINITPOSE` service (`/database/config_init_pose`,
  message type `keenon_database_msgs/initPoseConfig`) with `option:"select"|"delete"`; a
  dedicated `init_pose` table stores `init_method`, `angleRange`, `confidence`, `penetrate` plus
  position/orientation — `confidence`/`penetrate` strongly suggest an AMCL-style
  particle-filter localization confidence score, i.e. **[INFERRED]** standard ROS AMCL or a
  close derivative, not confirmed by reading AMCL source directly (not present in any decompiled
  Android APK — it would run entirely on the `192.168.64.20` Linux box, outside the scope of
  what's decompilable here).
- **Source**: confirmed to be the ROS bridge (§8/§9), not the Peanut SDK, not a separate HTTP/MQTT
  path, and not another documented robot API.

**EVIDENCE:** `ROSNAME.CONFIGINITPOSE` quoted in §5; `pose`/`init_pose` table schemas in §15
(`database/tables/Pose.java`, `InitPose.java`).

---

## 12. Mapping start flow

**[PARTIALLY CONFIRMED — exact trigger call not isolated to a single named function]**. What is
confirmed:
- A build-map **mode selection** exists client-side: `setBuildMapType(pt.data)` with observed
  value `"dynamic"`, which calls `updateRosName("CONFIGMAP",...)` — i.e. selecting a build-map
  type reconfigures which ROS name/topic is active for the "CONFIGMAP" channel.
- A `ROSNAME.BUILDMAPTYPE` **rosbridge service call** exists (`f.value.service(ROSNAME.BUILDMAPTYPE,Qe)`)
  — the actual "start building this type of map" action is a ROS service call, not a
  local/native Android API call.
- The UI affordance found for ending a mapping session is a **"stop record"** button
  (`clean.stopRecord`, icon `map_saveRecord`) — this wording ("record"/"recording"), combined with
  `peanut-clean`'s own `CruiseConfigActivity`/`RouteConfigActivity`/`TeachPathActivity` family
  (Phase 3 finding), suggests Keenon's own mental model treats "building a map" as **recording a
  driven/pushed route** while SLAM runs, conceptually adjacent to (but a materially bigger, richer
  feature than) what `SakarC40Agent`/the reference screenshots call "Teaching Mode." **[INFERRED
  — not confirmed by a direct code path linking TeachPathActivity to BuildMapLocalState.]**
- Native-side progress/completion of a build-map operation is pushed back to the browser via
  `WebServerManager.update(BuildMapLocalState)` (an `@Subscribe` EventBus handler), which packages
  `{status, code, action, ...}` and calls `SendMessageToAll(...)` over the 8888 WebSocket to every
  connected browser tab.

**EVIDENCE:** `setBuildMapType`/`ROSNAME.BUILDMAPTYPE`/`clean.stopRecord`/`map_saveRecord` quoted
from the JS bundles (§5); `BuildMapLocalState` fields
(`action, actionType, buildState, code, currentTime, data, sessionId, status`) and the
`WebServerManager.update()` EventBus subscriber quoted from
`model/bean/BuildMapLocalState.java` and `WebServerManager.java`.

---

## 13. Mapping finish flow

Symmetric to §12: a finished/updated build-map state is communicated the same way — native
`BuildMapLocalState` event → `WebServerManager.update()` → JSON push over WebSocket 8888 to all
connected clients. Persistence of the finished map's data (name, type, floor, value, MD5, floor
info, building info) is a `postDBOperation` write to the `map` table (see §15) — **[INFERRED]**
this is a reasonable, but not individually re-traced, corollary of the generic `postDBOperation`
CRUD path already confirmed for reads/writes to this table; no single "on map finished, write X"
call site was isolated line-by-line in this pass.

**EVIDENCE:** as in §12 (`BuildMapLocalState`, `WebServerManager`); `map` table schema §15.

---

## 14. Map persistence

Confirmed local, on-device, Room/SQLite persistence — see full schema in §15/§16. No cloud call
is part of the write path for map/pose/elevator/gate/label/init_pose/dynamic_map_info data itself.

**EVIDENCE:** §15, §16, §21.

---

## 15. Mapping.db write flow

```
JS (assets/ros/dist/) --[ws://<host>:8888, action:"postDBOperation"]-->
  WebSocketHelp.onHandleMessage() [§6] -->
  InformationSupport (native) -->
  DataBaseManager.OperationDB(WebRequestBean, data) -->
  FactoryDao.createDao(tableNameString)  // switch on "map"/"pose"/"elevator"/"gate"/... -->
  <Table>DaoHelp.requestDB(action, table, itemsJson)
      // action ∈ {insert, update, delete, select, selectAll, deleteAll}
      (DBConstant.OPERATION_INSERT/UPDATE/DELETE/SELECT/SELECT_ALL/DELETE_ALL) -->
  Room DAO (generated *Dao_Impl.java) -->
  Mapping.db  (SQLite, at /sdcard/peanutservice/DBfile/Mapping.db)
```
**Who writes each table**: all 7 tables are written through this **single, generic dispatch
path** — there is no per-table bespoke write pipeline; `FactoryDao` is a switch statement mapping
a table-name string to the matching `*DaoHelp` class (`MapDaoHelp`, `PoseDaoHelp`,
`ElevatorsDaoHelp`, `GateDaoHelp`, `InitPoseDaoHelp`, `LableDaoHelp`, `DynamicMapInfoDaoHelp`),
each implementing the same `insert/update/delete/select/selectAll/deleteAll` contract
(`BaseDaoHelp.requestDB`). **When** each table is written is therefore driven entirely by *when
the JS front-end sends a `postDBOperation` message naming that table* — a UI/JS-side decision,
not a native-side trigger, for every table except `dynamic_map_info` (which also has a
`deleteDynamicMapInfo` JS-side helper noted in §5, consistent with the same pattern).

**EVIDENCE:** CLASS: `database.help.FactoryDao` · METHOD: `createDao(String table)` (switch
statement, confirmed by direct read); CLASS: `database.DataBaseManager` · METHOD: `OperationDB`;
CLASS: `server.webSocket.help.WebSocketHelp` — `if (action.equals(HubConstant.POST_DB_OPERATION))`
at the line the research agent cited as `WebSocketHelp.java:301`; CLASS:
`database.dao.AppDatabase` (Room database declaration, `DB_NAME = "Mapping.db"`).

---

## 16. Map file format

The `map` table's `value` column (type `String`) is the map's serialized payload; `map_md5` is a
checksum field alongside it. **[NOT CONFIRMED]** the exact serialization format inside `value`
(e.g. base64-encoded PNG, a custom binary-to-string encoding of an occupancy grid, or a
ROS `.pgm`/`.yaml` pair re-encoded as text) was not decoded in this pass — doing so would require
either capturing a live `value` payload from a running robot or finding the specific
encode/decode call sites in `Home-0ebbc05a.js`/`MapDaoHelp.java`, neither of which was completed
here. This is flagged as an open item rather than guessed.

**Full `map` table schema** (confirmed):
`_id (int), name, type, floor (int), value, map_md5, floorInfo, buildingInfo` (all `String` except
`_id`/`floor`), with real generated SQL
`INSERT OR REPLACE INTO map (_id,name,type,floor,value,map_md5,floorInfo,buildingInfo) VALUES ...`
and query patterns `SELECT * FROM map WHERE type = ? AND floor = ?` /
`SELECT * FROM map WHERE type == ?`.

**EVIDENCE:** CLASS: `database.tables.Map` (entity fields); CLASS: `MapDao_Impl.java`
(Room-generated SQL, quoted).

---

## 17. Multi-floor implementation

- `map` table has a `floor (int)` column and a compound-key query pattern
  (`type` + `floor`) — floors are a first-class dimension of every map row, not a separate
  hierarchy table.
- `dynamic_map_info` also has its own `floor (int)` column (per-floor grid metadata).
- `label` has `floor` too, but typed `float` (not `int`) — **[NOTE]** an inconsistency between
  `map.floor`/`dynamic_map_info.floor` (int) and `label.floor` (float), recorded as observed, not
  explained.
- Floor **switching** in the UI is handled by JS-side state/helpers: `currentFloor`,
  `getFloorParam`, `whichFloorSwitched`, `selectMapByFloorAndType` — i.e. floor switching is a
  client-side selection that re-queries the `map`/`dynamic_map_info` tables for the newly-selected
  floor's data, not a native/ROS-side floor-change operation.
- **Elevator's role in multi-floor**: the `elevator` table's `available_floor`/`transit_floor`
  string fields (format **[NOT CONFIRMED]** — likely a delimited floor-ID list, not decoded in
  this pass) associate an elevator with the floors it connects, which is how floor-to-floor
  robot navigation across elevators is modeled.

**EVIDENCE:** `database.tables.Map`/`DynamicMapInfo`/`Label`/`Elevators` field lists (§15/§21);
JS floor-handling symbol list from §5.

---

## 18. Virtual walls

- Confirmed as a **map layer type**, not a separate database table: `MAPTYPE.VIRTUALWALL`,
  distinguished into `"virtual_wall"` vs `"virtual_wall_auto"` variants in layer-type arrays
  (e.g. `["carto_map","virtual_wall","vel","gate","elevator","draw"]`), plus a dedicated
  `ROSNAME.VIRTUALWALLAUTOSAVE` **rosbridge service** for the auto-save variant.
- **[INFERRED]** virtual-wall geometry is therefore very likely persisted as a specially-typed
  row in the `map` table (using its `type` column to distinguish a virtual-wall layer from an
  occupancy-grid layer, both sharing the same `value`/`floor` shape) rather than a bespoke table —
  this is inferred from the layer-type-array pattern, not confirmed by seeing an explicit
  virtual-wall write call.

**EVIDENCE:** `MAPTYPE.VIRTUALWALL`, `ROSNAME.VIRTUALWALLAUTOSAVE`, and the layer-type array
literal, all quoted from `Home-0ebbc05a.js` in §5.

---

## 19. Restricted areas / cleaning zones

Only a `cleanZoneTypes` import was found (no `cleaningZone`/`cleaning_zone`/`restrictedArea`
identifiers exist literally). **[INCOMPLETE — not further traced]**: the research agent did not
open/expand this import to see its member values or the code paths that read/write zone
geometry. No dedicated `restricted_area` or `cleaning_zone` database table exists among the 7
confirmed `Mapping.db` tables (§15/§21) — **[INFERRED]** consistent with §18, restricted/cleaning
areas are most likely additional `MAPTYPE`-style layer values stored in the same generic `map`
table, but this was not directly confirmed.

**EVIDENCE:** `cleanZoneTypes` import cited by the research agent from `Home-0ebbc05a.js`; no
further trace performed in this pass (recorded as a gap, not a finding).

---

## 20. Initial pose

Covered fully in §11 (`ROSNAME.CONFIGINITPOSE`, `init_pose` table with `init_method`,
`angleRange`, `confidence`, `penetrate`, position/orientation).

---

## 21. Elevator / gate

**Two entirely separate elevator-related systems exist, and they are NOT the same thing:**

1. **Local, per-map elevator/gate config** — inside `robot-installation-assistant`'s
   `Mapping.db`:
   - `elevator` table: `_id, elevator_id (int), robot_id (int), available_floor (String),
     transit_floor (String), opendoor_time (int)`.
   - `gate` table: `_id, id (int), floor (int), mac_address (String)` — gates are identified by
     a **BLE/Wi-Fi MAC address**, i.e. physical beacon-based gate detection, not a ROS
     topic/geofence.
   - Also a **file-based** config path exists alongside the DB:
     `HubConstant.POST_ELEVATOR_CONFIG = "postElevatorConfig"`,
     `ELEVATOR_CONFIG_FILE_NAME = "elevatorConfig.json"`, stored at
     `{sdcard}/peanut/elevator_config/`.
2. **Cloud elevator-vendor IoT lookup** — inside **`peanut-clean`** (a different APK, see Phase 3),
   class `com.keenon.module.datasync.http.HttpElevatorApi`:
   ```java
   public interface HttpElevatorApi {
       @GET("/api/hotel/robot/search/iot/device/{deviceName}")
       Observable<BaseResp<ServerCheckInfo>> getCheckInfo(@Path("deviceName") String deviceName, @Query("deviceType") String deviceType);
       @POST("/api/elevator/v2/elevator/search/lift/configInfo")
       Observable<BaseResp<RemoteElevatorConfig>> getElevatorConfig(@Query("deviceName") String deviceName);
   }
   ```
   This is a **Retrofit interface calling a cloud REST API** to look up a specific building's
   elevator-vendor IoT integration (e.g. for hotel deployments with 3rd-party elevator control
   systems) — unrelated to the local `elevator`/`gate` tables used for the robot's own
   floor-transition navigation logic.

**EVIDENCE:** table schemas from `database/tables/Elevators.java`/`Gate.java`; `HttpElevatorApi`
interface quoted verbatim from `com/keenon/module/datasync/http/HttpElevatorApi.java` in
`peanut-clean`.

---

## 22. Offline requirements vs. cloud dependency

**Separated exactly as requested:**

**REQUIRED FOR MAPPING (fully local, no internet needed):**
- The entire Mapping.db read/write path (§15) — local SQLite, local WebSocket IPC only.
- The rosbridge control/data plane (§8/§9) — LAN-only, fixed IP `192.168.64.20`, no internet
  route involved.
- `AndService`'s local HTTP server (port 8080) binds with `inetAddress(null)`, i.e. **all
  interfaces, reachable over LAN but not requiring internet** — confirmed by
  `AndServer.serverBuilder(this).inetAddress(null).port(8080)` plus the app logging/broadcasting
  its own LAN IP via `NetUtils.getLocalIPAddress()`.
- `RetrofitFactory` (in `robot-installation-assistant`) has **no hardcoded base URL** — set only
  at runtime — and no map-specific cloud endpoint literal was found in this app at all.

**OPTIONAL CLOUD SYNC (confirmed present, but separate from the mapping data path above):**
- Region-specific cloud console URLs found in `Home-0ebbc05a.js`:
  ```js
  {cn:{development:"https://t-cloud.ikeenon.com", production:"https://console.peanut.keenonrobot.com"},
   jp:{...}, es:{development:"http://d-cloud.ikeenon.com",...}}
  ```
  used with `axios.put(...)` for map-file upload — i.e. **exporting/backing up a finished map to
  Keenon's cloud console is optional and separate from building/using the map locally.**
- The elevator-vendor IoT cloud lookup (§21) lives in a *different app* (`peanut-clean`) and is
  about vendor integration, not core mapping.

**Conclusion for this section:** mapping itself (build, view live, save, edit, multi-floor,
elevator/gate config) requires **only a local Wi-Fi/LAN connection to the robot's own onboard ROS
compute node** — no internet, no Keenon Cloud account, no cloud authentication. Cloud
connectivity is exercised only for optional map backup/sync and for elevator-vendor IoT lookups
in an unrelated app.

**EVIDENCE:** as cited across §8, §9, §15; cloud URL object and `axios.put` call quoted from
`Home-0ebbc05a.js`; `AndServer.serverBuilder(...).inetAddress(null)` quoted from `AndService.java`.

---

## 23. Public SDK dependency

See full comparison table in §26 and in `SAKAR_MAPPING_TECHNICAL_FEASIBILITY.md`. Headline
finding: the public `peanut-sdk-release.aar` (the SDK Sakar is licensed to use) exposes only a
**coarse map-file transfer surface** (`MapComponent`: `upload/uploadNew/uploadOpt/download/
downloadOpt/uploadObs/getMapInfo/sendDownloadInfo/sendMapDownLoadAction`; `MapManager`:
`onImportToRos()/onExportToAndroid()` for USB-based transfer) plus
`PeanutRuntime.getInstance().location()` for triggering (re)localization within an *existing*
map. It does **not** expose: SLAM start/stop, occupancy-grid streaming, laser-scan/pose
subscription, or any of the `map`/`pose`/`elevator`/`gate`/`label`/`init_pose`/
`dynamic_map_info` CRUD surface documented in §15/§21.

**EVIDENCE:** `PEANUT_SDK_C40_API_MATRIX.md`, `PEANUT_SDK_C40_TECHNICAL_STUDY.md`, and
`SakarC40Agent/COMPATIBILITY_REPORT.md` (all in the Sakar project, read directly), each
describing the `MapComponent`/`MapManager` classes as decompiled/AAR-verified.

---

## 24. Internal/private API dependency

Everything else described in this document — the rosbridge control plane, the `postDBOperation`
WebSocket protocol, the `Mapping.db` Room schema, the CoAP-based SLAM beans in `peanut-clean`
(`SlamActionAckBean`, `SlamReplyBean`, `VslamIdentifyImageApi`, `VslamPictureApi`) — is **internal,
undocumented, private inter-process protocol between Keenon's own two apps and the robot's
onboard compute node.** None of it is part of the licensed public SDK. This is the single most
load-bearing finding for feasibility (see Report 2).

---

## 25. AIDL / cross-app IPC surface (full inventory)

The complete set of `.aidl` files found across **all 5** decompiled apps (identical compiled
module bundled into `peanut-clean`, `robot-installation-assistant`, and `ServiceSystem`):

```
com/keenon/aidl/mqtt/IMqttAidlApi.aidl (+ 2 listener .aidl)
com/keenon/aidl/OnCmdResultCallback.aidl
com/keenon/bi/aidl/IBiManager.aidl
com/keenon/tts/aidl/ITtsAidlApi.aidl (+ listener .aidl)
com/keenon/voice/aidl/IVoiceAidlApi.aidl (+ 6 supporting .aidl)
```

**No AIDL file exists anywhere with "map", "slam", or "nav" in its name.** `krlog` and
`RemoteControl` ship no AIDL files at all. This positively confirms mapping/SLAM data is **not**
exposed via Binder/AIDL to any other app on the device — the only way another app (like
`peanut-clean`) participates in the SLAM domain is via the CoAP-based SDK beans noted above, and
via the exported broadcast actions on `APPReceiver` (§7: `MAPPING_RESTART_ROS`,
`MAPPING_RESTART_ROS_SUC`, `MAP_DOWNLOAD_START`), not via a queryable data API.

**EVIDENCE:** full `.aidl` file listing from a recursive search across all 5 apps'
`jadx/.../resources/` trees.

---

## 26. Robot-side service discovery — summary

| Component | Location | Role |
|---|---|---|
| rosbridge_websocket (port 9090) + custom bridge (port 9091) | On-robot Linux/ROS box, `192.168.64.20` | Real SLAM/nav ROS stack — **outside any decompilable APK** |
| `RosSocketClientManager` | `robot-installation-assistant` (Android) | Client-side relay to the above |
| `WebServerSocket`/`WebServerManager` (port 8888) | `robot-installation-assistant` (Android) | Local relay/dispatcher between browser and (a) rosbridge relay, (b) `WebSocketHelp`/DB bridge |
| `AndService` (port 8080) | `robot-installation-assistant` (Android) | Serves the web UI + minimal file/video/pic/login routes |
| `Mapping.db` (Room/SQLite) | `robot-installation-assistant` (Android), `/sdcard/peanutservice/DBfile/` | All map/pose/elevator/gate/label/init_pose/dynamic_map_info persistence |
| CoAP SLAM beans (`SlamActionAckBean`, `VslamIdentifyImageApi`, etc.) | `peanut-clean` (Android) | A second, narrower participation in the SLAM domain from the cleaning app itself, via the CoAP protocol also used by the licensed SDK's transport layer |
| ARCore (`libarcore_sdk_*.so`, `libgvr.so`) | `robot-installation-assistant` (Android) | Camera-based Visual SLAM (VSLAM) pose assistance |
| `libkeenon_serial.so` | both apps | Custom serial link to onboard hardware — not SLAM computation |

**No native SLAM library (cartographer, rtabmap, gmapping, etc.) exists inside any of the 5
decompiled APKs.** The actual occupancy-grid SLAM algorithm runs entirely on the separate,
non-Android Linux/ROS compute node, reached only via the rosbridge WebSocket protocol described
throughout this document.
