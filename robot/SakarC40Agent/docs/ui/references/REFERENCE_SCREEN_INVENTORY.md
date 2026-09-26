# Reference Screen Inventory

**Phase 2A: frozen inventory of the reference screenshots discovered under
`docs/ui/references/screenshots/`.** Every image listed below is treated as immutable reference
material (see `README.md`). This file only records what exists and what it visually shows. It does
**not** decide whether a screen belongs in the Sakar application, does **not** mark anything as
duplicate or orphaned, and does **not** invent routes, features, measurements, or implementation
ownership - those are separate, later decisions for `FEATURE_REGISTRY.json`, `ROUTE_REGISTRY.md`,
and `COMPONENT_REGISTRY.md`, none of which were touched by this pass.

All paths below are relative to `docs/ui/references/screenshots/`.

## Fields

| Field | Meaning |
|---|---|
| `reference_id` | Stable identifier for this image within this inventory. |
| path | Relative image path under `screenshots/`. |
| resolution | Pixel dimensions, read directly from the file. |
| screen/group | Which of the 14 groups below this image belongs to. |
| parent screen | The screen this appears to be reached from, only when obvious from the filename/folder structure or from a visible in-app breadcrumb/sidebar selection - `not obvious` otherwise. |
| notes | Recorded only when directly supported by visual inspection of the image itself. |

---

## Home

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| HOME-01 | `keenonwebpic/keenonwebpic/cleaning app/home.png` | 963x555 | (top-level) | Home dashboard: top toolbar, robot illustration, 4 quick-action cards (Start Cleaning, Scheduled Cleaning, Teaching Mode, Manual Drive) |
| HOME-02 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-29 17-08-29.png` | 963x555 | not obvious from filename/folder | Modal dialog "Recover Positioning Using the Guide" with Method 1/2/3 and a "Positioning Failed" status; sits directly in the `cleaning app/` root, not a subfolder, so its trigger screen cannot be confirmed from the file location alone |

## Cleaning

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| CLEAN-01 | `keenonwebpic/keenonwebpic/cleaning app/start cleaning.png` | 963x555 | Home | "Start Cleaning Now" screen: map/area selection list, "Start Clean" button |

## Manual Drive / Manual Push

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| MANUAL-01 | `keenonwebpic/keenonwebpic/cleaning app/manual drive.png` | 963x555 | Home | Cleaning-mode selector (Sweep & Mop / Water Suction / Sweep & Vacuum / Sweep & Push / Sweep) with intensity picker (Gentle/Standard/Powerful) and "Start Cleaning" button |
| MANUAL-02 | `keenonwebpic/keenonwebpic/cleaning app/manual push.png` | 963x555 | Home | "Manual Push" screen: illustration of a person pushing the robot, "Manually End Pushing" button |

## Scheduled Cleaning

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| SCHED-01 | `keenonwebpic/keenonwebpic/cleaning app/in side schdule clean.png` | 963x555 | Home | "Scheduled Task" list with existing tasks and a "+ New Task" action |
| SCHED-02 | `keenonwebpic/keenonwebpic/cleaning app/edit task.png` | 963x555 | Scheduled Cleaning (filename indicates task edit) | "Edit Task" form: Task Name, Task Type, Clean Cycle, Scheduled Start Time, Cleaning Counts, Cleaning Area, Cleaning Mode |

## Teaching Mode

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| TEACH-01 | `keenonwebpic/keenonwebpic/cleaning app/teaching mode.png` | 963x555 | Home | "Teach Route" list, empty state ("No routes yet"), "+ Create Route" action |
| TEACH-02 | `keenonwebpic/keenonwebpic/cleaning app/in side teaching mode.png` | 963x555 | Teaching Mode | Operation-steps dialog (push the robot along the route, notes about not recording near elevators/staircases) with a "Next" button |

## Settings

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| SET-01 | `keenonwebpic/keenonwebpic/cleaning app/settings.png` | 963x555 | Home | Settings sidebar + "Consumables Statistics" pane selected |
| SET-02 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-29 17-07-42.png` | 963x555 | Settings | Consumables Statistics pane (Side Brush, Sweeping Brush, Fibre brush, Washing Brush) |
| SET-03 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-53-57.png` | 663x516 | Settings | Consumables Statistics pane, scrolled (Squeegee Blade, HEPA, Dust Bag) |
| SET-04 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-54-43.png` | 663x516 | Settings | Consumables Statistics pane, scrolled (Fibre brush, Washing Brush, Dust Mop Brush) |
| SET-05 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-55-27.png` | 982x537 | Settings | "Cleaning Data" pane (Cleaning Record, Cleaning Settings) |
| SET-06 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-56-05.png` | 982x537 | Settings | "Workstation Settings" pane (Water Refill, Drainage, Workstation Self-Clean, Cleaning Agent) |
| SET-07 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-56-45.png` | 982x537 | Settings | "Charging Settings" pane (Idle Battery Protection, Auto-recharge Level, Charging Interval, Task Battery Protection, Minimum Operating Battery Level) |
| SET-08 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-57-02.png` | 982x537 | Settings | "Screen Lock Settings" pane (Lock Screen Password, Screen Lock Duration) |
| SET-09 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-57-34.png` | 982x537 | Settings | "General Settings" pane, top section (Language, Select Point to Locate, Manual Charging Location, Resume Cleaning from Breakpoint, Sweeping Brush Auto-height Adjustment) |
| SET-10 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-57-53.png` | 982x537 | Settings | "General Settings" pane, scrolled to Wi-Fi section (network list) |
| SET-11 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-58-08.png` | 982x537 | Settings | "Screen Emoji" pane (eye animation toggle, Promotional Content) |
| SET-12 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-58-24.png` | 982x537 | Settings | "Sound Settings" pane (Music Volume, Voice Volume, Music During Cleaning/Navigation) |
| SET-13 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-58-35.png` | 982x537 | Settings | "Do Not Disturb (DND) Mode" pane (Music DND, Task DND) |
| SET-14 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-59-01.png` | 982x537 | Settings | "Resource Management" pane (SR Cleaning, SR Butler, Ckpc, Demo, Carpet) |
| SET-15 | `keenonwebpic/keenonwebpic/cleaning app/Screenshot from 2026-08-31 12-59-12.png` | 982x537 | Settings | "About the Robot" pane (MAC Address, Pairing Code, QR code, Open Mapping App) |

