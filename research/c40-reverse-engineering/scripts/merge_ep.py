import json, glob, csv, os
os.chdir(r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS')

def rows(pat):
    out = []
    for f in glob.glob('partials/' + pat):
        src = os.path.basename(f)
        d = json.load(open(f, encoding='utf-8'))
        if isinstance(d, dict):
            for v in d.values():
                if isinstance(v, list):
                    d = v
                    break
        for r in d:
            if isinstance(r, dict):
                r['source_file'] = src
                out.append(r)
    return out

def cell(v):
    if isinstance(v, (list, dict)):
        return json.dumps(v, ensure_ascii=False)
    return '' if v is None else str(v)

ep = rows('*_endpoints.json')
keys = ['kind', 'value', 'source', 'status', 'confidence', 'source_file']
with open('NETWORK_ENDPOINTS.csv', 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh)
    w.writerow(keys)
    for r in ep:
        w.writerow([cell(r.get(k)) for k in keys])
print('endpoints', len(ep))

rr = rows('*_ros_refs.json')
allk = set()
for r in rr:
    allk.update(r.keys())
pref = ['name', 'type', 'kind', 'category', 'direction', 'node', 'message_type', 'value', 'source', 'status', 'confidence', 'source_file']
keys = [k for k in pref if k in allk] + [k for k in sorted(allk) if k not in pref]
with open('ROS_STATIC_REFERENCES.csv', 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh)
    w.writerow(keys)
    for r in rr:
        w.writerow([cell(r.get(k)) for k in keys])
print('ros_refs', len(rr), 'cols', keys[:10])
