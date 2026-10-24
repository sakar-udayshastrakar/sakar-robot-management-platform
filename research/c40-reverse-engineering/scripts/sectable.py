import json, glob, os
os.chdir(r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS')
rank = {'Critical': 0, 'High': 1, 'Medium': 2, 'Low': 3, 'Info': 4, None: 5, '': 5}
fl = []
for f in glob.glob('partials/*_findings.json'):
    for x in json.load(open(f, encoding='utf-8')):
        fl.append(x)

def esc(s):
    return str(s).replace('|', '\\|').replace('\n', ' ').replace('\r', ' ')

sec = [x for x in fl if x.get('domain') == 'SEC' or x.get('severity') in ('Critical', 'High', 'Medium', 'Low')]
sec.sort(key=lambda x: (rank.get(x.get('severity'), 5), x.get('id', '')))
lines = ['| ID | Sev | Status | Conf | Component | Title |', '|---|---|---|---|---|---|']
for x in sec:
    lines.append('| %s | %s | %s | %s | %s | %s |' % (
        x.get('id', ''), x.get('severity', ''), esc(x.get('status', ''))[:26], x.get('confidence', ''),
        esc(x.get('component', ''))[:55], esc(x.get('title', ''))[:120]))
open('_sec_table.md', 'w', encoding='utf-8').write('\n'.join(lines))
print('security rows', len(sec))

# per-domain full table for appendix
alld = sorted(fl, key=lambda x: x.get('id', ''))
lines = ['| ID | Domain | Sev | Status | Conf | Title |', '|---|---|---|---|---|---|']
for x in alld:
    lines.append('| %s | %s | %s | %s | %s | %s |' % (
        x.get('id', ''), x.get('domain', ''), x.get('severity', ''), esc(x.get('status', ''))[:24],
        x.get('confidence', ''), esc(x.get('title', ''))[:110]))
open('_all_findings_table.md', 'w', encoding='utf-8').write('\n'.join(lines))
print('all rows', len(alld))
