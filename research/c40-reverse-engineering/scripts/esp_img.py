import struct,sys,hashlib,json
p=sys.argv[1]; d=open(p,'rb').read()
magic,nseg,mode,szf,entry=struct.unpack_from('<BBBBI',d,0)
print('magic %#x nseg %d spi_mode %d size_freq %#x entry %#x'%(magic,nseg,mode,szf,entry))
wp,drv0,drv1,drv2,chip,minrev,minfull,maxfull,res,hashapp=struct.unpack_from('<BBBBHBHH4sB',d,8)
print('wp %#x chip_id %d minrev %d minfull %d maxfull %d hash_appended %d'%(wp,chip,minrev,minfull,maxfull,hashapp))
off=24; cs=0xEF; segs=[]
for i in range(nseg):
    la,ln=struct.unpack_from('<II',d,off); data=d[off+8:off+8+ln]
    for b in data: cs^=b
    segs.append((i,hex(la),ln,off+8))
    print('seg',i,'load %#x len %d (%#x) file_off %#x'%(la,ln,ln,off+8))
    off+=8+ln
print('end of segments',hex(off))
pad=(15-(off%16))%16
chk_pos=off+pad
print('checksum byte pos',hex(chk_pos),'stored',hex(d[chk_pos]),'computed',hex(cs))
end=chk_pos+1
print('digest start',hex(end),'remaining bytes',len(d)-end)
dig=d[end:end+32]; calc=hashlib.sha256(d[:end]).digest()
print('stored digest',dig.hex()); print('calc   digest',calc.hex(), 'MATCH' if dig==calc else 'MISMATCH')
print('trailing after digest:',len(d)-end-32)
# app desc
ad=d[0x20:0x20+256]
print(struct.unpack_from('<II8s32s32s16s16s32s32s',ad,0))
