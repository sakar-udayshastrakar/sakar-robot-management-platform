import json, os, sys, zipfile, collections
T = sys.argv[1]
inv = json.load(open(os.path.join(T, 'REVERSE_ENGINEERING_RESULTS', 'inventory_base.json'), encoding='utf-8'))
by = {r['relative_path']: r for r in inv['artifacts']}
cfg = json.load(open(os.path.join(T, 'upgradeConfig.json'), encoding='utf-8'))
print('== MD5 check vs upgradeConfig.json ==')
for s in cfg['subPkgInfo']:
    n = s['subPkgName']
    r = by.get(n)
    if r:
        print(n, 'size', r['size_bytes'] == s['subPkgSize'], 'md5', r['md5'] == s['subPkgMd5'])
    else:
        print(n, 'NOT PRESENT AS FILE (zip package; extracted dir present?)')
print('== APK structure ==')
for r in inv['artifacts']:
    if r['file_name'].endswith('.apk'):
        z = zipfile.ZipFile(os.path.join(T, r['relative_path']))
        names = z.namelist()
        c = collections.Counter()
        for n in names:
            if n.endswith('.dex'): c['dex'] += 1
            elif n.endswith('.so'): c['so'] += 1
            elif n.startswith('assets/'): c['assets'] += 1
            elif n.startswith('res/'): c['res'] += 1
            elif n.startswith('META-INF/'): c['meta-inf'] += 1
            else: c['other'] += 1
        print(r['file_name'], len(names), dict(c))
        print('  dex:', [n for n in names if n.endswith('.dex')])
        print('  so:', len([n for n in names if n.endswith('.so')]))
        print('  META-INF:', [n for n in names if n.startswith('META-INF/')][:12])
