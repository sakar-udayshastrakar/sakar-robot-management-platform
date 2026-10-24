import re
PRIM={'V':'void','Z':'boolean','B':'byte','S':'short','C':'char','I':'int','J':'long','F':'float','D':'double'}
def tname(t,short=True):
    dims=0
    while t.startswith('['): dims+=1;t=t[1:]
    if t in PRIM: n=PRIM[t]
    else:
        n=t[1:-1].replace('/','.')
        if short: n=n.split('.')[-1]
    return n+'[]'*dims
def parse_sig(s):
    # (params)ret
    m=re.match(r'\((.*)\)(.*)',s)
    ps=m.group(1);ret=m.group(2)
    out=[];i=0
    while i<len(ps):
        j=i
        while ps[j]=='[': j+=1
        if ps[j]=='L': j=ps.index(';',j)
        out.append(ps[i:j+1]);i=j+1
    return out,ret
def msig(m,short=True):
    name,rest=m.split('->')[1].split('(',1)
    ps,ret=parse_sig('('+rest)
    return name,[tname(p,short) for p in ps],tname(ret,short)
