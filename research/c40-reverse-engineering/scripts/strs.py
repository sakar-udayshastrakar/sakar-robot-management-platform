"""Usage: strs.py <file> [regex] [minlen] -- print ASCII strings of a binary, skipping C++ mangled/symbol noise unless regex given."""
import re,sys
p=sys.argv[1]
rx=re.compile(sys.argv[2]) if len(sys.argv)>2 and sys.argv[2] else None
ml=int(sys.argv[3]) if len(sys.argv)>3 else 6
d=open(p,'rb').read()
seen=set()
for m in re.finditer(rb'[\x20-\x7e]{%d,}'%ml,d):
    s=m.group().decode('ascii')
    if re.match(r'^(_Z|N\d|St\d|Z[NK]|\d+[A-Z]|_ZT)',s) or 'N5boost' in s[:12]: continue
    if rx is None:
        if s.startswith(('_Z','_ZN','N5boost','N3ros','St','NSt')) or '_ZN' in s[:6]: continue
        if re.match(r'^[A-Za-z0-9_.]+$',s) and not ('/' in s) and len(s)<12 and not s.islower(): continue
    else:
        if not rx.search(s): continue
    if s in seen: continue
    seen.add(s)
    print(s[:300])
