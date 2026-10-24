"""Hand-written findings for the SLAM/ROS package analysis. Secrets are redacted (type/location/length/first-2-chars only)."""
RS = 'ros-indigo-robot-settings'
FINDINGS = []
def F(id, domain, title, severity, component, evidence, status, confidence, impact, remediation, description):
    FINDINGS.append(dict(id=id, domain=domain, title=title, severity=severity, component=component, evidence=evidence,
                         status=status, confidence=confidence, impact=impact, remediation=remediation, description=description))

# ---------------- ROB ----------------
F('ROS-001','ROB','Software platform: ROS Indigo / Ubuntu Trusty, armhf userland, Rockchip-class SoC','Info','Whole package (106 debs)',
  'All 106 debs: control Architecture=armhf; 104 debs Version 2.3.1c1-4573-g1406f3b-trusty, ros-indigo-filters 1.1.1, ros-indigo-robotviz 2.6.1c1v4-4-g8006fd8-trusty. ELF scan of data.tar: 283 ELF files = 278 ARM32 (266 stripped + 12 unstripped) + 3 RISC-V32 (micro-laser firmware) + 2 x86-64; robot-settings postinst branches on `uname -p` (aarch64/x86_64); robot-settings firmware/rosconsole/aarch64/indigo/librosconsole.so is ELF ARM 32-bit; robot-settings usr/bin/user_monitor.sh references /sys/kernel/config/usb_gadget/rockchip/UDC and adbd.sh.',
  'CONFIRMED (armhf/trusty/indigo); STRONGLY_SUPPORTED (aarch64 kernel with 32-bit userland on a Rockchip SoC)','HIGH',
  'Defines the toolchain/ABI any custom node or replacement binary must match (32-bit ARM, glibc of Ubuntu 14.04, Boost 1.54, OpenCV 2.4, PCL 1.7/1.8).',
  'Build any added ROS nodes with the same cross toolchain (ros indigo, trusty armhf); do not assume a 64-bit userland.',
  'The whole stack is ROS Indigo (Python 2.7) built for Ubuntu 14.04 (trusty) armhf. Directory name firmware/rosconsole/aarch64 holds a 32-bit ARM library, and postinst checks for aarch64, which together indicate a 64-bit kernel running a 32-bit userland. user_monitor.sh refers to the Rockchip USB gadget configfs. This is a static inference; no live system was examined.')

F('ROS-002','ROB','Package is multi-model; C40 launch profile identified (keenon_robot_C40.launch)','Info',RS,
  'robot-settings ./opt/ros/indigo/share/robot_settings/launch/keenon_robot_C40.launch: ROBOT_TYPE=C40, ROBOT_GROUP=cleaning, LOCATION_TYPE=peanut_localization, LIDAR_TYPE=free, LIDAR_NUM=1, MONO=true, VSLAM=false, USE_CAMERA=false, USE_TOF=true (TOF_TYPE=sy_tof), USE_EDGE_LASER=true (XD x4), USE_BAGRECORD=true, USE_ANOMALIES_DETECT=true, SCHEDULE_SW=false, USE_DROP_RESISTENT=true, USE_LABEL=true, USE_CLEAN=true, USE_LASER_BACK=true. keenon_robot.launch (default) = W3/hotel. common_config.yaml robotTypeList lists 29 model names incl. C40, C40H, C55, C30, T*, G*, M*, W*, S100.',
  'CONFIRMED (launch defaults); UNKNOWN (which launch file is active on a given unit)','HIGH',
  'The package contains configuration for ~30 Keenon models; the active model on a unit is chosen by /home/peanut/launch_config/keenon_robot.launch which is preserved across updates (preinst/config_setting.py), not by this package alone.',
  'Confirm the active ROBOT_TYPE on the target unit (service keenonrobot launches launch/keenon_robot.launch) before relying on C40-specific parameters.',
  'keenon_robot.launch shipped in the package is the W3 default; config_setting.py restores keenon_robot_<ROBOT_TYPE>.launch from the user-preserved copy and re-applies user args. For a C40 the profile is keenon_robot_C40.launch which includes robot_common.launch.')

F('ROS-003','ROB','C40 runtime node composition (from robot_common.launch + C40 args)','Info',RS,
  'robot_common.launch includes (always): database, keenonrecorder, robot_state_publisher, rosbridge_websocket (+control on 9091), tf2_web_republisher, coap_bridge_server, web_backend, <LIDAR_TYPE>_<NUM> driver (free_1), lidar_filter_1, chassis, virtual_wall_generator, switch_map, peanut_localization, move_base_teb (kplanner + TebLocalPlannerROS), gmapping, cartographer_1, interactive, elevator_position_detector, topo_map_generator, lora_driver, matching_task, robot_settings_node, multi_goals_planner, schedule_map_builder, location_fusion, task_dispatcher, elevator_task, target_point_task. Conditional for C40: run_fdl (fdl_ros_node, USE_DROP_RESISTENT), anomalies_detection, label_fusion, keenon_vslam (MONO), safe_decision (default true), GS2/ydlidar back lidar (USE_LASER_BACK), USE_CLEAN group: cover_planner + complement_sweep, robot_monitor, task_manager, segmentation, sy_tof_1 (sy_tof_dev_node), XD_4 (auto_bind_node + micro_laser_node x3), calibration. Not launched for C40: schedule_ros (SCHEDULE_SW=false), dynamic_detector, astra/halley camera (USE_CAMERA=false), vslam, amcl, busy_map (commented out).',
  'CONFIRMED (static launch XML; STATIC_REFERENCE, not live-observed)','HIGH',
  'Gives the expected set of ROS processes for a C40 and the order of dependencies; useful as a baseline for live process/topic comparison later.',
  'Compare against `rosnode list` on a live unit to detect missing/disabled nodes.',
  'Parsed with Python ElementTree (an/launch.txt); rosbag.launch is not well-formed XML and could not be parsed (treated by text only).')

F('ROS-004','ROB','Mapping, localization and navigation architecture','Info','cartographer, peanut-localization, location-fusion, keenon-vslam, move-base, kplanner, teb-*',
  'cartographer include/cartographer_1.launch: cartographer_node -configuration_basename keenon_common.lua (2D, use_odometry=true, use_landmarks=true, tracking_frame=laser_link), cartographer_occupancy_grid_node -resolution 0.05, auto_denoise_node, label_filter_node; gmapping.launch (slam_gmapping). peanut_localization_node (150 param names, libpcl 1.8.1.99, libcreate_local_map.a). location_fusion_node (libmyorocos-bfl). move_base_teb.launch: base_global_planner=kplanner/KPlannerROS, base_local_planner=teb_local_planner/TebLocalPlannerROS; C40 move_base_params.yaml recovery_behaviors clear_static_recovery + rotation_escape_recovery. C40 teb params: max_vel_x 0.8, max_vel_theta 0.6/1.0, acc_lim_x 0.7/0.3. C40 costmap footprint is an 11-point polygon about 0.62 m long x 0.54 m wide.',
  'CONFIRMED (config/launch); INFERRED (runtime behaviour)','HIGH',
  'Mapping uses Cartographer 2D pbstream maps; run-time localization is a PCL-registration node fused (EKF/BFL) with labels and mono VSLAM; navigation is classic move_base with a custom global planner and TEB.',
  'None (descriptive).',
  'Two SLAM back-ends are launched (cartographer for map building, gmapping available), localization is separate (peanut_localization) and fused by location_fusion. Costmap observation sources for C40 include /scan, /fdl/obs_cliff, /fdl/obs_low, /sy_tof_dev_node/depth/points, /bump, /edge_laser_front_cloud, /low_cloud_left, /low_cloud_right, /mx/cloud1, carpet clouds (costmap_common_params.yaml).')

