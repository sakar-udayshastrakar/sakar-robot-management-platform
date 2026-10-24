# ROS Topic / Node Inventory - Keenon C40 (release V246, `slam-ros-v2.10.9-c2`)

**Classification of ALL rows in this document: `CONFIRMED_STATIC` as a source-level reference, `UNVERIFIED_RUNTIME` as a live fact.** Every topic/node/type name was harvested statically from launch XML, `.msg/.srv/.action` definitions, YAML, and ELF strings in the shipped `slam-ros` package. **No live ROS graph was observed** (no `rostopic list`/`rosnode list` was run; no robot was reachable). A name appearing in a launch file or a binary does **not** prove the topic is advertised at runtime, who publishes it, or its direction.

## Method and its limits (read before using the tables)

- **Source:** per-node name lists were extracted by the ROS worker from each ELF's string table (names beginning `/`) and from `pkg/Type` message-type strings, plus launch/YAML. Full machine lists are in `partials/ros_packages.md` section 5.2 and the scratch file `an/topics.json`. This document re-organises the cleaning/navigation-relevant subset by functional category.
- **Direction (publisher/subscriber):** string harvesting **does not** establish direction. Direction is marked `UNKNOWN` unless a specific artifact states it (e.g. the CoAP bridge node, ROS-033). Determining true pub/sub per node would require disassembly of `advertise<T>()`/`subscribe()` call sites, which was not performed (no decompiler for ARM32 ROS binaries was available; `rea` native providers Ghidra/Hopper/IDA are not installed).
- **Message types:** the `.msg/.srv/.action` **catalog** is confirmed (files exist in the `*-msgs` debs; see `ros_packages.md` section 4). But the **pairing** of a specific topic name to a specific message type is mostly `INFERRED` from naming convention, because the binaries store topic strings and type strings separately. Pairings marked `INFERRED` below use ROS naming convention (e.g. `/scan` -> `sensor_msgs/LaserScan`); pairings marked `CONFIRMED_STATIC (def)` come from a `.msg` field reference or a launch remap.
- **Active-on-C40 filtering:** the C40 launch profile (`keenon_robot_C40.launch`, ROS-002) sets `USE_CAMERA=false`, `VSLAM=false` (but `MONO=true`), `LIDAR_TYPE=free`, `LIDAR_NUM=1`, `USE_TOF=true`, `USE_EDGE_LASER=true` (x4 XD lasers), `USE_LASER_BACK=true`, `USE_DROP_RESISTENT=true`, `USE_CLEAN=true`, `SCHEDULE_SW=false`. Nodes launched vs not-launched for C40 are per ROS-003. **Which launch file is actually active on a given physical unit is UNKNOWN** (the package default launch is a different model, W3).

## 1. ROS nodes associated with the C40 profile

From `robot_common.launch` resolved with C40 args (ROS-003, `CONFIRMED_STATIC` from launch XML; `UNVERIFIED_RUNTIME` as live processes):

**Always launched:** `database`, `keenonrecorder`, `robot_state_publisher`, `rosbridge_websocket` (+ `control` on 9091), `tf2_web_republisher`, `coap_bridge_server`, `web_backend`, `free_lidar_driver` (free_1), `lidar_filter_1`, `chassis`, `virtual_wall_generator`, `switch_map`, `peanut_localization`, `move_base` (kplanner + TebLocalPlannerROS), `gmapping`, `cartographer_1`, `interactive`, `elevator_position_detector`, `topo_map_generator`, `lora_driver`, `matching_task`, `robot_settings_node`, `multi_goals_planner`, `schedule_map_builder`, `location_fusion`, `task_dispatcher`, `elevator_task`, `target_point_task`.

**Conditionally launched for C40:** `fdl_ros_node` (cliff/drop, `USE_DROP_RESISTENT`), `anomalies_detection`, `label_fusion`, `keenon_vslam_node` (`MONO`), `safe_decision`, GS2/ydlidar back lidar (`USE_LASER_BACK`), and under `USE_CLEAN`: `cover_planner` + `complement_sweep`, `robot_monitor`, `task_manager`, `segmentation`, `sy_tof_dev_node` (`sy_tof_1`), `micro_laser_node` x3 + `auto_bind_node` (XD_4 edge lasers), `calibration`.