## Super User

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| SU-01 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Screenshot from 2026-08-31 13-03-01.png` | 982x537 | Super User sidebar | "Network" pane (Wi-Fi toggle, network list) |
| SU-02 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Screenshot from 2026-08-31 13-03-22.png` | 982x537 | Super User sidebar | "Resource Management" pane |
| SU-03 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Screenshot from 2026-08-31 13-04-43.png` | 982x537 | Super User sidebar | "General Settings" pane |
| SU-04 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Screenshot from 2026-08-31 13-05-10.png` | 982x537 | Super User sidebar | "Robot Debugging" pane - row list (Robot Debugging, Return to charge stress test, Wash Pressure Test, Industrial computer/ROS connection test) |
| SU-05 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Screenshot from 2026-08-31 13-05-55.png` | 976x572 | Super User sidebar | "System Settings" pane |

## Robot Debugging

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| DEBUG-01 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/Industrial Computer ROS/Screenshot from 2026-08-31 13-11-08.png` | 976x572 | Super User > Robot Debugging | "Industrial computer/ROS connection test" screen |
| DEBUG-02 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/Return to charge stress test/Screenshot from 2026-08-31 13-09-29.png` | 976x572 | Super User > Robot Debugging | Recharge stress test screen (Recharge Count, Start/Passed) |
| DEBUG-03 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/Water Pressure Test/Screenshot from 2026-08-31 13-10-17.png` | 976x572 | Super User > Robot Debugging | "Wash Pressure Test" screen (zoneId, Main/Sub Cycle Count, Cleaning Status, Start Clean) |
| DEBUG-04 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/Screenshot from 2026-08-31 13-08-21.png` | 976x572 | Super User > Robot Debugging | Debug dashboard, top section: Emergency Stop/Hub Status, IMU, Battery |
| DEBUG-05 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/robot debug 1.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Hub Speed, pressure sensor, water volumes, Carpet Detection, Fan/Component & Speed Test |
| DEBUG-06 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/robot debug 2.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Side Brush/Washing Brush RPM+speed level, Raise/Lower, embedded joystick (Forward/Turn Left/Stop/Turn Right/Reverse) |
| DEBUG-07 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/robot debug 3.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Sweeping Brush/Dust Mop Brush RPM, Handle Adjustment, Raise/Lower, Clean Water Pump / Washing Nozzle flow rates |
| DEBUG-08 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/robot debug 4.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Front Roller Brush (Sweeping Brush) + Side Brush - Forward/Reverse Rotation, Speed, Lowering Distance, Fan (Vacuum) |
| DEBUG-09 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/robot debug 5.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Fan/Water Suction, Workstation Settings (Water Refill, Drainage pumps, Detergent Pump) |
| DEBUG-10 | `keenonwebpic/keenonwebpic/cleaning app/Super User/Robot Debugging/robot debug/rebot debug 6.png` | 976x572 | Super User > Robot Debugging | Debug dashboard: Light Strips (Left/Right), Anti-collision Bumper (Left/Medium/Right), Geomagnetic Detection (Left/Right X/Y/Z). Filename contains a typo ("rebot" instead of "robot") |

## Robot Installation / Maps

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| INSTMAP-01 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 18-22-21.png` | 963x555 | not obvious from filename/folder | "Map deployment" list (SR Cleaning, SR Buttler, SR Pallet cards, "+New" action) |
| INSTMAP-02 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-13-38.png` | 976x572 | not obvious from filename/folder | Map deployment list, same content as INSTMAP-01 with a language dropdown open |
| INSTMAP-03 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 18-27-58.png` | 963x555 | not obvious from filename/folder | "Set up" sidebar (Basic Settings/Volume Setting/Network Settings/Business settings/Advanced setting) with "Basic Settings" pane selected (serial number, model, wireless network name, robot IP, versions) |
| INSTMAP-04 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-14-45.png` | 976x572 | not obvious from filename/folder | Set up > Basic Settings pane, same content as INSTMAP-03 |
| INSTMAP-05 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-15-02.png` | 976x572 | Set up sidebar | Set up > Volume Setting pane (current volume slider) |
| INSTMAP-06 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-15-13.png` | 976x572 | Set up sidebar | Set up > Network Settings pane (wireless LAN list) |
| INSTMAP-07 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-15-52.png` | 976x572 | Set up sidebar | Set up > Advanced setting pane (Select area where robot is located, Function switch, Relocation, Scheduling module settings, Date settings, Restore factory settings, Quit) |
| INSTMAP-08 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 20-11-58.png` | 963x555 | not obvious from filename/folder | Map route-drawing editor over a floor plan ("Implement all the routes that robots need to walk") |
| INSTMAP-09 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 20-14-56.png` | 963x555 | not obvious from filename/folder | Map zone editor (Remove zone / Set restriction area / Custom Area actions) |
| INSTMAP-10 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 20-15-19.png` | 963x555 | not obvious from filename/folder | Map zone editor, a marked area visible on the floor plan |
| INSTMAP-11 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-29 20-15-37.png` | 963x555 | not obvious from filename/folder | Map zone editor with colored zones (Divide Area / Generate path / Carpet path actions) |