F('ROS-005','ROB','C40 sensor suite referenced by launch/config','Info','free-lidar-driver, ydlidar-ros-driver-v2, micro-laser, sy-sdk, fdl-ros, keenon-vslam, chassis',
  'free_1.launch -> free_lidar_driver_node, C40 sdkfree_params.yaml host 192.168.64.103 port 2111 (libc200_lidar.so strings: socket/connect/"Connecting socket to laser"). GS2.launch -> ydlidar_ros_driver_v2_node port /dev/ttyUSBLidar baud 921600 lidar_type 3 frame laser_back_link range_max 0.50. XD_4.launch -> auto_bind_node + micro_laser_node args /dev/ttyACM0..2 (edge_laser_front.yaml port /dev/ttyUSBAlongSideF, low_laser_left /dev/ttyUSBObstacleL, low_laser_right /dev/ttyUSBObstacleR). sy_tof_params.yaml (Sunny Optical ToF; camera_manager.sh USB id 2207:0028). run_fdl.launch -> fdl_ros_node (cliff/drop depth sensing via Orbbec/Berxel libs). keenon_vslam camera_list.txt usb:v0C45p6230 (mono USB camera). chassis params: size_sonar 1, size_psd 2, bump topics. Other lidars shipped but not C40 defaults: sdkeli_ls1207de (192.168.64.100:2112), ldlidar (LD06), ydlidar_ros_driver, astra/berxel cameras.',
  'CONFIRMED (static references)','HIGH',
  'Identifies the exact driver packages and device nodes that matter for C40 sensor troubleshooting.',
  'Verify device presence live (lsusb, ls /dev/ttyUSB* /dev/ttyACM*, ping 192.168.64.103).',
  'lidar_manager.sh auto-selects the lidar type by pinging 192.168.64.100 (keli), .103 (free), .107 (free_c260) and rewrites default= attributes in keenon_robot.launch with sed; camera_manager.sh selects mx/sy_tof by lsusb ids 2207:0006 / 2207:0028.')

F('ROS-006','ROB','Cleaning actuators controlled through the chassis node (brushes, water pump, suction fan, dust box)','Info','chassis, keenon-clean-msgs, keenon-ui-msgs',
  'chassis ELF strings (STATIC_REFERENCE): /water_pump, /water_suction_motor, /motor_air, /motor_edge, /motor_edge_cover, /motor_roll, /motor_sweep, /motor_wash_brush_rod, /motor_wash_brush_switch, /motor_roll_brush_rod_stm32, /rake_rod, /disc_brush, /roll_brush, /dis_rod, /BC_DustBox, /clean_devices_control, /clean_device_switch_status, /debug_chassis/start_water_value_and_water_time, /chassis/suction_switch, /self_clean_task, /carpet_detection_switch, /urgency_stop, /light_switch, /steering_engine_switch. C40 chassis_params.yaml: port /dev/ttyUSBStm32, wheel_gauge 377.4 mm, wheel_perimeter 439.2 mm, water_suction (fan gear) 80, WaterControlLow pulse control (water_pump_low 30), stage*_edge_brush_value, stage*_wash_brush_value/rod, stage*_roll_brush_value. keenon_clean_msgs: clean_device_control(device_type, device_lock), self_clean_status, carpetStatus, task_manager_report stage TASK_stage_residual_water. robot_monitor_params.yaml: dust_box error 140601, motor error bits 1404xx.',
  'CONFIRMED (strings/config); behaviour NOT_ANALYZED','HIGH',
  'Confirms a wet-scrubber style cleaning head: roll/wash brushes with lift rods, edge brush, vacuum/suction fan, water pump with pulse control and a dust box, all driven from an STM32 over serial.',
  'Live-test actuator topics only on a safe test unit; do not publish to them on a unit in service.',
  'No actuator is driven directly by a ROS node on the main CPU; the chassis node translates topics to an STM32 serial protocol (/dev/ttyUSBStm32). The protocol itself (libslam_comm_lib / chassis) was not reverse engineered.')

F('ROS-007','ROB','STM32 / peripheral MCU link and firmware update (IAP) path','Info','chassis, libcpprest (ros_update), robot-settings (usb_scan, auto_make_port.py)',
  'robot-settings usr/bin/usb_scan strings: symlink targets /dev/ttyUSBStm32, /dev/ttyUSBLoRa, /dev/ttyUSBLidar, /dev/ttyUSBLabel, /dev/ttyUSBSchedule, /dev/ttyUSBDoorCtl, /dev/aruco_location*, netlink monitor, GPIO power via /sys/class/gpio and /etc/power_*. ros_update (libcpprest usr/bin/ros_update) strings: "mode: peripheral board", "mode:direct download", "mode:indirect download", "Enter encoder IAP mode!", "wait bootloader signal...", "SOH:%03d" (XMODEM/YMODEM-style framing), crc16_ibm, MD5 check, /dev/ttyUSBStm32; function names update_stm32_from_bin. udev rules created by auto_make_port.py: KERNELS=="..", SUBSYSTEM=="tty", MODE:="0666", GROUP:="dialout", SYMLINK+="ttyUSB...". manifest/package here ships no STM32 .bin (base_station-app belongs to another package).',
  'CONFIRMED (strings); STRONGLY_SUPPORTED (STM32 IAP over serial from ros_update); framing details NOT_ANALYZED','MEDIUM',
  'The OTA service can reflash the peripheral MCU (and encoder board) over the serial link; the MCU firmware itself is not part of this package.',
  'Treat the update service as able to modify MCU firmware; protect it accordingly (see ROS-044).',
  'Device aliases ttyUSB* are created by usb_scan (netlink monitoring of USB/serial) rather than static udev rules (50-keenon.rules is emptied/removed by postinst).')

F('ROS-008','ROB','LoRa link for elevator and automatic gate control','Info','lora-driver, keenon-elevator-msgs, keenon-gate-msgs, elevator-task',
  'lora_driver ELF names (STATIC_REFERENCE): /elev_lora_send, /elev_lora_receive, /gate_lora_send, /gate_lora_receive, /config_lora_channel, /lora_run_status; C40 interactive_params.yaml serial_port /dev/ttyUSBLoRa, max_lora_wait_response_time 0.5, max_4g_wait_response_time 5; elevator_task_node names /elev_4g_send, /elevator_4g_request, /elevator_lora_request; keenon_gate_msgs (gate_cmd, gate_set_channel, gate_lora_driver); keenon_elevator_msgs/elev_lora_driver; elevator_params.yaml mqtt_server_ip (cloud broker IP, see ROS-035).',
  'CONFIRMED (static references)','HIGH',
  'Elevator calls and gate opening are radio (LoRa) / 4G / cloud driven; C40 profile has have_elevator=false by default.',
  'None.','Elevator control supports LoRa, a 4G path and a configured MQTT broker IP; whether C40 units use them depends on site configuration.')