**NOT launched for C40:** `schedule_ros` (`SCHEDULE_SW=false`), `dynamic_detector`, astra/halley `camera` (`USE_CAMERA=false`), standalone `vslam`, `amcl`, `busy_map` (commented out).

Full executable inventory: 76 ELF nodes (ROS-070, `ros_packages.md` 5.3). Node<->topic name associations below are from each node's own string table (the node whose binary contains the name), which is strong evidence the node *references* the topic but does **not** prove publish vs subscribe.

## 2. Topics by functional category

Direction legend: `PUB`/`SUB` only where an artifact states it; otherwise `UNKNOWN (ref)` = the name is referenced by the listed node(s). Type legend: `(def)` = backed by a `.msg/.srv` definition or launch remap; otherwise `INFERRED` by convention. All rows: static class `CONFIRMED_STATIC (ref)`, live class `UNVERIFIED_RUNTIME`.

### 2.1 LiDAR

| Topic | Message type | Referencing node(s) | Dir | Confidence |
|---|---|---|---|---|
| `/scan` | `sensor_msgs/LaserScan` (INFERRED) | lidar driver -> `lidar_filter_1` -> consumers (gmapping, cartographer, move_base, task_manager, calibration) | UNKNOWN (ref) | HIGH (topic exists) / MED (type) |
| `/scan_orig` | `sensor_msgs/LaserScan` (INFERRED) | `micro_laser`, `fdl_ros`, `keenon_vslam`, `keenonrecorder` (replay) | UNKNOWN (ref) | HIGH / MED |
| `/scan_base_map` | `sensor_msgs/LaserScan` (INFERRED) | `web_backend` | UNKNOWN (ref) | MED |
| `/edge laser scans` e.g. `/micro_laser/debug`, `/micro_debug/edge_scan_num_`, `/micro_laser/fdl_msg_width_threshold`, `/micro_laser/restart_request`, `/micro_laser/power_restart_completed` | `keenon_sensor_msgs/*`, `std_msgs/*` (INFERRED) | `micro_laser_node` (x3, XD edge lasers) | UNKNOWN (ref) | MED |
| Lidar hardware endpoints (not ROS topics): free lidar TCP `192.168.64.103:2111`, keli `192.168.64.100:2112`, c260 `192.168.64.107` | n/a (raw TCP/UDP) | `free_lidar_driver`/`libc200_lidar.so`, `sdkeli_ls1207de`, `ydlidar_ros_driver(+v2)`, `ldlidar` | driver connects out | HIGH |

Back lidar for C40: `USE_LASER_BACK=true` -> a GS2/ydlidar rear unit (ROS-002/005). ToF line lasers feed `/fdl/obs_low`, `/get_low_obs`, `/NoFallingRiskMap` (see FDL/cliff below).

### 2.2 Cameras / depth cameras

| Topic | Message type | Node | Dir | Notes |
|---|---|---|---|---|
| `/camera/depth/image_*`, `/camera/rgb/image_*`, `/camera/depth/points`, `rgb_camera_info` | `sensor_msgs/Image`, `sensor_msgs/PointCloud2`, `sensor_msgs/CameraInfo` (INFERRED, astra driver convention) | `astra_camera` / `berxel_camera` | UNKNOWN (ref) | **NOT launched on C40** (`USE_CAMERA=false`, ROS-003). Driver/topics exist in package but are inactive in the C40 default profile. |
| `/depth/points`, `/RawCloud`, `/calib_cloud`, `/config_cloud`, `/camera/get_sn`, `/restart_camera`, `/camera_calibration` | `sensor_msgs/PointCloud2`, `keenon_clean_msgs/camera_calibration` (def, action) | `calibration_node` | UNKNOWN (ref) | Calibration node IS launched under `USE_CLEAN`; uses depth/ToF cloud. |
| `/sensor/depthData`, `/calibration` | `keenon_sensor_msgs/*` (INFERRED) | `sy_tof_dev_node` (Sunny ToF) | UNKNOWN (ref) | ToF **is** active on C40 (`USE_TOF=true`, `TOF_TYPE=sy_tof`). |
| `/robot_ros/ob_depth_image`, `/vslam/get_module_image` | `sensor_msgs/Image` (INFERRED) | `fdl_ros`, `keenon_vslam` | UNKNOWN (ref) | Mono/depth image source for drop-detection and VSLAM. |

