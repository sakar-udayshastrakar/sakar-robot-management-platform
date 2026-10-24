# Scan a Thumb-2 range for MOVW/MOVT immediates (hand-decoded encodings T3), report combined 32-bit constants. Not a disassembler.
import sys,struct
p=sys.argv[1]; va=int(sys.argv[2],0); size=int(sys.argv[3],0); base=int(sys.argv[4],0) if len(sys.argv)>4 else 0x8000
d=open(p,'rb').read(); off=(va&~1)-base
regs={}
i=0
while i<size-3:
    h1,h2=struct.unpack_from('<HH',d,off+i)
    if (h1&0xfbf0)==0xf240 or (h1&0xfbf0)==0xf2c0:
        imm4=h1&0xf; i_=(h1>>10)&1; imm3=(h2>>12)&7; imm8=h2&0xff; rd=(h2>>8)&0xf
        imm=(imm4<<12)|(i_<<11)|(imm3<<8)|imm8
        a=(va&~1)+i
        if (h1&0xfbf0)==0xf240: regs[rd]=imm; print('%#x movw r%d,#%#x'%(a,rd,imm))
        else:
            lo=regs.get(rd); print('%#x movt r%d,#%#x  => %s'%(a,rd,imm,hex((imm<<16)|lo) if lo is not None else '?'))
        i+=4
    else: i+=2