F('ROS-009','ROB','Charging subsystem (auto-dock, cable, exit) via STM32','Info','keenon-charge-msgs, chassis, matching-task, location-fusion',
  'keenon_charge_msgs/ChargeTask.action goals AUTO_CHARGE_GOAL=1, DIRECT_CHARGE_GOAL=2, EXIT_CHARGE_GOAL=3, CABLE_CHARGE_GOAL=4 with about 28 REPORT_* codes (e.g. REPORT_match_charger_fail, REPORT_no_5V_detected, REPORT_charge_finish=65); ChargeCMD/ChargeFB; chassis topics /charge_cmd_toSTM32, /charge_state_fromSTM32, /ros_charge_cmd, /charger_station_version, /battery_state; C40 init_location_params.yaml use_charge_pile true, charge_pose_num 211; MoveBase goal types CHARGE_TARGET=3, MANUAL_CHARGE=5.',
  'CONFIRMED (msg definitions/strings)','HIGH',
  'Docking uses scan/label matching on the main CPU, with charge state handled by the STM32.',
  'None.','Charging logic is split between matching_task (docking approach) and chassis (STM32 charge FSM).')

F('ROS-010','ROB','Task layering: dispatcher -> target-point / charge / elevator / seller / clean tasks','Info','task-dispatcher, task-manager, target-point-task, elevator-task, matching-task, interactive',
  'keenon_task_msgs/TaskInfo taskType enum UndefinedTask/TargetPointTask/ChargeTask/ElevatorTask/SellerTask/CleanTask; TaskCmd (START/PAUSE/RESUME/STOP/SCH_PAUSE); task_dispatcher_node names /TaskDispatcher/Action, /TaskDispatcher/CurExecTaskInfo, /TargetPointTask/Cmd, /clean/clean_cmd; task_manager names /CleanTask/Cmd, /clean_state, /task_manager_report, /start_clean, /along_edge_path_origin, /complement_paths; keenon_clean_msgs/CleanPNC.action (EDGE_CLEAN/COVER_CLEAN/OMISSION_CLEAN, RUN/PAUSE/STOP).',
  'CONFIRMED (static)','HIGH','Reveals the command path an app command takes: UI -> interactive -> task_dispatcher -> task_manager/cover_planner -> move_base -> chassis.',
  'None.','See ROS-004 for planners; the tracing is by name only.')

F('ROS-011','ROB','Message / service / action catalog statistics','Info','all *-msgs packages and several service-bearing packages',
  'Parsed .msg/.srv/.action in 33 debs: 244 .msg (incl. auto-generated Action* wrappers), 172 .srv, 8 .action (ChargeTask, AlongEdge, CleanPNC, camera_calibration, Elev, MoveBase, topuMapAct, SellerTask). Largest: keenon_clean_msgs, keenon_database_msgs, keenon_ui_msgs, keenon_schedule_msgs, cartographer_ros_msgs.',
  'CONFIRMED','HIGH','Full interface contract for ROS-level integration (e.g. a custom controller) is available statically.',
  'Use as reference for building test clients against a safe test robot.','Catalogued in ros_packages.md section 4.')

F('ROS-012','ROB','Update package install flow (manifest.json, bin/keenon, script/app)','Info','manifest.json, bin/keenon, script/app',
  'manifest.json: 110 entries, keys only name/src/dest/mode/type (no checksum/signature field). 103 entries type=ros src=slam-ros-.../bin/ dest=bin/ mode=755; 3 entries type=3rdparty (filters, robotviz, remote-tools) dest="" mode=644; bin/keenon type=bin listed twice (dest bin/ and dest /etc/init.d/, mode 755); script/app type=app dest script/ mode 755; manifest.json type=manifest. bin/keenon and script/app (83 bytes each, identical): "cd /work/scripts; python guard.py &". ros_update strings: unzip -o, manifest.json, script/upgrade.sh, "service keenonrobot stop", "service cron stop", "dpkg -i --force-confnew", "apt-get -y -f install", reboot, /work/update/, /work/robot/, /work/data/, /tmp/robot/.',
  'CONFIRMED (manifest/scripts); STRONGLY_SUPPORTED (flow, inferred from ros_update strings)','MEDIUM',
  'The package installs the 106 debs via dpkg and drops an init.d script that starts guard.py (supervisor) from /work/scripts; guard.py itself is not in this package.',
  'Obtain guard.py from the device (/work/scripts) for analysis.',
  'script/upgrade.sh referenced by ros_update is not present in this package (script/ contains only app). The exact mapping of manifest dest/type to actions is inferred, not decompiled.')

F('ROS-013','ROB','Version stamping and dirty-version check','Info',RS,
  'robot-settings DEBIAN/postinst: update_Version="slam-ros-v2.10.9-c2-0-g58a75a7"; sed -i rewrites the first VERSION= line in /etc/os-release. usr/bin/check_version.py compares dpkg -l versions to /usr/bin/ros_version.ini (empty in this package) and appends "-g<ver>-dirty".',
  'CONFIRMED','HIGH','The reported system version string (slam-ros-v2.10.9-c2-0-g58a75a7) is written by this postinst, not by the OS image.',
  'None.','Useful to correlate the device /etc/os-release VERSION with this package.')

F('ROS-014','ROB','Startup services (upstart jobs)','Info',RS+', libcpprest, remote-tools',
  'etc/init: keenonrobot.conf (roslaunch keenon_robot.launch, respawn, ROS_IP=192.168.64.20, core dump to /home/peanut/keenon/core), robot_setup.conf, net_gw.conf, system_monitor.conf, monitor_script.conf, arm_setup.conf (taskset -pc 4,5 on move_base threads), create_swap.conf; libcpprest etc/init/ros_update.conf (respawn unlimited); remote-tools etc/init/Websocket_setup.conf (WsServer) and frpc_setup.conf (frpc call commented out); extra non-installed templates under share/robot_settings/system/upstart: keenonAP.conf (set_ap_mode), keenonUsbDebug.conf (getty on ttyUSBDebug), to_abandon/*. postinst removes legacy /etc/init.d/{robot_setup,keenonrobot,set_net_gw,set_up_frpc}; manifest installs /etc/init.d/keenon.',
  'CONFIRMED','HIGH','Ordered startup: networking -> robot_setup -> keenonrobot (roslaunch); ros_update and WsServer start independently of ROS.',
  'None.','The system runs under upstart (Ubuntu 14.04). keenonAP.conf and keenonUsbDebug.conf are shipped under share/ but not copied to /etc/init by this deb (UNKNOWN whether other components install them).')

F('ROS-015','ROB','Cron and periodic jobs added by robot_setup','Info',RS,
  'usr/bin/robot_setup appends to /etc/crontab: ros_log_clean.py (hourly then every 30 min), ros_core_clean.py (every minute), keenon_syslog_copy.sh (hourly), hwclock_sync.sh (03:00 daily, ntpdate cn.pool.ntp.org); sets timezone Asia/Shanghai, disables NTP (timedatectl set-ntp false), launches time_Synchronization.sh. postinst removes /etc/cron.daily/ros_log_clean. ros_update stops cron/anacron during updates.',
  'CONFIRMED','HIGH','Log retention and RTC sync are scripted; scripts run as root from /etc/crontab.','None.','The `[[ $size<1 ]]` tests are string/redirect expressions and the crontab replace logic is brittle; not tested.')

F('ROS-016','ROB','Referenced but not shipped components (gaps)','Info','launch files vs. package contents',
  'Launch nodes with no executable in the 106 debs: rosbridge_server/rosbridge_websocket, rosapi/rosapi_node, tf2_web_republisher (binary only as copy under robot-settings system/tf2_web_republisher), fdl_ros/fdl_ros_ob_sensor_node (run_fdl.launch), clean_task/clean_task, vslam/vslam_node, amcl/amcl, rviz, stage_ros, show_points/receive_depth_node, nodelet, robot_state_publisher, tf/static_transform_publisher, rosbag/play. ORB vocabulary /etc/ros/runtime/vslam/support_files/ORBvoc.bin, guard.py, script/upgrade.sh, nginx and Samba are not present.',
  'CONFIRMED (absence from this package only)','HIGH','These come from the base OS image or other packages (not analyzed here). Absence from this package must not be read as absence from the robot.',
  'Analyze the base rootfs/firmware image for rosbridge_server, nginx, guard.py.','Per rules, no conclusion about the robot is drawn from absence.')