**C40 depth sensing = Sunny ToF (`sy_tof`) + FDL depth module, NOT the astra/berxel RGB-D cameras** (those are present but disabled in the C40 launch). This is an important correction to any assumption that the C40 uses the astra/berxel cameras.

### 2.3 IMU

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/imu_raw` | `sensor_msgs/Imu` (INFERRED) | `chassis` (STM32 bridge), `keenonrecorder` | UNKNOWN (ref) |
| `/imu_low_rate` | `sensor_msgs/Imu` (INFERRED) | `chassis` | UNKNOWN (ref) |
| `/chassis/imu_reset`, `/imu_reset_trigger`, `/imu_reset_complete`, `/imu_reset_result` | `std_msgs/*` (INFERRED) | `chassis`, `interactive` | UNKNOWN (ref) |
| `/magnetometer_data` | `keenon_chassis_msgs/magnetometer_data` (def) | `chassis` | UNKNOWN (ref) |

### 2.4 Odometry

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/odom` | `nav_msgs/Odometry` (INFERRED) | `chassis` | UNKNOWN (ref) |
| `/odom_combined` | `nav_msgs/Odometry` / `geometry_msgs/PoseWithCovarianceStamped` (INFERRED) | `peanut_localization`, `keenon_vslam`, `keenonrecorder` | UNKNOWN (ref) |
| `/encoder_raw`, `/encoder_odom_t` | `keenon_*` (INFERRED) | `chassis`, `task_manager`, `keenonrecorder` | UNKNOWN (ref) |
| `/mileage` | `std_msgs/*` (INFERRED) | `chassis` | UNKNOWN (ref) |

### 2.5 TF (transforms)

| Topic | Message type | Node | Dir | Evidence |
|---|---|---|---|---|
| `/tf`, `/tf_static` | `tf2_msgs/TFMessage` (INFERRED) | `robot_state_publisher`, `chassis`, `peanut_localization`, `keenon_vslam`, others | PUB/SUB (framework) | **The literal `/tf` string was NOT found in the node binaries** (the string-harvest regex dropped names shorter than its minimum length). Presence is `INFERRED` from: (a) the `tf::TransformBroadcaster` symbol IS present in `chassis` (byte-exact check), (b) `robot_state_publisher` and `tf2_web_republisher` are launched, (c) frames `base_link`, `laser_link`, `odom_combined` are referenced. Confidence HIGH that TF is used; the literal topic names are convention. |

Frames referenced (CONFIRMED_STATIC): `base_link`, `laser_link` (and camera/tf-prefix frames in the astra launch, inactive on C40).

### 2.6 Localization

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/localization/robot_pose` | `geometry_msgs/PoseStamped` (INFERRED) | `peanut_localization`, `keenon_vslam`, `task_manager` | UNKNOWN (ref) |
| `/localization/better_location`, `/localization/no_data_location_status`, `/localization/robotSensorData`, `/localization/set_pose`, `/localization/submap_point_cloud`, `/localization/realtime_location_status` | mix `std_msgs`, `sensor_msgs`, `keenon_location_msgs/*` (INFERRED/def) | `peanut_localization`, `interactive`, `cover_planner` | UNKNOWN (ref) |
| `/initialpose`, `/initialpose_config` | `geometry_msgs/PoseWithCovarianceStamped` (INFERRED) | `interactive`, `peanut_localization` | UNKNOWN (ref) |
| `/init_location_pose`, `/init_location_node/config`, `/init_location_node/location_mode`, `/ignore_location`, `/feedback_location_status`, `/location_pose_info` | `keenon_location_msgs/*` (INFERRED) | `interactive`, `location_fusion`, `cover_planner` | UNKNOWN (ref) |
| `/fusion_pose` | `geometry_msgs/PoseStamped` (INFERRED) | `keenonrecorder`, `location_fusion` | UNKNOWN (ref) |
| VSLAM: `/vslam_robot_pose`, `/vslam_keyframe_pose`, `/vslam_keyframe_array`, `/vslam_match_pose`, `/vslam_match_cloud`, `/mx_vslam_keyframe`, `/realtime_location_status`, plus ~50 `keenon_vslam/*` tuning params | `keenon_vslam_msgs/*`, `keenon_database_msgs/keenon_vslam_keyframe` (def) | `keenon_vslam_node` | UNKNOWN (ref) |

### 2.7 Mapping

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/gmapping_map` | `nav_msgs/OccupancyGrid` (INFERRED) | `web_backend`, `gmapping` | UNKNOWN (ref) |
| `/map_app_start`, `/mapping_status` | `std_msgs/*` (INFERRED) | `web_backend` | UNKNOWN (ref) |
| `/scan_base_map`, `/switch_clean_slide_map`, `/map_slide`, `/combineMap` (srv) | `nav_msgs/OccupancyGrid`, `keenon_clean_msgs/combineMap` (def, srv) | `web_backend`, `switch_map`, `task_manager` | UNKNOWN (ref) |
| `/database/config_map`, `/database/config_dynamic_map`, `/database/config_buildmap_type` | `keenon_database_msgs/mapConfig`, `dynamicMapConfig`, `buildMapTypeConfig` (def, srv) | `database_node`, `cover_planner`, `interactive` | UNKNOWN (ref) |
| Cartographer internals (`cartographer_1` node, launched) | cartographer msgs | `cartographer` | UNKNOWN (ref) |
| `/vslam/mappingService`, `/vslam/rescanningProgress`, `/vslam/recommend_places` | `keenon_vslam_msgs/*` (def) | `keenon_vslam_node` | UNKNOWN (ref) |

### 2.8 Navigation

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/cmd_vel` | `geometry_msgs/Twist` (INFERRED) | `chassis`, `task_manager`, `move_base` | UNKNOWN (ref) |
| `/final_cmd_vel` | `geometry_msgs/Twist` (INFERRED) | `chassis`, `safe_decision` | UNKNOWN (ref) |
| `/move_base/goal` | `move_base_msgs/MoveBaseActionGoal` (INFERRED) | `fdl_ros`, `move_base` | UNKNOWN (ref) |
| `/move_base/TebLocalPlannerROS/*`, `/move_base/global_costmap/*`, `/move_base/local_costmap/costmap(_updates)`, `/move_base/move_base_global_plan` | costmap_2d / teb / nav_msgs (def via costmap-2d, teb-*-planner msgs) | `move_base` (kplanner + TEB), `keenonrecorder` | UNKNOWN (ref) |
| `/vel_map` | `std_msgs/*` (INFERRED) | `fdl_ros` | UNKNOWN (ref) |
| Cover/clean planners: `/cplanner/*` (allow_unknown, inflation_radius, use_dijkstra, outline_map ...), `/complement_paths`, `/all_path_replan`, `/personal_path_status` | `keenon_clean_msgs/*`, planner params | `cover_planner`, `task_manager` | UNKNOWN (ref) |
| `/app_cmd_navigation`, `/app_navigation_status`, `/current_nav_status`, `/clean_nav_status` | `keenon_ui_msgs/*`, `keenon_nav_msgs/*` (INFERRED/def) | `interactive`, `task_manager`, `elevator_task` | UNKNOWN (ref) |
| `/virtual_wall` | `keenon_nav_msgs/*` (INFERRED) | `web_backend`, `virtual_wall_generator` | UNKNOWN (ref) |

### 2.9 Cleaning hardware

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/clean_devices_control` | `keenon_clean_msgs/clean_device_control` (def: `clean_device_info[]` of `device_type`,`device_lock`) | `chassis` | UNKNOWN (ref) |
| `/clean_device_switch_status`, `/clean_devices_control`, `/clean_limit_speed`, `/clean_state`, `/clean_status` | `keenon_clean_msgs/clean_status` (def) etc. | `chassis`, `task_manager`, `task_dispatcher` | UNKNOWN (ref) |
| Motors / actuators: `/motor_sweep`, `/motor_roll`, `/motor_edge`, `/motor_edge_cover`, `/motor_air`, `/motor_roll_brush_rod_stm32`, `/motor_wash_brush_rod(_stm32)`, `/motor_wash_brush_switch`, `/disc_brush`, `/roll_brush`, `/rake_rod`, `/dis_rod` | `keenon_motor_msgs/*`, `std_msgs/*` (INFERRED) | `chassis` | UNKNOWN (ref) |
| Suction / vacuum: `/chassis/suction_switch`, `/debug_chassis/suction_switch`, `/steering_engine_switch` | `std_msgs/*` (INFERRED) | `chassis` | UNKNOWN (ref) |
| Water system: `/remaining_water/water_pump_value`, `/remaining_water/wash_brush_value`, `/remaining_water/current_cover_path`, `/remaining_water/is_speed_continuous`, `/remaining_water/navigating`, `/remaining_water/backtrack_path`, `/debug_chassis/start_water_value_and_water_time`, `/debug_chassis/stop_water_switch` | `keenon_clean_msgs/*`, `std_msgs/*` (INFERRED) | `chassis`, `task_manager` | UNKNOWN (ref) |
| Dust box / self-clean: `/BC_DustBox`, `/self_clean_task`, `/self_clean_task_status`, `/self_cleaning_mode` | `keenon_clean_msgs/self_clean_status` (def) | `chassis` | UNKNOWN (ref) |
| Carpet: `/carpet_detection_switch`, `/allow_carpet`, `/carpets_polygon`, `/real_carpet_cloud` | `keenon_clean_msgs/carpetStatus`, `supportCarpetAccess` (def) | `chassis`, `task_manager` | UNKNOWN (ref) |

### 2.10 STM32 serial communication (chassis / peripheral MCU bridge)

The `chassis` node is the ROS<->STM32/GD32 serial bridge (ROS-006/007; cross-ref firmware FW-001/002/016/017). Topics referenced by the `chassis` binary that bridge to the MCU:

| Topic | Direction (by naming) | Purpose |
|---|---|---|
| `/stm32_up`, `/stm32_down` | up = MCU->ROS, down = ROS->MCU (INFERRED from name) | Raw STM32 uplink/downlink frames |
| `/stm32_schedule_up`, `/stm32_down_schedule` | schedule board link | Schedule/ESP32 board frames (cross-ref FW-011) |
| `/stm32`, `/stm32_diagnosis_event`, `/stm32_diagnosis_status` | UNKNOWN | STM32 diagnosis |
| `/charge_cmd_toSTM32` | ROS->MCU (name) | Charging command to MCU |
| `/charge_state_fromSTM32` | MCU->ROS (name) | Charging state from MCU |
| `/encoder_raw`, `/pressure`, `/pressure_type`, `/psd1..4`, `/sonar1..4`, `/bump`, `/bump/status`, `/ground_type` | MCU->ROS (name, sensors) | Chassis sensors via serial |
| `/ros_charge_cmd`, `/charger_station_version`, `/charge_report_toUI`, `/charge_error_code` | mixed | Charging subsystem (cross-ref `keenon_charge_msgs`) |
| `/motor_lock`, `/motor_lock_status`, `/motor_error_code`, `/motor_speed` | mixed | Motor control/status via serial |

Serial parameters (from the Peanut SDK side, APK): `/dev/ttyS1-4`, `COM3RATE=921600`, `DEFAULT_RATE=115200`, `/dev/ttyUSBDoorCtl`. Firmware side (FW-016/017): framing `AA AA <payload+CRC16, A5-escaped> 55 55`, CRC-16 poly 0xA001; IAP uses 128-byte packets, SOH, ACK 0x06/NAK 0x15. The `/dev/ttyUSBSchedule` at 921600 baud is used by the MCU OTA tools (firmware `upgrade.sh`).

### 2.11 Robot status / diagnostics / errors

| Topic | Message type | Node | Dir |
|---|---|---|---|
| `/error_report`, `/error_report_handle`, `/ErrorInfo` | `keenon_diagnosis_msgs/*`, `keenon_clean_msgs/robot_monitor` (def) | nearly all nodes | UNKNOWN (ref) |
| `/robot_state` | `std_msgs/*` (INFERRED) | `web_backend` | UNKNOWN (ref) |
| `/battery_state` | `sensor_msgs/BatteryState` or `keenon_charge_msgs/*` (INFERRED) | `chassis` | UNKNOWN (ref) |
| `/diastatus_report`, `/stm32_diagnosis_status` | `keenon_diagnosis_msgs/*` (INFERRED) | `chassis` | UNKNOWN (ref) |
| `/event_tracking_report` | `keenon_event_tracking/*` (def) | `interactive`, `keenonrecorder` | UNKNOWN (ref) |
| `/robot_monitor` (android_monitor msg has `robot_monitor[] errorInfo`) | `keenon_clean_msgs/android_monitor`, `robot_monitor` (def) | `robot_monitor` node | UNKNOWN (ref) |

### 2.12 Infrastructure / app bridge / elevator-gate-lora (summary)

- **rosbridge / web:** `rosbridge_websocket` (9090), `rosbridge_websocket_control` (9091, `ns=control`, `authenticate=false`), `tf2_web_republisher`, `rosapi_node`. Browser/app topics like `/to_web`, `/web_command`, `/canvas_` (web_backend, interactive).
- **CoAP bridge** (`coap_bridge_server`, ROS-033): **SUB** `/libcoap/download` (`keenon_ui_msgs/download_data`: md5,url), **PUB** `/libcoap/download_result`. This is the one node with explicitly stated direction.
- **Database service hub** (`database_node`): dozens of `/database/config_*` services (map, pose, zone, path, elevator, gate, label, init_pose, robot_pos, sensorData) backed by `keenon_database_msgs/*Config` srv defs (CONFIRMED_STATIC def).
- **Elevator/gate/LoRa:** `/elev_lora_send/receive`, `/gate_lora_send/receive`, `/config_lora_channel`, `/lora_run_status`, `/Elevator/*`, `/elevator_*`, `/switch_dest_floor_map` (`lora_driver`, `elevator_task`, `elevator_position_detector`; `keenon_elevator_msgs`, `keenon_gate_msgs` defs).
- **Task layer:** `/TaskDispatcher/*`, `/CleanTask/Cmd`, `/app_cmd_clean_*`, `/start_clean`, `/console_task`, `/clean_task_snapshot_*` (`task_dispatcher`, `task_manager`, `interactive`; `keenon_task_msgs`, `keenon_clean_msgs` defs).

## 3. Message/service/action catalog (CONFIRMED_STATIC - definitions exist)

The `*-msgs` debs contain the full `.msg/.srv/.action` definitions (field-level). Key packages: `keenon_chassis_msgs`, `keenon_clean_msgs` (40+ msgs incl. `clean_status`, `clean_device_control`, `cover_path`, `self_clean_status`, `carpetStatus`, plus 40+ srv like `zone_paths`, `segmentation_map`, `OTA`, and actions `CleanPNC`, `AlongEdge`, `camera_calibration`), `keenon_database_msgs` (35 msg + 30 srv, full map/pose/zone/label/elevator schema), `keenon_charge_msgs`, `keenon_motor_msgs`, `keenon_sensor_msgs`, `keenon_nav_msgs`, `keenon_location_msgs`, `keenon_elevator_msgs`, `keenon_gate_msgs`, `keenon_ui_msgs`, `keenon_vslam_msgs`, `keenon_diagnosis_msgs`, `keenon_task_msgs`, `keenon_recorder_msgs`, `keenon_seller_msgs`, `keenon_label_msgs`. Field-level detail: `ros_packages.md` section 4. These are **definitions present in the package**, not proof the topics carrying them are live.

## 4. Confirmed-static vs live

| Aspect | Status |
|---|---|
| Message/service/action **definitions** exist in the package | CONFIRMED_STATIC (files present) |
| Topic **names** referenced by node binaries/launch | CONFIRMED_STATIC (strings present) |
| Node set for the C40 launch profile | CONFIRMED_STATIC (launch XML) / UNVERIFIED_RUNTIME (actually running) |
| Publisher/subscriber **direction** per topic | UNKNOWN except CoAP bridge (needs disassembly or live `rostopic info`) |
| Topic<->type **pairing** | mostly INFERRED (convention); a subset CONFIRMED via `.msg`/remap |
| Any topic actually **advertised at runtime** | UNVERIFIED_RUNTIME (no live graph observed) |

**Nothing in this inventory is `CONFIRMED_LIVE`.** To upgrade rows to live, a read-only `rostopic list -v` / `rosnode info` / `rostopic info <t>` against `192.168.64.20:11311` would be required and was not performed.
