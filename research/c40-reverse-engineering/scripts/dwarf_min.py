import sys,struct
p=sys.argv[1]; want=sys.argv[2:] 
d=open(p,'rb').read()
if d[4]==1:
    shoff,=struct.unpack('<I',d[32:36]); shes,shn,shstr=struct.unpack('<HHH',d[46:52]); fmt='<IIIIII'; n=24
else:
    shoff,=struct.unpack('<Q',d[40:48]); shes,shn,shstr=struct.unpack('<HHH',d[58:64]); fmt='<IIQQQQ'; n=40
secs=[]
for i in range(shn):
    o=shoff+i*shes; nm,ty,fl,ad,of,sz=struct.unpack(fmt,d[o:o+n]); secs.append((nm,of,sz))
sh=d[secs[shstr][1]:secs[shstr][1]+secs[shstr][2]]
S={sh[nm:sh.find(b'\0',nm)].decode():d[of:of+sz] for nm,of,sz in secs}
info=S['.debug_info']; abbrev=S['.debug_abbrev']; dstr=S['.debug_str']; dline=S.get('.debug_line',b'')
def uleb(b,o):
    r=0;s=0
    while True:
        x=b[o];o+=1;r|=(x&0x7f)<<s;s+=7
        if not x&0x80:return r,o
def sleb(b,o):
    r=0;s=0
    while True:
        x=b[o];o+=1;r|=(x&0x7f)<<s;s+=7
        if not x&0x80:
            if x&0x40:r-=1<<s
            return r,o
def parse_abbrev(off):
    t={};o=off
    while True:
        code,o=uleb(abbrev,o)
        if code==0:break
        tag,o=uleb(abbrev,o);ch=abbrev[o];o+=1;attrs=[]
        while True:
            a,o=uleb(abbrev,o);f,o=uleb(abbrev,o)
            ic=None
            if f==0x21: ic,o=sleb(abbrev,o)
            if a==0 and f==0:break
            attrs.append((a,f,ic))
        t[code]=(tag,ch,attrs)
    return t
TAG={0x11:'compile_unit',0x13:'structure_type',0x0d:'member',0x16:'typedef',0x34:'variable',0x2e:'subprogram',0x01:'array_type',0x24:'base_type',0x0f:'pointer_type',0x04:'enumeration_type',0x28:'enumerator',0x05:'formal_parameter',0x21:'subrange_type',0x26:'const_type',0x35:'volatile_type',0x17:'union_type',0x02:'class_type'}
AT={0x03:'name',0x0b:'byte_size',0x49:'type',0x3a:'decl_file',0x3b:'decl_line',0x1c:'const_value',0x38:'data_member_location',0x25:'producer',0x1b:'comp_dir',0x37:'count',0x2f:'upper_bound',0x3f:'external',0x02:'location',0x11:'low_pc',0x12:'high_pc'}
o=0; nodes={}; out=[]
while o<len(info):
    ul,=struct.unpack_from('<I',info,o); cu_start=o; o+=4; end=o+ul
    ver,=struct.unpack_from('<H',info,o);o+=2
    aoff,=struct.unpack_from('<I',info,o);o+=4; asz=info[o];o+=1
    ab=parse_abbrev(aoff); depth=0; stack=[]
    while o<end:
        off=o-cu_start
        code,o=uleb(info,o)
        if code==0:
            if stack: stack.pop()
            continue
        tag,ch,attrs=ab[code]; rec={'tag':TAG.get(tag,hex(tag)),'cu':cu_start,'off':off,'attrs':{}}
        for a,f,ic in attrs:
            if f==0x08: e=info.index(b'\0',o); v=info[o:e].decode('latin1'); o=e+1
            elif f==0x0e: so,=struct.unpack_from('<I',info,o);o+=4; e=dstr.index(b'\0',so); v=dstr[so:e].decode('latin1')
            elif f==0x0b: v=info[o];o+=1
            elif f==0x05: v,=struct.unpack_from('<H',info,o);o+=2
            elif f==0x06: v,=struct.unpack_from('<I',info,o);o+=4
            elif f==0x07: v,=struct.unpack_from('<Q',info,o);o+=8
            elif f==0x0d: v,o=sleb(info,o)
            elif f==0x0f: v,o=uleb(info,o)
            elif f==0x13: v,=struct.unpack_from('<I',info,o);o+=4; v=cu_start+v
            elif f==0x11: v=cu_start+info[o];o+=1
            elif f==0x12: v,=struct.unpack_from('<H',info,o);o+=2; v=cu_start+v
            elif f==0x14: v,=struct.unpack_from('<Q',info,o);o+=8; v=cu_start+v
            elif f==0x15: v,o=uleb(info,o); v=cu_start+v
            elif f==0x10: v,=struct.unpack_from('<I',info,o);o+=4
            elif f==0x17: v,=struct.unpack_from('<I',info,o);o+=4
            elif f==0x0c: v=info[o];o+=1
            elif f==0x19: v=True
            elif f==0x21: v=ic
            elif f==0x18: l,o=uleb(info,o); v=info[o:o+l];o+=l
            elif f==0x0a: l=info[o];o+=1;v=info[o:o+l];o+=l
            elif f==0x01: v,=struct.unpack_from('<I',info,o);o+=4
            elif f==0x09: l,o=uleb(info,o); v=info[o:o+l];o+=l
            elif f==0x03: l,=struct.unpack_from('<H',info,o);o+=2; v=info[o:o+l];o+=l
            elif f==0x04: l,=struct.unpack_from('<I',info,o);o+=4; v=info[o:o+l];o+=l
            elif f==0x1f: v,=struct.unpack_from('<I',info,o);o+=4
            else: raise Exception('form %#x'%f)
            rec['attrs'][AT.get(a,hex(a))]=v
        rec['depth']=len(stack); rec['parent']=stack[-1] if stack else None
        gid=cu_start+off; nodes[gid]=rec; out.append(gid)
        if ch: stack.append(gid)
    o=end