F('ROS-017','ROB','Rosbag recording and event telemetry','Info','keenonrecorder, keenon-event-tracking',
  'keenonrecorder.launch: record_node prefix /home/peanut/keenon/idlebag/ros_idle_00000876, compression L, max_size 20, min_space 1G; replay_node; event_tracking_node database_file_path /etc/ros/runtime/database/statistics.db, event_zip_path /etc/ros/runtime/database/statistics.zip, yaml event_upload.yaml (codes like 1-2-7 laser_lost_tracking, 1-2-8 robot_pose_resetted, 1-5-1 robot_pose_risk_area); event_interface_node -i <code> inserts events from shell (used by time_Synchronization.sh, system_event_track.sh). config_rosbag.srv, taskDispatcher param rosbagStatus=true. No upload URL was found in these binaries (NOT proof of absence).',
  'CONFIRMED (config); upload path UNKNOWN','MEDIUM','Operational data (rosbag, SQLite statistics, core dumps) accumulates under /home/peanut/keenon and /etc/ros/runtime/database.',
  'Check which component (Android service) uploads statistics.zip.','Event codes are logged locally and zipped; uploading is not visible in this package.')

F('ROS-018','ROB','Diagnostics and error-code scheme','Info','robot-monitor, system-monitor scripts, keenon-ui-msgs',
  'robot_monitor_params.yaml (C40): motor error bits on /Motor_Error -> codes 140408/140413/140424/140429, dump detection on /imu_raw -> 140299, dust_box on /ErrorInfo -> 140601; system_monitor.sh publishes /error_report keenon_ui_msgs/error_report codes 111502 (CPU), 111503 (memory), 111505 (node coredump); sy/fdl etc. use /error_report; send_rostopic.sh helper.',
  'CONFIRMED','HIGH','Error codes are published on /error_report and surfaced by the UI.','None.','Complete code table not built; only samples above.')

# ---------------- DB ----------------
F('ROS-020','DB','SQLite database peanut.db schema (tables) and storage location','Info','database, keenonrecorder, schedule-map-builder, topo-map-generator',
  'database.launch: database_file_path=/etc/ros/runtime/database/peanut.db. database_node strings (sqlite3_open_v2, PRAGMA journal_mode = WAL, wal_autocheckpoint=100) with CREATE TABLE for: map, pose, init_pose, label, transform, gate, elevator, RobotPos, RobotSensorData, keyframe, semantic_map, dynamic_map, build_map_type, vslamKeyframe, path, zone, commonArea, AbilityTable, web_app_data, commonPath, param; indexes unique_map(type,floor), unique_label(label_id,floor), unique_keyframe, unique_dynamic_map, unique_build_type, unique_vslamKeyframe. Additional DBs: /etc/ros/runtime/database/statistics.db (tables event_tracking, event_tracking_summary); topo_path_data / topo_path_check (schedule_map_builder_node, topo_map_generator_node). No .db file is shipped in the debs (schema is created at runtime).',
  'CONFIRMED (table names from strings); column-level schema NOT_ANALYZED','MEDIUM',
  'Maps, poses (goal points), zones (cleaning areas), paths and calibration data live in one unencrypted SQLite file under /etc/ros/runtime/database.',
  'Pull peanut.db from a unit for column-level schema if needed.',
  'Services keenon_database_msgs/* (mapConfig, poseConfig, zoneConfig, pathConfig, ...) are the only intended access path (service names /database/config_*). The `manager.srv` and database_operate.srv (path, uuid) support import/export of whole DB zips (/etc/ros/runtime/database/uuidzip/).')

F('ROS-021','DB','Map and configuration file formats / locations','Info','robot-settings, database, web-backend, cartographer',
  'robot-settings cfg/maps/*.zip: empty.zip contains empty.pgm (16,000,045 bytes = 4000x4000 P5 PGM); Keenon_Elevator_Map.zip contains per-floor dirs 1..11 with map<N>.pgm, map<N>VirtualWall.pgm, map<N>GateWall.pgm (16,000,056 bytes each). map_config.msg: map_yaml/map_pgm/map_md5 + virtual_wall_* + gate_map_*. Runtime JSON/config paths from yaml: /etc/ros/runtime/pose/{pose,gate,elevator,init_pose}.json, /etc/ros/runtime/maps/{map,label}.json, /etc/ros/runtime/web/maps/layer.json, /etc/ros/runtime/web_receive/{map,virtual_wall}.pgm, /etc/ros/runtime/sensors/{lidar_type.txt,camera_type.txt,sy_tof_config.json}, /etc/ros/runtime/vslam/. cartographer srv Operatepbstream (md5, char[] data) and pbstreamConfig for .pbstream submap states. cfg/model/model.txt is a libsvm c_svc RBF model (939 SV). Config formats: ROS YAML (rosparam), OpenCV YAML (%YAML:1.0) for vslam/label/micro_laser, Lua for cartographer, JSON for calibration/FDL.',
  'CONFIRMED','HIGH','Map data are plain PGM images + per-floor virtual wall/gate layers; they can be exported/imported as zips.',
  'None.','The 16 MB PGM files indicate fixed 4000x4000 canvas maps at 0.05 m/cell (cartographer_occupancy_grid_node -resolution 0.05).')

F('ROS-022','DB','Runtime data and log directories','Info',RS+', keenonrecorder',
  'Paths referenced: /home/peanut/{launch_config,yaml_config,keenon/{core,log,bag,idlebag,systemlog,schedule},py_log,mx_ql,monitor_script,system_monitor_nmon,websocketlog.log}, /etc/ros/runtime/{database,maps,pose,sensors,vslam,web,web_receive,remotemap,frpc_info.ini}, /root/.ros/bag, /work/{scripts,robot,data,update}. robot-settings preinst: copies keenon_robot.launch and camera_*.yaml to /home/peanut/launch_config (if missing/empty) and recreates /home/peanut/yaml_config from cfg/yaml/common; postinst merges them back via config_setting.py / yaml_setting.py and deletes the staging dirs.',
  'CONFIRMED','HIGH','User-modified launch args and common_params.yaml persist across updates through the preinst/postinst merge.','None.','Common params live under the ROS parameter namespace /persistence_params.')

# ---------------- NET ----------------
F('ROS-030','NET','rosbridge WebSocket servers (ports 9090 and 9091) without authentication or TLS','High','robot-settings (rosbridge_websocket_control.launch), robotviz',
  'robot_common.launch includes $(find rosbridge_server)/launch/rosbridge_websocket.launch and include/rosbridge_websocket_control.launch: args port=9091, address="" , ssl=false, authenticate=false, ns=control, plus rosapi_node. robotviz Home.js contains ws://192.168.64.20:9090 and ws://192.168.64.20:9091; calibration/config.json host_url 192.168.64.199:9090; robotviz index.js also contains a developer ws://172.16.8.13:9092. postinst replaces rosbridge_library protocol.py/publishers.py and tornado iostream.py with vendor copies.',
  'CONFIRMED (launch args/client URLs); INFERRED (effective exposure; default port 9090 comes from upstream rosbridge_server which is not shipped)','MEDIUM',
  'Anyone who can reach the robot LAN (or the AP that is bridged into it) can publish/subscribe/call any ROS topic or service over WebSocket, including motion and configuration services.',
  'Bind to localhost or a management VLAN, enable authentication/SSL, firewall 9090/9091.',
  'No authenticate=true or certificate is configured; address is empty which in rosbridge means all interfaces. Not tested live.')