## Business Settings

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| BIZ-01 | `keenonwebpic/keenonwebpic/robot installation/Screenshot from 2026-08-31 13-15-23.png` | 976x572 | Set up sidebar | Set up > "Business settings" pane (Elevator settings, Scheduling path, Remote control robot) |
| BIZ-02 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Screenshot from 2026-08-31 13-17-31.png` | 976x572 | Set up sidebar | Same "Business settings" pane content as BIZ-01 |

## Elevator / Remote Control / Scheduling Path

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| ELEV-01 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Elevator settings/Screenshot from 2026-08-31 13-18-06.png` | 976x572 | Business settings | "Elevator settings" submenu (Elevator robotid configuration, Elevator list configuration, Draw Elevator) |
| ELEV-02 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Elevator settings/Draw Elevator/Screenshot from 2026-08-31 13-20-58.png` | 976x572 | Elevator settings | "Draw Elevator" map tool (Auto-generated/Clear Elevator/Draw Elevator/Complete) |
| ELEV-03 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Elevator settings/Elevator List Configuration/Screenshot from 2026-08-31 13-20-41.png` | 976x572 | Elevator settings | "Elevator list configuration" screen, empty list, "Preserve" action |
| ELEV-04 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Elevator settings/Elevator robotid/Screenshot from 2026-08-31 13-20-02.png` | 976x572 | Elevator settings | "Elevator robotid configuration" screen ("Modify local robotid" button) |
| ELEV-05 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Remote control robot/Screenshot from 2026-08-31 13-23-04.png` | 976x572 | Business settings | "Remote control robot" screen - QR code for phone pairing |
| ELEV-06 | `keenonwebpic/keenonwebpic/robot installation/Business settings/Scheduling path/Screenshot from 2026-08-31 13-22-24.png` | 976x572 | Business settings | "Scheduling path" map editor (path nodes/route, Generate path/Complete) |

