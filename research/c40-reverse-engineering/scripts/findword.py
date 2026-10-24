import sys,struct
d=open(sys.argv[1],'rb').read()
for v in sys.argv[2:]:
    v=int(v,0); pat=struct.pack('<I',v)
    i=-1; out=[]
    while True:
        i=d.find(pat,i+1)
        if i<0: break
        out.append(hex(i))
    print(hex(v),out[:40])