F('ROS-031','NET','ros_update OTA service listens on HTTP (default port 9527) with upgrade/action and upgrade/status resources','High','ros-indigo-libcpprest usr/bin/ros_update',
  'ros_update strings: "http://0.0.0.0:", adjacent literals "upgrade/action", "upgrade/status", "192.168.64.10", "9527", "application/json"; websocketpp 0.8.2 server code (QBaseWebsocketServer); resources via cpprest http_listener (GET/POST); classes QLUpdateResource (update_ros_from_zip, update_general_from_zip, update_stm32_from_bin, install_package, restart_ros, stop_other_services, check_install), KeenonShell (sudo_exec_cmd, dpkg_info, unzip_package); build path /workdir/catkin_ws/src/keenon_ros/keenon_infrastructure/rest_kits/src/ros_update/*. upstart job ros_update.conf: start-stop-daemon, respawn unlimited, runs as root.',
  'CONFIRMED (strings); port 9527 INFERRED from adjacent literal (LOW-MEDIUM); authentication behaviour UNKNOWN/NOT_ANALYZED','MEDIUM',
  'A root-run, plain-HTTP update endpoint reachable from the robot LAN accepts update requests containing a package URL + md5 (string "No obj url md5").',
  'Restrict to localhost/management network; require authentication and signed packages.',
  'No TLS code path (only http:// 0.0.0.0:). No credential/token strings were found in the binary, which is not proof that no authentication exists.')

F('ROS-032','NET','WsServer websocket control channel and frp reverse tunnel (remote SSH)','High','ros-indigo-remote-tools (package name "build")',
  'WsServer (ARM32 stripped, websocketpp 0.8.2) strings: actions frpRestart, frpConfig, frpMapConfig, frpDisconnect, frpState, debug_close; builds shell commands "python /usr/bin/update_frpc.py <serverAddr> <serverPort> <remotePort> <token> <taskNo>", "python /usr/bin/update_map_frpc.py", "nginx -s reload", "md5sum %s", writes /etc/nginx/conf.d/mapping.conf and unzips certificate.zip to /etc/ros/runtime/remotemap/. frpc_release_arm: Go 1.16.2, fatedier/frp (client + dashboard assets). frpc_info.ini template: [ssh_robotSn] type=tcp local_ip=127.0.0.1 local_port=22 remote_port=<> with server_addr/server_port/token blank. update_frpc.py starts "./frpc_release_arm -c /etc/ros/runtime/frpc_info.ini" and kills it after 12 h. upstart Websocket_setup.conf: sleep 10; exec /usr/bin/WsServer (log /home/peanut/websocketlog.log). frpc_setup.conf has the frpc start commented out. WsServer listening port is not recoverable by string analysis (UNKNOWN).',
  'CONFIRMED (strings/scripts); UNKNOWN (listening port, input sanitization)','MEDIUM',
  'A remote party able to send the websocket message can make the robot open an outbound frp tunnel that exposes local sshd (port 22) on a server chosen by the message; message fields are placed into shell command lines (possible command injection if unsanitized - not tested).',
  'Authenticate the websocket, validate/escape fields, bind to localhost, and pin server addresses.',
  'The tunnel exposes SSH only if the attacker also knows or guesses the SSH credentials. The frp server address and token are supplied at runtime, so none are present in this package.')

F('ROS-033','NET','CoAP bridge server and download client (libcoap)','Medium','ros-indigo-libcoap (coap_bridge_server, libcoap_bridge.so)',
  'libcoap_bridge.so symbol classes (from mangled names): CoapServer, CleanResource, DiagnosisResource, ElevatorResource, ErrorResource, FdlResource, MotorResource, NavigationResource, PositionResource, RuntimeResource, VendorResource, powerResource, ServiceClient, SimpleActionClient. coap_bridge_server strings: QLDownloadClient subscribes /libcoap/download (keenon_ui_msgs/download_data: md5,url) and publishes /libcoap/download_result; downloads into /etc/ros/runtime/database/uuidzip/, /tmp/robot/, /work/robot/ ; uses libcurl + libkQLTftp ("QLTftp,downloading,curl_easy_perform"); shell composition `echo "..." | sudo -S sh -c "..."` and `chmod -R`. CoAP port not found (libcoap default 5683 only appears in pdu.h).',
  'CONFIRMED (classes/strings); CoAP port UNKNOWN (default 5683 INFERRED, LOW)','MEDIUM',
  'A CoAP (UDP, unauthenticated by default) interface exposes robot clean/navigation/motor/position/runtime resources, and a ROS-topic-triggered downloader fetches files by URL+md5 and installs them via sudo shell commands.',
  'Restrict network exposure; use DTLS/PSK or remove; sign downloads.',
  'The resource handlers and their authorization were not decompiled.')

F('ROS-034','NET','Robot internal network design (192.168.64.0/24, bridge br0) and Wi-Fi AP bridged into it','Medium',RS,
  'etc/network/interfaces: br0 bridge_ports eth0 eth1, address 192.168.64.20/24, gateway 192.168.64.10; etc/default/isc-dhcp-server INTERFACES="br0"; wifi/dhcpd.conf subnet 192.168.64.0/24 range 192.168.64.200-209, routers 192.168.64.20; etc/hostapd/hostapd.conf: interface=wlanAP, ssid=Keenon_robot_FFFFFF (last 6 hex digits rewritten from wlanAP MAC by set_ap_mode), bridge=br0, wpa=1, WPA-PSK, CCMP; net_gw.conf forces default route via 192.168.64.10 dev br0; upstart env ROS_IP=192.168.64.20; legacy rosenv ROS_MASTER_URI=http://192.168.64.20:11311; udev 70-persistent-net.rules names USB eth* as ethdbg (set_ethdbg_mode adds it to br0 and restarts dhcp).',
  'CONFIRMED (config); effective on-device state UNKNOWN','HIGH',
  'Lidars, modules, the Android UI board and the ROS master share one flat L2/L3 segment that the Wi-Fi AP and any debug Ethernet port are bridged into.',
  'Separate the AP/debug interfaces from the control network; firewall inter-segment access.',
  'Wired peers: UI 192.168.64.10 (gateway), robot 192.168.64.20, lidars .100/.101/.103/.104/.107, calibration host .199 (config.json), UWB 172.16.1.33:9000. (192.168.64.10 identified as gateway and also appears next to the OTA port string.)')

F('ROS-035','NET','Other hard-coded endpoints (cloud IP, devices, MX stereo module, NTP)','Info',RS+', check_connect, show-points',
  'elevator_params.yaml (all models incl. C40): mqtt_server_ip 47.96.156.255, topics elevtopic_test / robottopic_test; usr/bin/check_connect SERVER_IP="47.96.156.255" (4G ping check, route via 10.64.64.64); MX stereo module IP 10.11.21.1 (vslam_available.sh, check_mx_stereo; module_tool wget ftp://<ip>/param/* with --ftp-user=anonymous); send_depth_node tcp://%s:%s (ZeroMQ + libsodium crypto_secretbox); time sync via ntpdate cn.pool.ntp.org; DHCP DNS 8.8.8.8, 114.114.114.114; resolv.conf nameserver 8.8.8.8 appended by robot_setup.',
  'CONFIRMED','HIGH','Shows outbound dependencies: one fixed cloud IP, China NTP pool, public DNS; MX module is reached over FTP anonymous and ZMQ.',
  'Review whether the test topic names / fixed IP should ship in production.','No MQTT client code was found in any ELF; the broker parameter consumer is UNKNOWN.')

