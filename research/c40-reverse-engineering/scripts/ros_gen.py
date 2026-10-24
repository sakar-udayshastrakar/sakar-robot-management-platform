import json, os, re, sys, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_roles import ROLES
from ros_findings import FINDINGS

SP = r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad'
OUT = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\partials'
TARGET_PREFIX = 'slam-ros-v2.10.9-c2-0-g58a75a7-app-update-package/'
T = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\slam-ros-v2.10.9-c2-0-g58a75a7-app-update-package'

debs = json.load(open(SP + '/debs.json', encoding='utf-8'))
deps = json.load(open(SP + '/an/deps.json'))
elf = json.load(open(SP + '/an/elf.json'))
topics = json.load(open(SP + '/an/topics.json'))
rtypes = json.load(open(SP + '/an/robot_types.json'))
inv = json.load(open(r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\inventory_base.json'))
sha = {a['relative_path']: a for a in inv['artifacts']}
manifest = json.load(open(T + '/manifest.json', encoding='utf-8'))
msgs_txt = open(SP + '/an/msgs.txt', encoding='utf-8').read().splitlines()

def short(n): return n.replace('ros-indigo-', '')

# ---- per deb data
rows = []
for k, v in debs.items():
    fn = os.path.basename(k)
    f = {}
    for line in v['control'].splitlines():
        m = re.match(r'^([A-Za-z-]+):\s*(.*)', line)
        if m: f[m.group(1)] = m.group(2)
    nm = fn.split('_')[0]
    files = [x for x in v['files'] if x['type'] != 'dir']
    cnt = collections.Counter(os.path.splitext(x['path'])[1] for x in files)
    elfs = [e for e in elf if e['deb'] == nm]
    exes = [e for e in elfs if e['type'] == 'EXEC' and e['machine'] == 'ARM' and e['stripped']]
    sub = k.replace('\\', '/')
    sub = sub[sub.index('slam-ros-v2.10.9-c2-0-g58a75a7/'):] if 'slam-ros-v2.10.9-c2-0-g58a75a7/' in sub else sub
    rows.append(dict(fn=fn, nm=nm, sn=short(f['Package']) if f['Package'] != 'build' else 'build', pkg=f['Package'], ver=f['Version'], size=f.get('Installed-Size', ''),
                     maint=re.sub(r'\s*<.*>', '', f.get('Maintainer', '')), nfiles=len(files), cnt=cnt, nelf=len(elfs), nexe=len(exes), rel=k, files=files,
                     scripts=list(v['maintainer_scripts'].keys())))
# robot-settings / remote tools short name mapping
for r in rows:
    r['key'] = short(r['nm'])
rows.sort(key=lambda r: ('3rdparty' not in r['rel'].replace('\\', '/'), r['key']))
# the rel path in debs.json starts with 'slam-ros...-package/' already
def manifest_entry(fn):
    for e in manifest['files']:
        if e['name'] == fn: return e
    return None

# ================= findings json
os.makedirs(OUT, exist_ok=True)
ids = [f['id'] for f in FINDINGS]
assert len(ids) == len(set(ids))
json.dump(FINDINGS, open(OUT + '/ros_packages_findings.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

# ================= artifacts json
art = []
for r in rows:
    rel = r['rel'].replace('\\', '/')
    rp = rel[len(TARGET_PREFIX):] if rel.startswith(TARGET_PREFIX) else rel
    role = ROLES.get(r['key'], ('', ''))
    c = r['cnt']
    parts = []
    if r['nexe']: parts.append('%d stripped ARM executables' % r['nexe'])
    if r['nelf']: parts.append('%d ELF total' % r['nelf'])
    for ext, lab in (('.launch', 'launch'), ('.msg', 'msg'), ('.srv', 'srv'), ('.action', 'action'), ('.yaml', 'yaml')):
        if c.get(ext): parts.append('%d %s' % (c[ext], lab))
    if r['scripts']: parts.append('maintainer scripts: ' + ','.join(r['scripts']))
    me = manifest_entry(r['fn'])
    sh = sha.get(rel, {})
    art.append(dict(relative_path=rp, analyzed=True,
                    method='ar/tar extraction (Python stdlib); control + file list + maintainer scripts read; custom ELF header/symbol/DT_NEEDED parse; ASCII string scan; XML/YAML/msg parsing where applicable',
                    findings='%s | %s | Package=%s Version=%s files=%d | %s | manifest: type=%s dest=%r mode=%s' % (role[0], role[1], r['pkg'], r['ver'], r['nfiles'], '; '.join(parts) or 'no ELF/launch/msg content', me['type'] if me else '?', me['dest'] if me else '?', me['mode'] if me else '?'),
                    status='CONFIRMED (contents and metadata); behaviour of binaries NOT_ANALYZED',
                    limitations='No disassembly/decompilation or execution; role text is derived from control metadata, file lists, launch/config files and strings (INFERRED where not explicit); string scan excludes headers and large third-party libs (libpcl*, libboost*, libopencv*).',
                    sha256=sh.get('sha256'), size_bytes=sh.get('size_bytes'), file_type='deb (ar archive)'))
# non-deb artifacts
def rec(path, findings, method, status, lim):
    full = TARGET_PREFIX + path
    sh = sha.get(full, {})
    art.append(dict(relative_path=path, analyzed=True, method=method, findings=findings, status=status, limitations=lim, sha256=sh.get('sha256'), size_bytes=sh.get('size_bytes')))
rec('manifest.json', '110 file entries (keys: name/src/dest/mode/type only; no checksums/signatures): 103 type=ros (bin/, 755), 3 type=3rdparty (filters, robotviz, remote-tools; mode 644), bin/keenon type=bin twice (dest bin/ and absolute dest /etc/init.d/), script/app type=app, manifest.json type=manifest. All 106 debs listed and present; nothing on disk unlisted.', 'JSON parse; cross-check against files on disk', 'CONFIRMED', 'Meaning of type/dest fields is inferred from ros_update strings, not from decompiled code.')
rec('bin/keenon', 'POSIX sh stub (83 bytes): "cd /work/scripts; python guard.py &". Installed to /etc/init.d/keenon and bin/ per manifest. Launches guard.py supervisor which is NOT in this package.', 'Read full text', 'CONFIRMED', 'guard.py not available; supervised processes UNKNOWN.')
rec('script/app', 'Identical content to bin/keenon (83 bytes): "cd /work/scripts; python guard.py &"; manifest type=app dest script/ mode 755; script/upgrade.sh referenced by ros_update is absent.', 'Read full text', 'CONFIRMED', 'guard.py not available.')
assert len(art) == 109, len(art)
json.dump(art, open(OUT + '/ros_packages_artifacts.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

# ================= report
L = []
w = L.append
w('# ROS / SLAM package analysis - Keenon C40 `slam-ros-v2.10.9-c2-0-g58a75a7`')
w('')
w('Domain worker output (ROB/NET/DB/SEC/NAT). READ-ONLY static analysis of the ROS update package. Nothing was executed, no robot was contacted, no software installed. All topic/service/node/endpoint names below are **STATIC_REFERENCE** (found in launch files, YAML, message definitions or binary strings) - none is live-observed.')
w('')
w('Status labels: CONFIRMED, STRONGLY_SUPPORTED, INFERRED, HISTORICAL, UNKNOWN, NOT_ANALYZED, BLOCKED. Confidence: HIGH/MEDIUM/LOW. Secret values are redacted (type, location, length, first 2 characters at most). No decompilation was performed; binary findings come from ELF header/section/dynamic-symbol parsing and ASCII strings only.')
w('')
w('Machine-readable companions: `ros_packages_findings.json` (%d findings, IDs ROS-001..), `ros_packages_artifacts.json` (109 records).' % len(FINDINGS))
w('')
w('## 0. Executive summary')
w('')
for s in [
 'The package is the full ROS **Indigo / Ubuntu 14.04 (trusty) armhf** application stack of a multi-model Keenon platform: 106 debs (103 under `bin/`, 3 under `3rdparty/`), 7,879 files, 283 ELF files, plus `bin/keenon`, `script/app` (identical 83-byte stubs `cd /work/scripts; python guard.py &`) and `manifest.json` (ROS-001, ROS-012).',
 'A **C40 launch profile exists** (`keenon_robot_C40.launch`: cleaning group, free network lidar, GS2 rear lidar, 4 XD edge/low-obstacle line lasers, Sunny ToF, FDL cliff sensing, mono VSLAM, cartographer + peanut_localization + move_base/kplanner/TEB, task_manager/cover_planner) (ROS-002..006). The shipped default `keenon_robot.launch` is **W3**, so the active model on a unit is UNKNOWN from the package alone.',
 'Cleaning hardware is driven through an STM32 serial bridge (`chassis`): roll/wash/edge brushes with lift rods, suction fan, water pump with pulse control, dust box, carpet detection (ROS-006). ros_update can flash the MCU over serial (ROS-007).',
 'Network services found statically: rosbridge 9090/9091 (no auth, no TLS), ros_update OTA HTTP (port 9527 inferred), WsServer websocket + frp reverse SSH tunnel, CoAP bridge, Samba shares, Wi-Fi AP bridged into the robot LAN (ROS-030..034).',
 'Security highlights: hard-coded sudo password in `send_depth_node` and `WsServer` (same 11-char value as the default Wi-Fi AP passphrase), unauthenticated rosbridge/ROS control, unsigned debs + md5-only OTA with root maintainer scripts, client-side-only robotviz login, EOL stack (ROS-040..050).',
 'Gaps: `guard.py`, `script/upgrade.sh`, rosbridge_server/nginx/Samba, ORB vocabulary, STM32 firmware and all binary internals (NOT_ANALYZED) (ROS-016).']:
    w('- ' + s)
w('')

# ---- 1 inventory
w('## 1. Package inventory (106 debs)')
w('')
cat = collections.Counter(ROLES.get(r['key'], ('?',))[0] for r in rows)
w('Versions: 104 debs `2.3.1c1-4573-g1406f3b-trusty` (build 4573 of commit g1406f3b), `ros-indigo-filters 1.1.1`, `ros-indigo-robotviz 2.6.1c1v4-4-g8006fd8-trusty`. All Architecture=armhf. Maintainer fields are developer names/aliases (several `@todo.todo`). Role column is INFERRED from control text, file lists, launch/config and strings unless stated. "Manifest" = type/dest from `manifest.json`.')
w('')
w('| # | Package (control) | Deb location | Version | Category | Inst. KB | Files | ELF (exe) | Maintainer | Manifest | Maint. scripts | Role / notes |')
w('|---|---|---|---|---|---|---|---|---|---|---|---|')
for i, r in enumerate(rows, 1):
    me = manifest_entry(r['fn'])
    role = ROLES.get(r['key'], ('?', ''))
    loc = '3rdparty' if '3rdparty' in r['rel'].replace('\\', '/') else 'bin'
    w('| %d | `%s` | %s | %s | %s | %s | %d | %d (%d) | %s | %s | %s | %s |' % (i, r['pkg'] + (' (file: ' + r['nm'] + ')' if r['pkg'] != r['nm'] else ''), loc, r['ver'].replace('-trusty', ''), role[0], r['size'], r['nfiles'], r['nelf'], r['nexe'], r['maint'].replace('|', '/'), (me['type'] + ' ' + me['mode']) if me else '?', ','.join(r['scripts']) or '-', role[1].replace('|', '/')))
w('')
w('Totals by category: ' + '; '.join('%s: %d' % (k, v) for k, v in cat.most_common()) + '.')
w('')

# ---- 2 dependency graph
w('## 2. Dependency graph (from control `Depends`)')
w('')
names = {v['Package'] for v in deps['pk'].values()}
common = lambda n: short(n).endswith('-msgs') or short(n) in ('keenon-event-tracking', 'keenon-deps', 'keenon-common-cmake', 'slam-comm-lib', 'keenon-msgs')
internal = [(short(a), short(b)) for a, b, _ in deps['edges'] if b in names]
w('954 dependency edges in total; 333 are between packages of this set (EVIDENCE: control `Depends:` of each deb; `an/deps.json`). Edges to `keenon_*_msgs` / common libs are summarised in a table because drawing them would obscure the graph.')
w('')
w('```mermaid')
w('flowchart LR')
groups = {
 'Navigation core': ['costmap-2d', 'nav-core', 'voxel-grid', 'base-local-planner', 'navfn', 'kplanner', 'move-base', 'path-check', 'clear-static-recovery', 'rotation-escape-recovery', 'teb-local-planner', 'teb-local-planner-noetic', 'teb-clean-local-planner', 'costmap-converter', 'nav2-rotation-shim-controller', 'navigation', 'multi-goals-planner', 'tangent-planner', 'global-planner-user', 'pnc-core', 'cleanning-planner', 'demonstration-route-optimizer'],
 'Tasks and UI': ['task-manager', 'task-dispatcher', 'target-point-task', 'elevator-task', 'interactive', 'ros2cmd', 'libcoap', 'web-backend'],
 'SLAM and maps': ['gmapping', 'openslam-gmapping', 'slam-gmapping', 'map-server', 'map-tools', 'switch-map', 'virtual-wall-generator', 'peanut-localization'],
 'Sensors': ['astra-camera', 'astra-launch', 'fdl-ros', 'berxel-camera', 'sy-sdk', 'image-transport', 'compressed-image-transport', 'lidar-filter', 'filters', 'label-fusion', 'busy-map', 'cover-planner'],
}
placed = set()
for g, ns in groups.items():
    w('  subgraph "%s"' % g)
    for n in ns:
        nid = n.replace('-', '_')
        w('    %s["%s"]' % (nid, n)); placed.add(n)
    w('  end')
for a, b in sorted(set(internal)):
    if common(a) or common(b) or a not in placed or b not in placed: continue
    w('  %s --> %s' % (a.replace('-', '_'), b.replace('-', '_')))
w('```')
w('')
w('Packages not drawn (leaf/other): ' + ', '.join(sorted({short(v['Package']) for v in deps['pk'].values()} - placed - {n for n in {short(v['Package']) for v in deps['pk'].values()} if common('ros-indigo-' + n)})) + '. Packages with no `Depends:` line: robotviz, keenon-common-cmake, openslam-gmapping.')
w('')
w('### 2.1 Dependencies on common message / support packages')
w('')
by = collections.defaultdict(list)
for a, b in internal:
    if common(b) and not common(a): by[a].append(b)
w('| Package | Depends on keenon msgs / common libs |')
w('|---|---|')
for a in sorted(by):
    w('| %s | %s |' % (a, ', '.join(x.replace('keenon-', 'k-').replace('-msgs', '') if x.endswith('-msgs') else x for x in by[a])))
w('')
ext = collections.Counter()
for a, b, full in deps['edges']:
    if b not in names: ext[b] += 1
w('### 2.2 External (not shipped) dependencies - provided by the base image (STATIC_REFERENCE)')
w('')
w(', '.join('%s (%d)' % (n, c) for n, c in ext.most_common()) + '.')
w('')
w('Noteworthy external dependencies: `python-paramiko` (roslaunch), `openssl` (libcoap), `libsdl-image1.2-dev` (map-server), `libusb-1.0-0` (sdkeli), `ros-indigo-octomap-server` and `ros-indigo-robot-localization` (peanut-localization), `libpcl-1.7-all`, `ros-indigo-libg2o`, `ros-indigo-bfl`. rosbridge_server, rosapi, tf2_web_republisher, rviz, nginx and Samba are NOT declared dependencies of any deb (see gaps).')
w('')

# ---- 3 C40 / launch
w('## 3. Launch files, robot models and node composition (STATIC_REFERENCE)')
w('')
w('### 3.1 Model launch profiles (`robot_settings/launch/keenon_robot_<model>.launch` default args)')
w('')
w('| Launch | ROBOT_TYPE | GROUP | LIDAR (num) | MONO | USE_CAMERA/type | TOF | EDGE LASER | USE_CLEAN | SCHEDULE_SW |')
w('|---|---|---|---|---|---|---|---|---|---|')
for r in rtypes:
    w('| %s | %s | %s | %s (%s) | %s | %s/%s | %s/%s | %s/%s | %s | %s |' % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], r[12], r[13]))
w('')
w('Cleaning models in this package: C30, C30-1, C40, C40H, C55. `common_config.yaml` robotTypeList: C40, C40H, C55, C30, G1, G2, M1, M2, peanut, T1, T2, T2pro, T3, T5, T5plus, T5pro, T5Pro_pickup, T6, T8, T8Pro, T9, T9Pro, T10, T10Omni, T10S, W1, W3, S100, T11. C40 vs C40H: C40H has USE_EDGE_LASER=false and no USE_LASER_BACK. C55 adds side cameras (berxel, USE_SIDE_CAMERA=true) and USE_CAMERA=true.')
w('')
w('### 3.2 C40 node composition (robot_common.launch, `if=` conditions resolved with C40 args)')
w('')
w('| Group | Node (pkg/type) -> name | Condition / notes |')
w('|---|---|---|')
c40 = [
 ('Core services', 'database/database_node -> database', 'always; database_file_path=/etc/ros/runtime/database/peanut.db'),
 ('Core services', 'keenonrecorder/record_node, replay_node, event_tracking_node', 'always; rosbag prefix /home/peanut/keenon/idlebag/'),
 ('Core services', 'robot_state_publisher; rosbridge_websocket (default launch) + rosbridge_websocket_control (port 9091, ns control) + rosapi; tf2_web_republisher', 'always (rosbridge_server/rosapi/tf2 not shipped in debs)'),
 ('Core services', 'libcoap/coap_bridge_server; web_backend/web_backend_node (args _image_transport:=compressed); robot_settings/robot_settings_node; interactive/interactive_node', 'always'),
 ('Sensors', 'free_lidar_driver/free_lidar_driver_node (LIDAR_TYPE=free, NUM=1 -> free_1.launch) + lidar_filter/lidar_filter (lidar_filter_1.launch, free_filter_params.yaml)', 'always (C40 front lidar)'),
 ('Sensors', 'ydlidar_ros_driver_v2/ydlidar_ros_driver_v2_node (GS2.launch, /dev/ttyUSBLidar, 921600 baud, frame laser_back_link)', 'USE_LASER_BACK=true'),
 ('Sensors', 'sy_sdk/sy_tof_dev_node (sy_tof_1.launch -> sy_tof_params.yaml)', 'USE_CLEAN and USE_TOF'),
 ('Sensors', 'micro_laser/auto_bind_node + micro_laser_node micro_laser_ACM0/1/2 (XD_4.launch)', 'USE_CLEAN and USE_EDGE_LASER'),
 ('Sensors', 'fdl_ros/fdl_ros_node (+ fdl_ros_ob_sensor_node not shipped)', 'USE_DROP_RESISTENT=true'),
 ('Sensors', 'anomalies_detection/{depth_laser_cali_node,sensor_monitor_node,danger_area_detector_node}', 'USE_ANOMALIES_DETECT=true'),
 ('Sensors', 'calibration/calibration_node (calib_2halley.yaml)', 'USE_CLEAN'),
 ('Actuation', 'chassis/chassis (chassis_params.yaml; remap /final_cmd_vel->/cmd_vel if safe_decision off)', 'always'),
 ('Actuation', 'safe_decision/safe_decision_node', 'USE_SAFE_DECISION (default true)'),
 ('SLAM/localization', 'cartographer_ros/cartographer_node (keenon_common.lua), cartographer_occupancy_grid_node, auto_denoise_node; label_filter/label_filter_node; gmapping/slam_gmapping', 'always (cartographer_1.launch because LIDAR_NUM=1)'),
 ('SLAM/localization', 'peanut_localization/peanut_localization_node; location_fusion/location_fusion_node; label_fusion/label_fusion_node; keenon_vslam/keenon_vslam_node', 'LOCATION_TYPE=peanut_localization; USE_LABEL; MONO=true'),
 ('Maps', 'switch_map/switch_map; virtual_wall_generator; topo_map_generator; schedule_map_builder', 'always'),
 ('Navigation', 'move_base/move_base (kplanner + TebLocalPlannerROS, move_base_params.yaml) via move_base_teb.launch', 'T2_SINGLE_DIRE=true'),
 ('Navigation', 'multi_goals_planner; matching_task; target_point_task; elevator_position_detector; elevator_task; lora_driver; task_dispatcher', 'always'),
 ('Cleaning', 'cover_planner/{cover_planner,complement_sweep}; robot_monitor; task_manager/task_manager; segmentation/segmentation_node', 'USE_CLEAN=true'),
 ('Not launched', 'schedule_ros (SCHEDULE_SW=false), dynamic_detector (USE_DYDETECT=false), camera driver (USE_CAMERA=false), vslam (VSLAM=false), amcl, busy_map (commented out), suspend_obs (USE_SUSPEND default false)', 'for C40 defaults'),
]
for g, n, c in c40: w('| %s | %s | %s |' % (g, n.replace('|', '/'), c))
w('')
w('### 3.3 All shipped launch files (178) by package')
w('')
lc = collections.Counter()
for r in rows:
    n = r['cnt'].get('.launch', 0)
    if n: lc[r['key']] = n
w(', '.join('%s: %d' % (k, v) for k, v in lc.most_common()) + '. `robot-settings` holds 105 (per-model + `include/*.launch`); `roslaunch` 6 and `astra-launch` 12 are upstream examples/includes; `cartographer` 24 (mostly upstream demos plus keenon.launch, keenon_T2.launch, keenon_offline.launch); `sdkeli-ls-udp` 4 (hostname 192.168.0.10/.11 port 2112 examples). `rosbag.launch` is not well-formed XML and could not be parsed.')
w('')
w('### 3.4 Key parameter files (C40, `robot_settings/cfg/yaml/C40/`, 65 files)')
w('')
w('| File | Key content (STATIC) |')
w('|---|---|')
for a, b in [
 ('chassis_params.yaml', 'port /dev/ttyUSBStm32; wheel_gauge 377.4 mm; wheel_perimeter 439.2 mm; max_acc 0.4; clean scene tuning blocks (CleanSceneArrived, StandingTurn, Block, PathUturn...); water_suction 80; WaterControlLow pulse (water_on 1.0 s / off 2.0 s, pump gear 30); brush gears per stage (edge/wash/roll + rods)'),
 ('sdkfree_params.yaml', 'host 192.168.64.103, port 2111, frame laser_link, range 0.05..30 m, start/stop angle -15..195, offset -90'),
 ('sy_tof_params.yaml', 'ToF pose sy_euler [0,0.2967,0], sy_vector3 [0.2603,0,0.61844]; config_path /etc/ros/runtime/sensors/sy_tof_config.json; plane/line extraction thresholds'),
 ('interactive_params.yaml', 'pose/gate/elevator/map/label JSON paths under /etc/ros/runtime; serial_port /dev/ttyUSBLoRa; max_task_time 720; nodes [database, interactive, move_base, task_manager]'),
 ('move_base_params.yaml', 'controller_frequency 15; KPlannerROS is_clean_robot true; recovery_behaviors clear_static_recovery, rotation_escape_recovery; robot_length 0.68; narrow-passage and elevator/gate timeouts'),
 ('teb_local_planner_params.yaml / teb_clean_local_planner_params.yaml', 'max_vel_x 0.8; max_vel_theta 0.6 / 1.0; acc_lim_x 0.7 / 0.3; acc_lim_theta 1.2; xy_goal_tolerance 0.1'),
 ('costmap_common_params.yaml', 'footprint 11-point polygon (about 0.62 x 0.54 m); observation sources /scan, /fdl/obs_cliff, /fdl/obs_low, /sy_tof_dev_node/depth/points, /bump, /beam, /gate_cloud, /mx/cloud1,2, /edge_laser_front_cloud, /low_cloud_left|right, carpet clouds'),
 ('cover_planner.yaml / clean_coverage.yaml', 'cover_plan_algo 1 (BCD); roller_width 7 px; footprint multi_circles; CleanCoverage brush_dist 0.05, corner/route offset parameters'),
 ('robot_monitor_params.yaml', 'motor error bits -> codes 140408/140413/140424/140429; dump 140299; dust_box 140601'),
 ('safe_decision.yaml / task_manager.yaml / schedule_params.yaml', 'sds_open true, sds_max_linear_acc 1.2; reclean_enable 0, test_run_area_limit 10000; business_type c-clean, other_robot_meet_passageWidths C30/C40/C40H'),
 ('elevator_params.yaml', 'mqtt_server_ip (cloud IP, see 7); sub/pub topic names *_test; offsets/radii'),
 ('init_location_params.yaml', 'location_mode charge_mode; use_charge_pile true; charge_pose_num 211; search params'),
 ('uwb_params.yaml', 'hostname 172.16.1.33 port 9000 use_uwb false'),
 ('common/common_params.yaml, common_config.yaml', 'persistence_params (side_drive_status, clean/mileageSavePeriod, label_switch, map_area_threshold, ability_sync_list [schedule]); taskTypeList, robotTypeList, custom_area_type_list [floor_carpet, no_falling, temporary_floor_carpet], max_subarea_area 1000')]:
    w('| %s | %s |' % (a, b))
w('')
w('Other configs: cartographer `configuration_files/keenon_common.lua` (num_laser_scans 1, use_odometry true, use_landmarks true, use_online_correlative_scan_matching, submaps 60 range data, optimize_every_n_nodes 40, global_localization_min_score 0.6); `label_filter.yaml`; `keenon_vslam/config/*.yaml` (ORB vocabulary /etc/ros/runtime/vslam/support_files/ORBvoc.bin, camera topic /camera/image); `peanut_localization/yaml/configs/{filters,location_common,pose_estimation,pose_recovery,pose_tracking,create_local_map}`; `micro_laser/config/{edge_laser_front,low_laser_left,low_laser_right}.yaml`; `calibration/config/config.json` (FDL parameters, host_url 192.168.64.199:9090).')
w('')

# ---- 4 msgs
w('## 4. Message / service / action catalog (from `.msg/.srv/.action` in the debs)')
w('')
w('Totals: 244 `.msg` (including auto-generated `*Action*`, `*Goal`, `*Result`, `*Feedback` wrappers), 172 `.srv`, 8 `.action`. Below, auto-generated action wrapper messages are omitted; fields are abbreviated. Constants (enum values) are listed only for key messages.')
w('')
cur = None
acts = set()
items = []
for l in msgs_txt:
    if l.startswith('### '): cur = short(l[4:]); continue
    m = re.match(r'^(\S+?)\.(msg|srv|action) \[[^\]]*\]: (.*)$', l)
    if not m:
        m2 = re.match(r'^(\S+?)\.(msg|srv|action) \[[^\]]*\]: ?$', l)
        continue
    items.append((cur, m.group(1), m.group(2), m.group(3)))
    if m.group(2) == 'action': acts.add((cur, m.group(1)))
skip = set()
for p, a in acts:
    for suf in ('Action', 'ActionGoal', 'ActionResult', 'ActionFeedback', 'Goal', 'Result', 'Feedback'): skip.add((p, a + suf))
bypkg = collections.OrderedDict()
for p, n, e, f in items:
    if e == 'msg' and (p, n) in skip: continue
    bypkg.setdefault(p, []).append((n, e, f))
n_listed = 0
for p, lst in bypkg.items():
    w('### 4.%s `%s`' % ('x', p))
    w('')
    w('| Name | Kind | Key fields (abbrev.) |')
    w('|---|---|---|')
    for n, e, f in lst:
        f = f.replace('|', '/')
        if len(f) > 230: f = f[:230] + ' ...'
        w('| %s | %s | %s |' % (n, e, f)); n_listed += 1
    w('')
w('Listed %d definitions. Domains requested in the brief and where they live: clean (keenon_clean_msgs), charge (keenon_charge_msgs), chassis (keenon_chassis_msgs + keenon_motor_msgs + keenon_sensor_msgs), task (keenon_task_msgs), schedule (keenon_schedule_msgs), elevator (keenon_elevator_msgs), gate (keenon_gate_msgs), nav (keenon_nav_msgs, keenon_move_base_msgs), location (keenon_location_msgs), ui (keenon_ui_msgs), vslam (keenon_vslam_msgs), diagnosis (keenon_diagnosis_msgs), recorder (keenon_recorder_msgs), seller (keenon_seller_msgs), label (keenon_label_msgs), database (keenon_database_msgs). Vendor-added: cartographer_ros_msgs, peanut_localization (LocalizationDetailed/Diagnostics/Times), web_backend, robot_settings/launchConfig, kplanner, berxel_camera.' % n_listed)
w('')
w('Key enumerations (CONFIRMED in .action/.msg): ChargeTask goals AUTO=1, DIRECT=2, EXIT=3, CABLE=4; MoveBase goal_status NORMAL_TARGET=1, ELEVATOR_TARGET=2, CHARGE_TARGET=3, GET_OUT_OF_ELEVATOR=4, MANUAL_CHARGE=5, UNLOCK_MOTOR=6, LOCK_MOTOR=7, LOW_SPEED_MODE=8, SELLER_TARGET=9, ELEVATOR_TARGET_FORCE=10, ORIGIN_TARGET=11, ELEVATOR_QUEUE=12, ZONE_START_POSE=13; CleanPNC clean_type EDGE=1/COVER=2/OMISSION=3, clean_action RUN=1/PAUSE=2/STOP=3; clean_status.cleanStage along_edge=1, cover=2, avoid=3, reclean=4; TaskInfo taskType TargetPoint=1, Charge=2, Elevator=3, Seller=4, Clean=5; web_backend/robot_state NAVIGATION=0, GMAPPING=1, CARTOGRAPHER=2, EXPANSION=3.')
w('')

# ---- 5 nodes/topics
w('## 5. Nodes, executables and topic/service references (all STATIC_REFERENCE)')
w('')
w('### 5.1 Notes on method')
w('')
w('Names were harvested from ELF strings that look like ROS graph names (leading `/`, identifier characters) and from `pkg/Type` message-type strings. They may include parameter names and a few false positives (short garbage strings were dropped by regex). They do not prove that a topic is active, who publishes it, or its direction.')
w('')
w('### 5.2 Selected node interfaces')
w('')
sel = [('chassis:chassis', 'Chassis / STM32 bridge'), ('interactive:interactive_node', 'UI bridge'), ('task-manager:task_manager', 'Cleaning task manager'), ('task-dispatcher:task_dispatcher_node', 'Task dispatcher'), ('web-backend:web_backend_node', 'Web backend'), ('robot-settings:robot_settings_node', 'Robot settings'), ('database:database_node', 'Database'), ('lora-driver:lora_driver', 'LoRa'), ('cover-planner:cover_planner', 'Cover planner'), ('micro-laser:micro_laser_node', 'Micro laser'), ('fdl-ros:fdl_ros_node', 'FDL cliff'), ('sy-sdk:sy_tof_dev_node', 'ToF'), ('keenon-vslam:keenon_vslam_node', 'VSLAM'), ('elevator-task:elevator_task_node', 'Elevator task'), ('keenonrecorder:replay_node', 'Replay'), ('calibration:calibration_node', 'Calibration')]
for k, lab in sel:
    v = topics.get(k)
    if not v: continue
    names_ = [n for n in v['names'] if len(n) > 3 and not re.match(r'^/[A-Za-z]{1,3}$', n)]
    w('**%s** (`%s`, %d names, %d msg types):' % (lab, k, len(names_), len(v['types'])))
    w('')
    w('`' + ' '.join(names_)[:1800] + '`' + (' ...' if len(' '.join(names_)) > 1800 else ''))
    w('')
w('Full per-node name lists for all 76 executables were generated to the scratchpad (`an/topics.json`) and are summarised above only for the nodes most relevant to cleaning, charging, UI and diagnostics.')
w('')
w('### 5.3 Executable inventory (ARM32 ELF executables, stripped unless noted)')
w('')
w('| Package | Executable | Size | Non-core linked libs (DT_NEEDED) |')
w('|---|---|---|---|')
core = ('libc.', 'libm.', 'libgcc', 'libstdc', 'libpthread', 'ld-linux', 'libdl', 'librt', 'libboost', 'libroscpp', 'librosconsole', 'librostime', 'libcpp_common', 'libroscpp_serialization')
for e in sorted(elf, key=lambda x: (x['deb'], x['path'])):
    if e['type'] != 'EXEC': continue
    libs = ', '.join(n.replace('.so', '') for n in e['needed'] if not n.startswith(core))
    tag = '' if e['stripped'] else ' (UNSTRIPPED, %d syms)' % e['symtab_count']
    mach = e['machine'] if e['machine'] != 243 else 'RISC-V32'
    w('| %s | `%s` | %d | %s%s%s |' % (short(e['deb']), e['path'].replace('opt/ros/indigo/', ''), e['size'], (mach + '; ') if mach != 'ARM' else '', libs[:200], tag))
w('')
w('Shared objects: 186 (e.g. libcartographer.a/static, libpeanut_localization, liblocation_fusion, libslam_comm_lib, libk* support libs, libpcl_* 1.8.1.99, libfdl, libsy_tof_dev_sdk, libc200_lidar). `frpc_release_arm` is a statically linked Go binary (no DT_NEEDED, Go 1.16.2).')
w('')
w('### 5.4 Python scripts (non-generated)')
w('')
w('`robot-settings` usr/bin: config_setting.py, yaml_setting.py, check_version.py, yaml_check.py, ros_log_clean.py (17.6 kB), ros_core_clean.py, get_debug_log_flag.py, update_vslam_launch.py, catch_serialdata.py, display_ros_version.py, `home/peanut/monitor_script/ARM/monitor.py` (+ bundled psutil, setproctitle.so, monitorCfg.ini: monitors move_base, peanut_localization_node, record_node, task_manager, rosmaster, chassis, coap_bridge_server ...), system/bin/system_config/auto_make_port.py (24 kB, writes /etc/udev/rules.d/50-keenon.rules). `remote-tools`: update_frpc.py, update_map_frpc.py. `move-base`: subtopic_forwarder.py, subtopic_forwarder_node.py, warner.py. Others are upstream TEB/cartographer helper scripts.')
w('')

# ---- 6 hardware
w('## 6. Hardware evidence (STATIC_REFERENCE; no live confirmation)')
w('')
w('| Item | Evidence (deb + path / string) | Status | Conf. |')
w('|---|---|---|---|')
hw = [
 ('SoC / OS', 'armhf debs; firmware/rosconsole/aarch64 holds ARM32 lib; user_monitor.sh uses /sys/kernel/config/usb_gadget/rockchip, adbd.sh; system_monitor.sh reads soc-* thermal zone; arm_setup.conf taskset CPUs 4,5; create_swap.sh aarch64 swap sizing', 'STRONGLY_SUPPORTED', 'MEDIUM'),
 ('Main lidar ("free" type)', 'free-lidar-driver + libc200_lidar.so (TCP connect, C200 class); C40 sdkfree_params host 192.168.64.103 port 2111; lidar_manager.sh Free="192.168.64.103"; vendor/model name of the lidar not stated in package', 'CONFIRMED (interface) / UNKNOWN (vendor model)', 'HIGH'),
 ('Alt lidars (other models)', 'sdkeli_ls1207de (SDKELI LS1207DE, 192.168.64.100:2112, "keli"); ldlidar LD06 (ld_1.launch); ydlidar_ros_driver(+v2) (YDLIDAR, GS2); free_c260 (192.168.64.107)', 'CONFIRMED', 'HIGH'),
 ('Rear lidar (USE_LASER_BACK)', 'GS2.launch: ydlidar_ros_driver_v2_node, /dev/ttyUSBLidar, 921600 baud, lidar_type 3 (TYPE_GS), range 0.025-0.50 m, frame laser_back_link', 'CONFIRMED', 'HIGH'),
 ('Edge / low-obstacle line lasers', 'micro-laser: 3 x micro_laser_node on /dev/ttyACM0-2; auto_bind (left/right); firmware ql_xd1q_*; XD_4.launch; costmap topics /edge_laser_front_cloud, /low_cloud_left|right; udev-aliases ttyUSBAlongSideF, ttyUSBObstacleL/R', 'CONFIRMED', 'HIGH'),
 ('ToF depth (sy_tof)', 'sy-sdk sy_tof_dev_node, libsy_tof_dev_sdk.so, ros_sensor/parameter/MTP016-224x129-375DC-18-20M.ini, T00P12AM-224x129-375DC-18-20M.ini (Sunny Optical maintainer); camera_manager.sh USB 2207:0028', 'CONFIRMED', 'HIGH'),
 ('Cliff / drop (FDL)', 'fdl-ros: Orbbec OpenNI2 (liborbbec.so, 56-orbbec.rules vendor 2bc5), Berxel (libBerxelUvcDriver, params100Q.bin); calibration config.json bIsFDLAvailable; topics /fdl/obs_cliff, /fall_proof_status', 'CONFIRMED', 'MEDIUM'),
 ('Mono camera (VSLAM)', 'keenon_vslam support_files/camera_list.txt usb:v0C45p6230; MONO=true for C40', 'CONFIRMED', 'MEDIUM'),
 ('Other cameras (not C40 default)', 'astra (Orbbec 2bc5 rules), halley/two_halley_transform.yaml (astra_camera_node), berxel_left/right, MX stereo module 10.11.21.1 (2207:0006)', 'CONFIRMED', 'HIGH'),
 ('Chassis MCU', '/dev/ttyUSBStm32; topics /stm32_up, /stm32_down, /stm32_version; ros_update IAP', 'CONFIRMED', 'HIGH'),
 ('Wheel drive / odometry', 'chassis_params wheel_gauge 377.4 mm, wheel_perimeter 439.2 mm; keenon_motor_msgs encoder/error_code/lock_status/wheel_hub_current (hub motors suggested by wheel_hub_current, INFERRED)', 'CONFIRMED / INFERRED', 'MEDIUM'),
 ('Bumpers / sonar / PSD / IMU', 'chassis: size_sonar 1, size_psd 2; SensorData bump[6], sonar[4], psd[4]; /imu_raw, /imu_low_rate; use_imu false in C40 chassis_params', 'CONFIRMED', 'HIGH'),
 ('Cleaning: brushes', 'topics /motor_edge, /motor_edge_cover, /motor_roll, /motor_sweep, /motor_wash_brush_*, /rake_rod, /disc_brush, /roll_brush; yaml stageN_{edge,wash,roll}_brush_value/rod', 'CONFIRMED', 'HIGH'),
 ('Cleaning: vacuum / suction fan', '/motor_air, /water_suction_motor, /chassis/suction_switch; yaml water_suction 80 (fan gear), WaterSuctionControl.water_suction_enhanced_value', 'CONFIRMED', 'HIGH'),
 ('Cleaning: water / pump', '/water_pump, /remaining_water/*, WaterControlLow/WaterControlStop pulse control; TASK_stage_residual_water', 'CONFIRMED', 'HIGH'),
 ('Dust box', '/BC_DustBox, robot_monitor dust_box error 140601', 'CONFIRMED', 'HIGH'),
 ('Charging / dock', 'ChargeTask action, /charge_cmd_toSTM32, charger label/scan matching (matching_task), init_location use_charge_pile', 'CONFIRMED', 'HIGH'),
 ('Carpet handling', 'carpetStatus msg, /carpet_detection_switch, /temp_carpet_cloud, custom_area_type_list floor_carpet/temporary_floor_carpet', 'CONFIRMED', 'HIGH'),
 ('LoRa radio', '/dev/ttyUSBLoRa; lora_driver; elevator/gate LoRa messages', 'CONFIRMED', 'HIGH'),
 ('4G modem (legacy)', 'wvdial.conf /dev/ttyUSB4G0, usb0/ppp0 in check_connect', 'STRONGLY_SUPPORTED', 'MEDIUM'),
 ('Wi-Fi AP / Ethernet', 'hostapd wlanAP; eth0+eth1 bridged to br0; debug USB-Ethernet ethdbg', 'CONFIRMED', 'HIGH'),
 ('UWB (optional)', 'uwb_params.yaml hostname 172.16.1.33:9000 use_uwb false', 'CONFIRMED (config) / disabled', 'MEDIUM'),
 ('Debug console', 'PL2303 067b:2303 -> /dev/ttyUSBDebug, getty 115200', 'CONFIRMED', 'HIGH'),
]
for a, b, c, d in hw: w('| %s | %s | %s | %s |' % (a, b.replace('|', '/'), c, d))
w('')

# ---- 7 network
w('## 7. Network interfaces and endpoints (STATIC_REFERENCE)')
w('')
w('| Endpoint | Where defined | Notes | Status |')
w('|---|---|---|---|')
net = [
 ('ROS master tcp/11311 on 192.168.64.20', 'upstart keenonrobot.conf ROS_IP; rosenv ROS_MASTER_URI; roslaunch default', 'no auth in ROS1', 'INFERRED (default port)'),
 ('rosbridge ws 9090 (default launch) and 9091 (control, ns /control)', 'robot_common.launch; rosbridge_websocket_control.launch (port=9091, ssl=false, authenticate=false, address "")', 'plain WS, all interfaces; see ROS-030', 'CONFIRMED / INFERRED'),
 ('ros_update HTTP (port 9527 inferred) /upgrade/action, /upgrade/status', 'ros-indigo-libcpprest usr/bin/ros_update strings', 'plain HTTP, root; see ROS-031', 'CONFIRMED / INFERRED port'),
 ('WsServer websocket (port UNKNOWN) actions frpRestart/frpConfig/frpMapConfig/frpDisconnect/frpState/debug_close', 'remote-tools usr/bin/WsServer', 'drives frpc + nginx config; see ROS-032', 'CONFIRMED (actions) / UNKNOWN port'),
 ('frp client outbound (server addr/port/token supplied at runtime)', 'frpc_info.ini template, update_frpc.py', 'exposes 127.0.0.1:22 (SSH) via remote_port', 'CONFIRMED'),
 ('CoAP server (port UNKNOWN; default 5683 INFERRED) + HTTP/TFTP downloader', 'libcoap coap_bridge_server / libcoap_bridge.so', 'resources Clean/Diagnosis/Elevator/Error/Fdl/Motor/Navigation/Position/Runtime/Vendor/power', 'CONFIRMED (classes)'),
 ('Static web UI (robotviz) served by an HTTP server not in package (nginx referenced)', 'robotviz; WsServer writes /etc/nginx/conf.d/mapping.conf and runs nginx -s reload', 'client connects ws://192.168.64.20:9090/9091', 'CONFIRMED client side'),
 ('Samba (if installed) shares /root/.ros (rw), /etc/ros/runtime (ro), /home/peanut (rw)', 'robot_settings/system/conf/smb.conf', 'guest ok = no', 'CONFIRMED config'),
 ('SSH 22', 'frpc ssh tunnel local_port=22', 'sshd not in package', 'INFERRED'),
 ('Lidar 192.168.64.103:2111 (free), .107 (free_c260), .100:2112 (keli), .101 and .104 second lidar examples', 'yaml/launch, lidar_manager.sh pings these', '', 'CONFIRMED'),
 ('MX stereo module 10.11.21.1 (FTP anonymous, ZeroMQ tcp://)', 'module_tool, send_depth_node, check_mx_stereo, vslam_available.sh', '', 'CONFIRMED'),
 ('UI/Android board 192.168.64.10 (gateway); calibration host 192.168.64.199:9090; dev ws 172.16.8.13:9092', 'interfaces, config.json, robotviz', '', 'CONFIRMED'),
 ('Cloud 47.96.156.255 (mqtt_server_ip, ping target)', 'elevator_params.yaml; check_connect', 'no MQTT client code found', 'CONFIRMED value / UNKNOWN use'),
 ('NTP cn.pool.ntp.org; DNS 8.8.8.8, 114.114.114.114', 'time_Synchronization.sh, hwclock_sync.sh, dhcpd.conf, robot_setup', '', 'CONFIRMED'),
 ('DHCP server on br0 192.168.64.200-209; hostapd AP SSID Keenon_robot_FFFFFF bridged', 'dhcpd.conf, isc-dhcp-server, hostapd.conf', '', 'CONFIRMED config'),
 ('ADB over USB gadget (Rockchip)', 'user_monitor.sh', '', 'CONFIRMED script'),
]
for a, b, c, d in net: w('| %s | %s | %s | %s |' % (a, b, c, d))
w('')
w('MQTT, WebSocket (rosbridge, WsServer, ros_update), CoAP, HTTP (cpprest), FTP (anonymous client use), TFTP (libkQLTftp/curl) and ZeroMQ are the protocols found. libcurl is linked by ros_update and coap_bridge_server. No HTTPS/TLS server setup was found in these binaries.')
w('')

# ---- 8 DB
w('## 8. Databases, maps and storage')
w('')
for f in FINDINGS:
    if f['domain'] == 'DB':
        w('### %s - %s' % (f['id'], f['title']))
        w('')
        w('- Evidence: ' + f['evidence'])
        w('- Status/confidence: %s / %s' % (f['status'], f['confidence']))
        w('')
w('Database service names (keenon_database_msgs): mapConfig, poseConfig, zoneConfig, pathConfig, initPoseConfig, labelConfig, gateConfig, elevatorConfig, keyframeConfig, vslamKeyframeConfig, commonAreaConfig, commonPathConfig, dynamicMapConfig, buildMapTypeConfig, mapAreaTypeConfig, mapTransConfig, robotPosConfig, robotSensorDataConfig, tableConfig, web_app_data_config, ability_crud/ability_sync/ability_table, manager (zip import/export), pbstreamConfig.')
w('')

# ---- 9 scripts
w('## 9. Scripts and install flow')
w('')
w('### 9.1 manifest.json')
w('')
w('| src | dest | type | mode | count |')
w('|---|---|---|---|---|')
cc = collections.Counter((e['src'], e['dest'], e['type'], e['mode']) for e in manifest['files'])
for (s_, d_, t_, m_), n in cc.items(): w('| `%s` | `%s` | %s | %s | %d |' % (s_, d_, t_, m_, n))
w('')
w('`name`: `slam-ros-v2.10.9-c2-0-g58a75a7-app-update-package.zip`. Only keys name/src/dest/mode/type exist per entry (no checksum/signature/version). `bin/keenon` appears twice (to `bin/` and absolute `/etc/init.d/`). ros_update strings (INFERRED flow): download by URL+md5 -> `unzip -o` into /work/update -> read manifest.json -> chmod per mode -> stop cron/anacron -> run `script/upgrade.sh` if present -> `service keenonrobot stop` -> `dpkg -i --force-confnew` -> `apt-get -y -f install` -> restart/reboot. `script/app` and `bin/keenon` start `/work/scripts/guard.py` (python 2) in the background; guard.py is not present (NOT_ANALYZED).')
w('')
w('### 9.2 Maintainer scripts (only 6 debs have any)')
w('')
w('| Deb | Script | Action |')
w('|---|---|---|')
w('| fdl-ros, libcpprest, location-fusion, peanut-localization | postinst / postrm | dh_makeshlibs `ldconfig` only |')
w('| robot-settings | preinst | create /home/peanut/launch_config, copy keenon_robot.launch (if missing/empty) and camera_*.yaml; wipe and recreate /home/peanut/yaml_config from cfg/yaml/common/*.yaml; sync |')
w('| robot-settings | postinst (4.9 kB) | source ROS; write VERSION=slam-ros-v2.10.9-c2-0-g58a75a7 into /etc/os-release; copy smb.conf to /etc/samba/ (guard bug, see ROS-047); delete legacy /etc/init.d/{robot_setup,keenonrobot,set_net_gw,set_up_frpc}; run config_setting.py and yaml_setting.py (merge user config); rm /usr/local/lib/libusb*; mkdir /home/peanut/mx_ql and keenon/core; delete schedule_log and /etc/cron.daily/ros_log_clean; set HISTTIMEFORMAT in /etc/bash.bashrc; sudo-replace rosbridge protocol.py/publishers.py, tornado iostream.py and tf2_web_republisher; copy librosconsole.so; truncate 50-keenon.rules + udev reload/trigger; (aarch64) remove usb_scan from /usr/local/bin and time-sync rc.local line; run user_monitor_update.sh |')
w('')
w('No sudoers files, no `chmod 777` and no setuid files are installed by the package; `chmod 755` is applied to tf2_web_republisher and to monitor.py by upstart; udev rules use MODE 0666 (ROS-048).')
w('')
w('### 9.3 Services, startup and cron')
w('')
for f in FINDINGS:
    if f['id'] in ('ROS-014', 'ROS-015'):
        w('- **%s** %s: %s' % (f['id'], f['title'], f['evidence']))
w('')
w('Other installed helper binaries in `/usr/bin` (robot-settings): usb_scan (tty alias creator via netlink), module_tool, fixture_calibrate, as_nuwacam_node, fdl_calibration_node, nmon, ethtool, schedule-reset, check_mx_stereo, mx_config.')
w('')

# ---- 10 security
w('## 10. Security observations (summary; details in findings JSON)')
w('')
w('| ID | Severity | Title | Status | Conf. |')
w('|---|---|---|---|---|')
order = {'Critical': 0, 'High': 1, 'Medium': 2, 'Low': 3, 'Info': 4}
for f in sorted([f for f in FINDINGS if f['domain'] in ('SEC', 'NET') and f['severity'] != 'Info'], key=lambda f: (order[f['severity']], f['id'])):
    w('| %s | %s | %s | %s | %s |' % (f['id'], f['severity'], f['title'], f['status'].split(';')[0], f['confidence']))
w('')
w('Secrets found (values redacted): (1) sudo password string in `send_depth_node` and `WsServer`, 11 chars, starts `ke`, identical to (2) default hostapd WPA passphrase in `hostapd.conf`; (3) robotviz client bundle: hard-coded AES key (8 chars, starts `PE`) and expected-ciphertext constant (96 hex chars, starts `52`), pre-filled default password (6 digits, starts `12`); (4) `wpa_supplicant.conf` PSK hash (64 hex, starts `ca`); (5) `wvdial.conf` guest/guest. No private keys, certificates or API tokens were found. `frpc_info.ini` token/server fields are empty templates.')
w('')

# ---- 11 gaps
w('## 11. Gaps, limitations and NOT_ANALYZED items')
w('')
for g in [
 'No disassembly/decompilation: ros_update, WsServer, coap_bridge_server, chassis, interactive, task_manager were examined by strings/symbols only; authentication, input sanitisation, ports (WsServer, CoAP) and exact OTA protocol are UNKNOWN/NOT_ANALYZED.',
 'guard.py (/work/scripts), script/upgrade.sh, rosbridge_server/rosapi, nginx, Samba, ORBvoc.bin, STM32 and encoder firmware, Android apps (other workers) are not in this package.',
 'Which model/launch profile and which Wi-Fi/SSH/Samba credentials are active on a given unit cannot be derived from the package.',
 'Message field lists are abbreviated in this report; full definitions were parsed from the debs (scratchpad an/msgs.txt).',
 'Third-party libs (libpcl*, libboost*, libopencv*, OpenNI2) were parsed for ELF metadata only; no CVE correlation (no network).',
 'rosbag.launch is not well-formed XML; parsed textually only. The YAML for the 30 other models was not individually reviewed beyond IP/port/secret scans.',
 'Topic directions (pub/sub), QoS and rates are not recoverable statically.']:
    w('- ' + g)
w('')
open(OUT + '/ros_packages.md', 'w', encoding='utf-8').write('\n'.join(L) + '\n')
print('findings', len(FINDINGS), 'artifacts', len(art), 'report lines', len(L))
