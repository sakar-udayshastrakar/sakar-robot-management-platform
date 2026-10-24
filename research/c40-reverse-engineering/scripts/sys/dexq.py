import sys, pickle, re, os
B = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
app, mode = sys.argv[1], sys.argv[2]
rx = re.compile(sys.argv[3]) if len(sys.argv) > 3 else None
extra = re.compile(sys.argv[4]) if len(sys.argv) > 4 else None
D = pickle.load(open(os.path.join(B, 'sysidx', app + '.pkl'), 'rb'))
def short(l): return [x for x in l]
if mode == 'cls':
    for c, e in sorted(D.items()):
        if rx.search(c):
            print(c, 'extends', e['super'], e.get('ifaces', ''), e['dex'], len(e['methods']), 'methods')
elif mode == 'm':   # dump method detail for classes matching; extra=filter on invokes
    for c, e in sorted(D.items()):
        if not rx.search(c): continue
        print('###', c, 'extends', e['super'], e.get('ifaces', ''))
        for k, i in e['methods'].items():
            if extra and not (extra.search(k)): continue
            print('  -', k)
            if i['str']: print('     S:', ' | '.join(i['str'])[:int(os.environ.get('W','900'))])
            inv = [x.replace('Lcom/keenon/krlog/utils/LogcatHelper;->','LH.').replace('Landroid/','A/').replace('Ljava/lang/','J/') for x in i['inv'] if not x.startswith(('Ljava/lang/StringBuilder', 'Ljava/lang/Object', 'Lkotlin/jvm/internal'))]
            if inv: print('     I:', ', '.join(inv)[:int(os.environ.get('W','900'))])
            if i['fld']: print('     F:', ', '.join(i['fld'])[:600])
            if i.get('insn') and os.environ.get('RAW'): print('     RAW:', i['insn'])
            if i['new']: print('     N:', ', '.join(i['new'])[:400])
elif mode == 'ms':   # strings + selected invokes only
    for c, e in sorted(D.items()):
        if not rx.search(c): continue
        print('###', c)
        for k, i in e['methods'].items():
            sel = [x for x in i['inv'] if re.search(os.environ.get('IV', r'Runtime|ProcessBuilder|exec|Shell|Socket|URL|Http|PackageInstaller|Settings|File|Zip|Mqtt|ContentResolver|startService|bindService|sendBroadcast|startActivity|Intent;->put'), x)]
            if i['str'] or sel:
                print('  -', k)
                if i['str']: print('     S:', ' | '.join(i['str'])[:int(os.environ.get('W','700'))])
                if sel: print('     I:', ', '.join(sorted(set(sel)))[:int(os.environ.get('W','700'))])
elif mode == 'str':
    for c, e in sorted(D.items()):
        for k, i in e['methods'].items():
            for s in i['str']:
                if rx.search(s):
                    print(c + '#' + k, '=>', s[:300]); break
elif mode == 'inv':
    for c, e in sorted(D.items()):
        for k, i in e['methods'].items():
            h = [x for x in i['inv'] if rx.search(x)]
            if h: print(c + '#' + k, '=>', ', '.join(sorted(set(h)))[:300])
elif mode == 'new':
    for c, e in sorted(D.items()):
        for k, i in e['methods'].items():
            h = [x for x in i['new'] if rx.search(x)]
            if h: print(c + '#' + k, '=>', ', '.join(sorted(set(h)))[:300])