F('ROS-036','NET','4G modem and ppp support scripts (largely legacy)','Info',RS,
  'system/conf/wvdial.conf (APN UNIM2M.NJM2MAPN, Phone *99#, Modem /dev/ttyUSB4G0); to_abandon/keenon4G.conf, check_net.conf; usr/bin/check_connect (usb0/ppp0 monitoring, start/stop keenon4G). max_4g_wait_response_time in interactive_params.yaml.',
  'CONFIRMED (files); STRONGLY_SUPPORTED (legacy, in to_abandon)','MEDIUM','Cellular fallback exists in older designs; may be unused on C40.','None.','The upstart keenon4G job itself is not installed by this package.')

F('ROS-037','NET','robotviz web UI (static Vue app) endpoints','Info','ros-indigo-robotviz',
  '/usr/share/robotviz: index.html title "Keenon", assets index/Home/Login/AboutModal .js; title string "Keenon Robot Mapping Tool"; roslib connections ws://192.168.64.20:9090 and :9091; calls /rosapi/*, /database/manager, /database/config_map, /robot_settings/cfg_launch_type_cmd, /robot_settings/cfg_origin_floor_cmd, /robot_settings/cfg_schedule_sw_cmd, /mx_config_srv, /reboot, /motor_lock, /manual_loop_closure, /operate_pbstream, /initialpose, /cmd_vel, /move_base/goal, /topo_action/*. Manifest type 3rdparty. HTTP server for the static files is not part of the package (nginx referenced by WsServer).',
  'CONFIRMED (client code strings)','HIGH','A browser-based mapping/commissioning tool controls the robot directly through rosbridge, including drive (/cmd_vel), reboot, and config services.',
  'Protect with network controls (see ROS-030, ROS-045).','STATIC_REFERENCE names.')

# ---------------- SEC ----------------
F('ROS-040','SEC','Hard-coded sudo password inside send_depth_node binary','High','ros-indigo-show-points opt/ros/indigo/lib/show_points/send_depth_node',
  'ELF string: shell command "echo <PASSWORD> | sudo -S service keenonrobot restart" (password: 11 lowercase chars, starts "ke", value redacted).',
  'CONFIRMED','HIGH','Anyone with the package can learn a root/sudo password that is the same string used elsewhere (ROS-041, ROS-042); likely valid on production units (not verified).',
  'Remove hard-coded passwords; use a restricted sudoers rule or service API; rotate the credential.',
  'The same 11-character value also appears as the Wi-Fi AP passphrase and in WsServer; it is also the name of the main service, so it is trivially guessable.')

F('ROS-041','SEC','Hard-coded password passed on command line to frpc_delete / sudo -S (WsServer and script)','High','ros-indigo-remote-tools usr/bin/WsServer, usr/bin/frpc_delete',
  'WsServer ELF string: "/usr/bin/frpc_delete \'<PASSWORD>\' &" (11 chars, starts "ke", redacted); frpc_delete script: `keen=$1; echo ${keen}|sudo -S kill -9 $li`.',
  'CONFIRMED','HIGH','Credential exposure in the binary and in process arguments (visible via ps).','Replace with root-owned service logic; no passwords in argv.',
  'The password equals the value in ROS-040 (same 11-char string).')

F('ROS-042','SEC','Default Wi-Fi AP credentials shipped in hostapd.conf (WPA-PSK), AP bridged to robot network','High',RS+' etc/hostapd/hostapd.conf',
  'hostapd.conf: ssid=Keenon_robot_FFFFFF (suffix replaced by AP MAC), wpa=1, wpa_key_mgmt=WPA-PSK, wpa_pairwise=CCMP, wpa_passphrase=<11 chars, starts "ke", redacted>, bridge=br0; file is a dpkg conffile of robot-settings. Also wifi/wpa_supplicant.conf contains a 64-hex PSK for SSID "testAP" (starts "ca", redacted).',
  'CONFIRMED (file); on-device value UNKNOWN (may be changed)','MEDIUM',
  'If unchanged, anyone who knows the default passphrase (shared by all units, derived from a guessable string) joins the robot control LAN including unauthenticated rosbridge/ROS master.',
  'Provision per-device random passphrases; use WPA2-only; do not bridge the AP to the control network.',
  'wpa=1 selects WPA (v1) with CCMP.')

F('ROS-043','SEC','Unauthenticated ROS master / rosbridge / services control (including config and reboot)','High',RS+', robotviz, ROS master',
  'rosbridge authenticate=false (ROS-030). Services/topics reachable by name through rosbridge (STATIC_REFERENCE): robot_settings/launchConfig.srv (option, cmdKey, cmdValue -> edits launch config), /robot_settings/cfg_launch_type_cmd, /reboot, /motor_lock, /cmd_vel, /cfg_param_cmd, keenon_ui_msgs/param_update, cfg_param_info, database_operate (path,uuid), keenon_clean_msgs/OTA.srv (file_path, md5). ROS master is started by roslaunch with default port 11311 on ROS_IP 192.168.64.20 (upstart env).',
  'CONFIRMED (interfaces exist); INFERRED (reachability and lack of access control)','MEDIUM',
  'A LAN-adjacent attacker can drive the robot, change launch/robot-type configuration, trigger reboot or push OTA files.',
  'Network segmentation, rosbridge auth, restrict service exposure.',
  'No live validation was performed.')

F('ROS-044','SEC','OTA update flow lacks integrity/authenticity controls and runs as root','High','manifest.json, ros-indigo-libcpprest ros_update, debs',
  'manifest.json entries contain no hashes/signatures; debs contain only debian-binary, control.tar.gz, data.tar.xz (no signature members); ros_update uses md5 only ("check md5 success/failed", mbedtls_md5) with md5 supplied alongside the URL ("No obj url md5"); installs with `dpkg -i --force-confnew` then `apt-get -y -f install`, executes script/upgrade.sh, stops cron, reboots; manifest places bin/keenon at absolute dest /etc/init.d/ ; maintainer scripts run as root; robot-settings postinst executes `sudo cp` over files in /opt/ros/indigo/lib/python2.7/dist-packages.',
  'CONFIRMED (package structure/strings); INFERRED (authorization model)','MEDIUM',
  'An attacker able to supply a package URL+md5 to ros_update (or tamper in transit/at rest) gets root code execution through maintainer scripts/init scripts.',
  'Sign packages (detached signature verified on device), verify against pinned key, TLS + authentication for the update API.',
  'Severity could be Critical if ros_update is proven reachable and unauthenticated; authentication was not analyzed.')

F('ROS-045','SEC','robotviz login is client-side only with hard-coded key/ciphertext and pre-filled default credentials','Medium','ros-indigo-robotviz assets/Login.396f4042.js',
  'Login.js: form state initialised with username "peanut" and a 6-digit numeric default password (starts "12", redacted); onLogin compares AES-ECB encryption of the JSON form object (CryptoJS, key const = 8-char string starting "PE", parsed as hex) to a hard-coded 96-hex-char constant (starts "52"); on match router pushes /home. No server round-trip.',
  'CONFIRMED (code); whether the constant corresponds to the default credentials NOT_ANALYZED','HIGH',
  'Authentication can be bypassed by anyone with browser dev tools or by connecting to rosbridge directly; the secret material is in the client bundle.',
  'Do real server-side authentication; remove defaults.',
  'The effective key of CryptoJS Hex.parse(<8-char key string>) is only 4 bytes (non-hex characters parse to 0) - INFERRED from library semantics, LOW confidence.')

