import os, re, glob, json
R = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS'
SCR = r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad'

# --- recover secret literals WITHOUT printing them, to scan deliverables for leaks ---
secrets = []
sd = glob.glob(os.path.join(SCR, 'debwork', 'ros-indigo-show-points*', '**', 'send_depth_node'), recursive=True)
if sd:
    d = open(sd[0], 'rb').read()
    m = re.search(rb'echo\s+(\S{6,20})\s*\|\s*sudo\s+-S', d)
    if m:
        secrets.append(('sudo_pw/wifi_psk', m.group(1)))
hostapd = glob.glob(os.path.join(SCR, 'debwork', 'ros-indigo-robot-settings*', '**', 'hostapd.conf'), recursive=True)
if hostapd:
    for line in open(hostapd[0], 'rb').read().splitlines():
        if line.startswith(b'wpa_passphrase='):
            secrets.append(('wifi_psk', line.split(b'=', 1)[1]))

# scan every deliverable (md/csv/json, excluding the raw inventory/embedded which are hashes only)
deliverables = [p for p in glob.glob(os.path.join(R, '*.*')) if p.lower().endswith(('.md', '.csv', '.json'))]
deliverables += glob.glob(os.path.join(R, 'partials', '*.*'))
leaks = []
for p in deliverables:
    try:
        b = open(p, 'rb').read()
    except Exception:
        continue
    for tag, sec in secrets:
        if sec and len(sec) >= 6 and sec in b:
            leaks.append((os.path.relpath(p, R), tag))
print('secret literals recovered for scan:', len(secrets))
print('LEAKS FOUND:', len(leaks))
for l in leaks:
    print('  LEAK', l)

# required deliverables present?
required = ['MASTER_REVERSE_ENGINEERING_REPORT.md', 'ARTIFACT_INVENTORY.csv', 'FINDINGS_REGISTER.csv',
            'ANDROID_APPLICATION_ANALYSIS.md', 'SDK_API_CATALOG.md', 'NATIVE_BINARY_ANALYSIS.md',
            'NETWORK_PROTOCOL_ANALYSIS.md', 'DATABASE_SCHEMA_ANALYSIS.md', 'ROBOTICS_SYSTEM_ARCHITECTURE.md',
            'APPLICATION_WORKFLOWS.md', 'SECURITY_AUDIT.md', 'UNRESOLVED_GAPS.md',
            'REVERSE_ENGINEERING_SUMMARY.md', 'INVESTIGATION_STATE.md',
            'ROS_IP_IDENTIFICATION.md', 'ROS_TOPICS_INVENTORY.md', 'ROS_COMMUNICATION_ARCHITECTURE.md',
            'ROS_IP_TOPICS_FINDINGS.json']
missing = [r for r in required if not os.path.isfile(os.path.join(R, r))]
print('required deliverables missing:', missing if missing else 'NONE')

# JSON parse check
badjson = []
for p in glob.glob(os.path.join(R, '*.json')) + glob.glob(os.path.join(R, 'partials', '*.json')):
    try:
        json.load(open(p, encoding='utf-8'))
    except Exception as e:
        badjson.append((os.path.basename(p), str(e)[:60]))
print('JSON parse failures:', badjson if badjson else 'NONE')

# original target files untouched (spot check: upgradeConfig.json + a few)?
print('original upgradeConfig.json present:', os.path.isfile(os.path.join(os.path.dirname(R), 'upgradeConfig.json')))
