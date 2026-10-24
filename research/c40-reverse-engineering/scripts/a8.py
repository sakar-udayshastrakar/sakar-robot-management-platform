import os,sys,re,json
SP=sys.argv[1]
W=SP+'/debwork'
skip_name=re.compile(r'^(libpcl|libboost|libopencv|libcpprest|libglog|libgflags|libunwind|libprotobuf|libceres|libOpenNI|libcholmod|libcairo|libeigen)')
skip_ext=('.h','.hpp','.pyc','.lisp','.cmake','.pc','.hh','.asd','.png','.svg','.ico','.stl','.bin','.a','.la','.gz','.css')
pats={
 'url':re.compile(r'(?:https?|wss?|mqtts?|coaps?|ftp|tcp|udp|rtsp)://[A-Za-z0-9._~:/?#\[\]@!$&()*+,;=%-]{4,}'),
 'ip':re.compile(r'(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])'),
 'cred':re.compile(r'(?i)(passw(?:or)?d|passwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|credential|sudo -S|authorization|bearer|BEGIN (?:RSA |EC |OPENSSH )?(?:PRIVATE|PUBLIC) KEY|ssh-rsa|chmod\s+(?:-R\s+)?7[0-9][0-9]|username)'),
 'port':re.compile(r'(?i)(?:port|listen|bind)[^\n]{0,20}?\b(\d{3,5})\b'),
}
out=open(SP+'/an/strings_hits.txt','w',encoding='utf-8')
def strings(data,minlen=5):
    return [m.group().decode('ascii') for m in re.finditer(rb'[\x20-\x7e]{%d,}'%minlen,data)]
cnt=0
for deb in sorted(os.listdir(W)):
    pkg=deb.split('_')[0].replace('ros-indigo-','')
    for root,dirs,files in os.walk(W+'/'+deb):
        if '/include' in root.replace(chr(92),'/') or 'share/doc' in root.replace(chr(92),'/') or 'robotviz/assets' in root.replace(chr(92),'/'): continue
        for fn in sorted(files):
            p=os.path.join(root,fn)
            if os.path.islink(p): continue
            if fn.endswith(skip_ext) or skip_name.match(fn): continue
            try:
                sz=os.path.getsize(p)
                if sz>25*1024*1024 or sz==0: continue
                d=open(p,'rb').read()
            except Exception: continue
            rel=os.path.relpath(p,W+'/'+deb).replace(chr(92),'/')
            isbin=d[:4]==b'\x7fELF'
            lines=strings(d) if isbin else d.decode('utf-8','replace').splitlines()
            for ln in lines:
                for k in ('url','ip','cred'):
                    m=pats[k].search(ln)
                    if m:
                        out.write('%s\t%s\t%s\t%s\t%s\n'%(k,pkg,rel,'BIN' if isbin else 'TXT',ln.strip()[:220]))
                        cnt+=1
print(cnt)