F('ROS-046','SEC','CoAP bridge builds sudo shell commands and downloads archives by URL without signature','Medium','ros-indigo-libcoap coap_bridge_server',
  'Strings: `echo "` / `" | sudo -S sh -c "` / `chmod -R `, /etc/ros/runtime/database/uuidzip/, /tmp/robot/, /work/robot/, QLTftp curl_easy_perform; topic /libcoap/download (url, md5) with md5 only.',
  'CONFIRMED (strings); injection exploitability NOT_ANALYZED','MEDIUM','Download URLs/paths flow into root shell commands; md5 gives integrity only against corruption.',
  'Use execve with argument arrays, sign downloads, restrict who can publish /libcoap/download.','Password operand source of the `sudo -S` pipe was not recovered.')

F('ROS-047','SEC','Samba shares exposing /home/peanut and /root/.ros (read-write) and /etc/ros/runtime','Medium',RS+' system/conf/smb.conf, postinst',
  'smb.conf: [old log path] path=/root/.ros read only=no; [config] path=/etc/ros/runtime read only=yes; [home] path=/home/peanut read only=no; guest ok=no; map to guest = bad user; unix password sync = yes. postinst: `if [ -d "$myPath"]; then cp $SMBCONF_FILE /etc/samba/` - $myPath is undefined and there is no space before `]`; bash evaluates `[ -d "" ]` as the one-argument form `[ -d ]` which is true, so the copy runs if /etc/samba exists (INFERRED, not executed).',
  'CONFIRMED (config); INFERRED (copy executes); Samba presence/users UNKNOWN','MEDIUM','If Samba is installed and a weak password exists, remote read/write of home, root-owned log dir and runtime configs is possible.',
  'Remove shares or restrict to localhost; use strong unique credentials.','Shares require Samba authentication (guest ok = no).')

F('ROS-048','SEC','Device nodes created world read/write (MODE 0666) by udev rules and scripts','Low',RS+', ydlidar-ros-driver',
  '56-orbbec.rules MODE:="0666" for Orbbec USB; 50-usb-debug.rules ttyUSB 067b:2303 MODE 0666 -> ttyUSBDebug (getty login console via keenonUsbDebug.conf template); auto_make_port.py rule template MODE:="0666", GROUP:="dialout"; ydlidar-ros-driver usr/startup/initenv.sh writes ydlidar*.rules MODE 0666.',
  'CONFIRMED','HIGH','Any local user can access serial/USB devices including the STM32 and debug console.','Use group-restricted modes.','No world-writable regular files exist in the debs (checked all 7,879 file entries: only modes 644/755, plus 44 symlinks at 777; no setuid/setgid).')

F('ROS-049','SEC','End-of-life software stack','Medium','Whole package',
  'ros-indigo (EOL Apr 2019), Ubuntu 14.04 trusty (EOL 2019), Python 2.7 scripts (print statements; libpython2.7), libssl.so.1.0.0/libcrypto.so.1.0.0 (OpenSSL 1.0.x) in ros_update/libcoap, Boost 1.54, OpenCV 2.4, PCL 1.7 and 1.8.1.99, Go 1.16.2 frpc, libcpprest 2.10, websocketpp 0.8.2, nlohmann json 3.11 in 3 binaries.',
  'CONFIRMED (names/versions)','HIGH','Known unpatched vulnerabilities likely exist in system libraries; cannot be remediated within this package.','Plan platform upgrade; isolate network exposure meanwhile.','CVE mapping was not performed (no network access).')

F('ROS-050','SEC','Debian packages unsigned; control files carry no md5sums; manifest has no checksums','Medium','all 106 debs, manifest.json',
  'Every deb ar members = debian-binary, control.tar.gz, data.tar.xz; no _gpg* members; no other control files besides control/maintainer scripts/conffiles (checked all 106).',
  'CONFIRMED','HIGH','No offline way to verify provenance of the 106 debs.','Add signing and checksums.','Integrity relies on the single md5 supplied at update time.')

F('ROS-051','SEC','Wvdial guest/guest credentials and legacy SSH public key shipped','Low',RS+' system/conf/wvdial.conf, system/robot.pub',
  'wvdial.conf Username/Password both "guest" (carrier APN placeholder); system/robot.pub: ssh-rsa public key, 401 bytes, not referenced by any script in the package (grep of authorized_keys/robot.pub found only roslaunch remoteprocess.py).',
  'CONFIRMED','HIGH','Guest credentials are low value; the public key may be an authorized key used by the vendor (usage UNKNOWN).','Remove unused keys/files.','Public keys are not secrets; no private key was found (searched for BEGIN ... PRIVATE KEY).')

F('ROS-052','SEC','Postinst overwrites system libraries and binaries with vendor copies','Low',RS+' DEBIAN/postinst',
  'sudo rm/cp of rosbridge_library/protocol.py, internal/publishers.py, tornado/iostream.py into /opt/ros/indigo/lib/python2.7/dist-packages; replaces /opt/ros/indigo/lib/tf2_web_republisher/tf2_web_republisher (x64/arm32 variants, chmod 755); copies librosconsole.so into /opt/ros/indigo/lib; removes /usr/local/lib/libusb*; edits /etc/bash.bashrc (HISTTIMEFORMAT), /etc/os-release, /etc/udev/rules.d/50-keenon.rules (truncated), runs `python config_setting.py`, `python yaml_setting.py`, user_monitor_update.sh.',
  'CONFIRMED','HIGH','Non-package-managed file replacement breaks package integrity tracking and makes rollbacks hard.','Package the patches properly.','Contents of the patched files were not diffed against upstream.')

F('ROS-053','SEC','Thermal trip points raised and ADB re-enabled by user_monitor script','Low',RS+' usr/bin/user_monitor.sh, user_monitor_update.sh',
  'user_monitor.sh: writes 90000 to thermal_zone0/trip_point_0_temp and 100000 to trip_point_1_temp every 5 s; restarts /etc/init.d/adbd.sh if the USB gadget UDC is empty. user_monitor_update.sh copies it to /etc/init.d when /etc/fw_version < v1.0.9.',
  'CONFIRMED (script); whether installed on units UNKNOWN','MEDIUM','Overrides SoC thermal protections (safety/durability) and keeps an ADB interface alive.','Review thermal limits with SoC vendor; disable ADB in production.','Script is in the to_abandon list but still installed to /usr/bin.')

F('ROS-054','SEC','All robot services run as root; unlimited core dumps written to /home/peanut/keenon/core','Low',RS+' etc/init/keenonrobot.conf',
  'Upstart jobs run as root; `ulimit -c unlimited`; core_pattern set to /home/peanut/keenon/core/core-%e-%p-%t; post-stop `kill -9` of all processes matching "opt/ros".',
  'CONFIRMED','HIGH','Any ROS node compromise yields root; core dumps may leak memory contents (maps, credentials).','Drop privileges for nodes; restrict core dump location/permissions.','')

F('ROS-055','SEC','Build-environment path leakage and bundled debug symbols','Info','ros-indigo-libcpprest, libcoap, busy-map, fdl-ros, micro-laser',
  'Strings like /workdir/catkin_ws/src/keenon_ros/keenon_infrastructure/rest_kits/..., keenon_interface/ros2cmd/libcoap/...; busy-map deb contains ./workdir/catkin_ws/** (28 entries); fdl-ros ships /usr/lib/debug/... symbol copies; unstripped: libOpenNI2, liborbbec, libudev, libusb copies, libsy_tof_dev_sdk.so (11,031 symbols), micro-laser firmware ELFs (1,176/1,898 symbols).',
  'CONFIRMED','HIGH','Eases reverse engineering of those components; reveals internal source tree names.','Strip binaries before release.','Other binaries (90 ARM executables) are stripped.')