## Keenon Service

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| KSVC-01 | `keenonwebpic/keenonwebpic/keenon services/Screenshot from 2026-08-29 18-47-22.png` | 963x555 | (top-level of a separate app) | "Keenon Service" screen (SN, Version) - visually a distinct app from the cleaning app, not a sub-screen of it |

## krlog

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| KRLOG-01 | `keenonwebpic/keenonwebpic/krlog/Screenshot from 2026-08-29 18-40-49.png` | 963x555 | (top-level of a separate app/tool) | "krlog工具" (log tool) screen - Log file size, log retention count, export/clear-and-re-record actions; UI text is in Chinese |
| KRLOG-02 | `keenonwebpic/keenonwebpic/krlog/Screenshot from 2026-08-29 18-43-07.png` | 963x555 | krlog tool | Export-file-selection dialog over the same krlog screen, listing `log_YYYYMMDD` entries |

## Remote Assistant

| reference_id | path | resolution | parent screen | notes |
|---|---|---|---|---|
| RA-01 | `keenonwebpic/keenonwebpic/Remote Assistant/Screenshot from 2026-08-29 20-21-12.png` | 963x555 | (top-level of a separate app) | "KEENON Remote Assistant" landing screen, info icon, "About" label |
| RA-02 | `keenonwebpic/keenonwebpic/Remote Assistant/Screenshot from 2026-08-29 20-21-50.png` | 963x555 | Remote Assistant | "About" detail screen (SN, Wlan Ip, Build Type, Version, UI Version, upgrade action) |

---

## Video reference material (not a screenshot)

| path | notes |
|---|---|
| `keenonwebpic/keenonwebpic/cleaning app/Screencast from 08-29-2026 05_02_42 PM.webm` | A screen recording, not a still screenshot. Flagged as video reference material per instruction; intentionally excluded from the image inventory above and from any `reference_image` field. |

## Housekeeping issues (flagged only, not acted on)

1. **Nested duplicate path**: every reference image lives under
   `docs/ui/references/screenshots/keenonwebpic/keenonwebpic/...` - the `keenonwebpic` folder
   appears to contain a copy of itself one level down, doubling the path depth for no apparent
   reason. Not moved or deleted per instruction.
2. **Inconsistent capture resolutions** for what look like the same physical device/app across
   different capture sessions: 963x555, 982x537, 976x572, and 663x516 all appear among these
   images (e.g. `home.png` at 963x555 vs the Super User panes mostly at 982x537 or 976x572). This
   may reflect different browser-window/emulator sizes at capture time rather than different
   devices - not something this inventory pass resolves.
3. **Filename typo**: `rebot debug 6.png` (DEBUG-10) - "rebot" instead of "robot". Left as-is.

---

## Summary

- **Total reference images (screenshots): 63**
- **Groups: 14** (Home, Cleaning, Manual Drive / Manual Push, Scheduled Cleaning, Teaching Mode,
  Settings, Super User, Robot Debugging, Robot Installation / Maps, Business Settings,
  Elevator / Remote Control / Scheduling Path, Keenon Service, krlog, Remote Assistant)
- **Video reference material: 1** (`.webm`, excluded from the screenshot count above)
