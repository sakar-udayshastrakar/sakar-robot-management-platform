import sys,struct,re
p=sys.argv[1]; d=open(p,'rb').read()
if d[4]==1:
    shoff,=struct.unpack('<I',d[32:36]); shes,shn,shstr=struct.unpack('<HHH',d[46:52]); fmt='<IIIIII'; n=24
else:
    shoff,=struct.unpack('<Q',d[40:48]); shes,shn,shstr=struct.unpack('<HHH',d[58:64]); fmt='<IIQQQQ'; n=40
secs=[]
for i in range(shn):
    o=shoff+i*shes; nm,ty,fl,ad,of,sz=struct.unpack(fmt,d[o:o+n]); secs.append((nm,of,sz))
sh=d[secs[shstr][1]:secs[shstr][1]+secs[shstr][2]]
S={sh[nm:sh.find(b'\0',nm)].decode():(of,sz) for nm,of,sz in secs}
of,sz=S['.debug_str']; ds=d[of:of+sz]
for t in ds.split(b'\0'):
    t=t.decode('latin1')
    if len(t)<2: continue
    if re.search(r'^(_?_?(gnu_cxx|std|__|_M_|_S_|_Tp|_Alloc|_Ch|_Lock|_Sp|_Impl|_Bind|_Mutex|type_info|thread|shared|allocator|char_traits|basic_string|ios|ctype|locale|ostream|istream|exception))|^_[A-Z]|size_t|__off|long|unsigned|short|signed|char$|int$|float|double|bool',t): continue
    print(t)