F('ROS-056','SEC','Packaging anomaly: ros-indigo-remote-tools deb declares Package: build','Info','ros-indigo-remote-tools_2.3.1c1-4573-g1406f3b-trusty_armhf.deb control',
  'control: Package: build, Source: build (0.0.0-0trusty), Description: "The remote ssh package", Version 2.3.1c1-4573-g1406f3b-trusty; installed-size 8827 kB. File list: WsServer, frpc_release_arm, update_frpc.py, update_map_frpc.py, frpc_delete, frpc_info.ini, 2 upstart confs.',
  'CONFIRMED','HIGH','dpkg registers it as "build" (generic name, collision/overwrite risk; hard to identify during forensics).','Rename the package.','')

F('ROS-057','SEC','Remote-tools tunnel client and command stack executes strings built from message fields','Medium','remote-tools update_frpc.py, update_map_frpc.py',
  'update_map_frpc.py: set_file_value(msg) does `eval(data_json)["data"]["config"]` on a JSON string passed on argv (Python 2 eval), and writes arbitrary key/value pairs into /etc/ros/runtime/frpc_info.ini (frp config) which can define arbitrary tunnels (type, local_ip, local_port, remote_port, plugin). update_frpc.py writes server_addr/server_port/remote_port/token from argv into the frp ini.',
  'CONFIRMED (code)','HIGH','Given control of the websocket message (ROS-032), arbitrary Python expression evaluation and arbitrary local-port exposure through frp become possible.','Use json.loads and a whitelist of ini fields.','Reachability of the message source depends on ROS-032 exposure.')

F('ROS-058','SEC','Fixed cloud broker IP and test topic names shipped in production configuration','Info',RS+' cfg/yaml/*/elevator_params.yaml',
  'mqtt_server_ip: 47.96.156.255; sub_server_topic elevtopic_test; pub_server_topic robottopic_test (all 30 model directories).',
  'CONFIRMED','HIGH','Production units may talk to a test topic namespace; endpoint is hard-wired.','Make configurable.','See ROS-035.')

# ---------------- NAT ----------------
F('ROS-070','NAT','Native binary inventory (ELF)','Info','all debs',
  'Scanned data.tar of all 106 debs: 283 ELF (186 shared objects/DYN, 97 EXEC). 266 stripped ARM32; unstripped: 10 ARM32 libs, 2 ARM32 firmware ELFs, 3 RISC-V32 firmware ELFs; 2 x86-64 (robot-settings x64 tf2_web_republisher and X86 check_mx_stereo). 90 stripped ARM executables: ROS node executables, helper tools (usb_scan, module_tool, fixture_calibrate, as_nuwacam_node, fdl_calibration_node, nmon, ethtool, schedule-reset, mx_config, check_mx_stereo), ros_update, WsServer and frpc_release_arm (Go, no DT_NEEDED). Per-binary list in ros_packages.md section 5.4.',
  'CONFIRMED','HIGH','All binaries are closed-source vendor builds; analysis limited to headers/symbols/strings.','Use Ghidra on selected binaries (ros_update, WsServer, coap_bridge_server, chassis) for deeper work.','No disassembly or decompilation was performed.')

F('ROS-071','NAT','Bundled third-party code and versions','Info','peanut-localization, libcpprest, libcoap, remote-tools, cartographer',
  'peanut-localization ships libpcl_*.so.1.8.1.99 (29 MB libpcl_features etc.) while other nodes link libpcl_*.so.1.7; ros_update/WsServer embed websocketpp 0.8.2 + asio + mbedtls md5; libcpprest.so.2.10; coap_bridge via libcoap (+libcurl); frpc Go 1.16.2 with fatedier/frp; nlohmann json 3.11.2 in depth_laser_cali_node, astra_camera_node, calibration_node; libkzipc embeds miniz; show_points/module_tool embed libsodium (crypto_secretbox); cartographer links protobuf .so.14, cairo, lua5.2, glog/gflags; costmap/TEB/navfn are upstream ROS indigo code (versions 1.12.14 etc.).',
  'CONFIRMED (names/versions from sonames/strings)','HIGH','Mixed PCL ABI versions on one device; any CVE audit must cover these bundles.','SBOM generation recommended.','')

F('ROS-072','NAT','micro_laser firmware blobs are Linux userland ELF apps for the line-laser modules (RISC-V32 and ARM32)','Info','ros-indigo-micro-laser share/micro_laser/firmware',
  'ql_xd1q_v821_33_33_20260603, ql_xd1q_v821_33_35_20260603, ql_xd1q_v821_3_34_20260603: ELF32 RISC-V (e_machine 243), musl (/lib/ld-musl-riscv32.so.1), 306,112 bytes, symtab 1,176 symbols, build string "Jun  5 2026 12:08:19", classes App_V3s_7251_Vga_X1_XD1Q, hHandleCMD, tcp_server_*, uart_parse_frame; strings: upload/replace /root/main and /root/config.txt, "dmesg | grep Linux version", gpio 135 export. ql_xd1q_202_35 and ql_xd1q_2_33: ARM32 ELF (260,936 bytes). auto_bind_node/micro_laser_node strings: "(5/5) Update firmware ...", SN read, left/right auto-binding, GPIO power restart, /dev/ttyACM0..3.',
  'CONFIRMED (headers/strings); behaviour NOT_ANALYZED','MEDIUM','The robot pushes executable firmware to external Linux-based sensor modules over the CDC-ACM serial link at start-up; integrity of that push is unauthenticated by design (INFERRED).','Treat module firmware as part of the supply chain; sign.','The build date string (Jun 5 2026) shows this release was built mid-2026.')

F('ROS-073','NAT','Vendor depth-camera SDK blobs bundled in fdl-ros (Orbbec OpenNI2, Berxel)','Info','ros-indigo-fdl-ros, berxel-camera',
  'fdl-ros lib/fdl/arm32: libfdl.so (contains JSON config text with "host_url":"192.168.64.199:9090"), libdepthsInterface.so, libmapobs.so, libfruit.so, libRobotOperation.a, sensors/depthAB/OpenNI2/Drivers/{liborbbec.so,libOniFile.so,orbbec.ini,libusb-1.0*,libudev*}, sensors/depthBS/{libBerxelUvcDriver.so,params100Q.bin 6,144,000 bytes}; /usr/lib/debug copies with symbols; berxel-camera ships libBerxelHawk and params100Q.bin.',
  'CONFIRMED','HIGH','Drop/cliff detection relies on a depth camera SDK with its own USB stack copies (libusb/libudev) shadowing system ones; postinst deletes /usr/local/lib/libusb*.','Ensure library path ordering does not load the wrong libusb.','')

F('ROS-074','NAT','Embedded protocol/crypto use: ZeroMQ+libsodium to MX module; md5 only in OTA','Info','show-points, robot-settings module_tool, ros_update',
  'send_depth_node and module_tool: strings tcp://%s:%s, crypto_secretbox / crypto_secretbox_open, username/password length checks (username.length () <= UCHAR_MAX) consistent with a ZeroMQ/libsodium based RPC; ros_update/libcoap use mbedtls_md5 / libkmd5 for integrity.',
  'CONFIRMED (strings); protocol NOT_ANALYZED','LOW','MX stereo module link is encrypted with a symmetric secretbox (key source unknown) and also reachable by anonymous FTP for parameter files.','Review key management.','')