def tname(gid,depth=0):
    if gid is None: return 'void'
    n=nodes.get(gid)
    if not n: return '?'
    a=n['attrs']
    if n['tag']=='pointer_type': return tname(a.get('type'),depth+1)+'*'
    if n['tag'] in ('const_type','volatile_type'): return n['tag'][:5]+' '+tname(a.get('type'),depth+1)
    if n['tag']=='array_type':
        cnt=[nodes[c]['attrs'] for c in out if nodes[c]['parent']==gid and nodes[c]['tag']=='subrange_type']
        return tname(a.get('type'),depth+1)+''.join('[%s]'%(c.get('upper_bound',-1)+1 if 'upper_bound' in c else c.get('count','?')) for c in cnt)
    return a.get('name',n['tag'])
for gid in out:
    n=nodes[gid]; a=n['attrs']
    if n['tag']=='compile_unit': print('CU',a.get('name'),'|',a.get('comp_dir'),'|',a.get('producer'))
    if (n['tag']=='structure_type' and a.get('name') in want) or (n['tag']=='typedef' and a.get('name') in want):
        tgt=gid
        if n['tag']=='typedef': tgt=a.get('type')
        print('TYPE',n['tag'],a.get('name'),'size',nodes[tgt]['attrs'].get('byte_size'))
        gid2=tgt
        for c in out:
            if nodes[c]['parent']==gid2 and nodes[c]['tag']=='member':
                m=nodes[c]['attrs']; print('   +%s %s %s'%(m.get('data_member_location'),tname(m.get('type')),m.get('name')))
        continue
        for c in out:
            if nodes[c]['parent']==gid and nodes[c]['tag']=='member':
                m=nodes[c]['attrs']; print('   +%s %s %s'%(m.get('data_member_location'),tname(m.get('type')),m.get('name')))
    if n['tag']=='subprogram' and 'name' in a and n['depth']==1 and (a.get('decl_file') in (1,2) or a.get('name') in want):
        params=[ (nodes[c]['attrs'].get('name'),tname(nodes[c]['attrs'].get('type'))) for c in out if nodes[c]['parent']==gid and nodes[c]['tag']=='formal_parameter']
        print('FUNC',a.get('name'),'(',', '.join('%s %s'%(t,nn) for nn,t in params),') ->',tname(a.get('type')) if 'type' in a else 'void', 'file#',a.get('decl_file'),'line',a.get('decl_line'))
    if n['tag']=='variable' and n['depth']==1 and 'name' in a and a.get('decl_line') and not a['name'].startswith('_'):
        print('VAR',tname(a.get('type')),a['name'],'line',a.get('decl_line'),'file#',a.get('decl_file'), 'const' if 'const_value' in a else '')
