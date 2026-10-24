"""Merge worker partials into ARTIFACT_INVENTORY.csv and FINDINGS_REGISTER.csv (+ coverage report)."""
import json, glob, os, csv, sys, collections
T = r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246'
R = os.path.join(T, 'REVERSE_ENGINEERING_RESULTS')
P = os.path.join(R, 'partials')
inv = json.load(open(os.path.join(R, 'inventory_base.json'), encoding='utf-8'))['artifacts']

def load(pattern):
    out = []
    for f in sorted(glob.glob(os.path.join(P, pattern))):
        try:
            d = json.load(open(f, encoding='utf-8'))
            if isinstance(d, dict):
                for k in ('findings', 'artifacts', 'records', 'items'):
                    if k in d and isinstance(d[k], list):
                        d = d[k]; break
            for r in d:
                if isinstance(r, dict):
                    r['_src'] = os.path.basename(f)
                    out.append(r)
        except Exception as e:
            print('WARN cannot load', f, e)
    return out

arts = load('*_artifacts.json')
amap = {}
suffix_index = []  # (normalized_worker_path, record)
for a in arts:
    k = (a.get('relative_path') or '').replace('\\', '/').lstrip('/')
    amap.setdefault(k, []).append(a)
    if k and '!/' not in k:
        suffix_index.append((k, a))

def match(rp):
    rpn = rp.replace('\\', '/').lstrip('/')
    if rpn in amap:
        return amap[rpn]
    # suffix match: worker path is a trailing segment of the inventory path, or vice versa
    best = []
    for wp, a in suffix_index:
        if rpn == wp or rpn.endswith('/' + wp) or wp.endswith('/' + rpn):
            best.append(a)
    return best

rows = []
uncovered = []
for r in inv:
    rp = r['relative_path']
    recs = match(rp)
    if recs:
        a = recs[0]
        row = [rp, r['file_name'], r['file_type'], r['size_bytes'], r['sha256'], r['md5'],
               a.get('analyzed'), a.get('method', ''), a.get('findings', ''), a.get('status', ''), a.get('limitations', ''), a['_src']]
    else:
        row = [rp, r['file_name'], r['file_type'], r['size_bytes'], r['sha256'], r['md5'], False, '', '', 'NOT_ANALYZED', 'No analysis record produced by any worker', '']
        uncovered.append(rp)
    rows.append(row)
with open(os.path.join(R, 'ARTIFACT_INVENTORY.csv'), 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh)
    w.writerow(['relative_path', 'file_name', 'file_type', 'size_bytes', 'sha256', 'md5', 'analyzed', 'analysis_method', 'findings', 'analysis_status', 'remaining_limitations', 'record_source'])
    w.writerows(rows)
print('inventory rows', len(rows), 'uncovered', len(uncovered))
open(os.path.join(R, 'work', 'uncovered_artifacts.txt'), 'w', encoding='utf-8').write('\n'.join(uncovered))

fl = load('*_findings.json')
seen = collections.Counter(f.get('id') for f in fl)
dups = [k for k, v in seen.items() if v > 1]
print('findings', len(fl), 'duplicate ids', dups)
cols = ['id', 'domain', 'title', 'severity', 'component', 'status', 'confidence', 'evidence', 'impact', 'remediation', 'description', 'reproduction', 'source']
with open(os.path.join(R, 'FINDINGS_REGISTER.csv'), 'w', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh)
    w.writerow(cols)
    for f in fl:
        def s(v):
            if isinstance(v, (list, dict)): return json.dumps(v, ensure_ascii=False)
            return '' if v is None else str(v)
        w.writerow([s(f.get(c)) if c != 'source' else f['_src'] for c in cols])
sev = collections.Counter(f.get('severity') for f in fl if f.get('domain') == 'SEC')
print('security severities', dict(sev))
