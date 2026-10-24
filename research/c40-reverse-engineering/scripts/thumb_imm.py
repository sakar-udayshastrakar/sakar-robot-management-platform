# Minimal Thumb-16 pattern scanner: lists movs/cmp/adds/subs immediates, and PC-relative literal loads (ldr Rd,[pc,#imm]).
# NOT a disassembler. 32-bit Thumb-2 instruction pairs are skipped by prefix detection (0xE800+ halfwords).
import sys,struct
p=sys.argv[1]; va=int(sys.argv[2],0); size=int(sys.argv[3],0); base=int(sys.argv[4],0) if len(sys.argv)>4 else 0x8000
d=open(p,'rb').read()
off=(va&~1)-base
i=0
while i<size:
    h,=struct.unpack_from('<H',d,off+i); a=(va&~1)+i
    if (h>>11) in (0x1d,0x1e,0x1f):
        h2,=struct.unpack_from('<H',d,off+i+2)
        print('%#x: T32 %04x %04x'%(a,h,h2)); i+=4; continue
    t=h>>11
    if t==4: print('%#x: movs r%d,#%#x'%(a,(h>>8)&7,h&0xff))
    elif t==5: print('%#x: cmp  r%d,#%#x'%(a,(h>>8)&7,h&0xff))
    elif t==6: print('%#x: adds r%d,#%#x'%(a,(h>>8)&7,h&0xff))
    elif t==7: print('%#x: subs r%d,#%#x'%(a,(h>>8)&7,h&0xff))
    elif t==9:
        imm=(h&0xff)*4; tgt=((a+4)&~3)+imm; v,=struct.unpack_from('<I',d,tgt-base)
        print('%#x: ldr  r%d,[pc,#%d] -> [%#x]=%#x'%(a,(h>>8)&7,imm,tgt,v))
    elif (h>>12)==0xd and ((h>>8)&0xf)<14: print('%#x: b<%d> %04x'%(a,(h>>8)&0xf,h))
    elif (h>>11)==0x1c: print('%#x: b %04x'%(a,h))
    else: print('%#x: %04x'%(a,h))
    i+=2
