import struct,sys,glob,os,subprocess,tempfile,hashlib
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
def rdutf(d,p):
    n=struct.unpack_from('>H',d,p)[0]; return d[p+2:p+2+n].decode('utf-8','replace'),p+2+n
def parse(path):
    d=open(path,'rb').read()
    p=0; ver=struct.unpack_from('>I',d,p)[0]; p+=4
    sl=struct.unpack_from('>I',d,p)[0]; p+=4+sl
    it=struct.unpack_from('>I',d,p)[0]; p+=4
    ents=[]
    while True:
        t=d[p]; p+=1
        if t==0: break
        alias,p=rdutf(d,p); p+=8
        nchain=struct.unpack_from('>I',d,p)[0]; p+=4
        chain=[]
        for _ in range(nchain):
            ty,p=rdutf(d,p); ln=struct.unpack_from('>I',d,p)[0]; p+=4; chain.append(d[p:p+ln]); p+=ln
        info={'type':{1:'CERT',2:'KEY',3:'SECRET',4:'SEALED'}.get(t,t),'alias':alias}
        if t==1:
            ty,p=rdutf(d,p); ln=struct.unpack_from('>I',d,p)[0]; p+=4; chain=[d[p:p+ln]]; p+=ln
        elif t==2:
            ty,p=rdutf(d,p); fm,p=rdutf(d,p); alg,p=rdutf(d,p); ln=struct.unpack_from('>I',d,p)[0]; p+=4+ln
        elif t==3 or t==4:
            ln=struct.unpack_from('>I',d,p)[0]; p+=4+ln
        info['certs']=chain
        ents.append(info)
    return ver,it,ents,len(d)
def certinfo(der):
    r=subprocess.run(['openssl','x509','-inform','DER','-noout','-subject','-issuer','-dates','-fingerprint','-sha256'],input=der,capture_output=True)
    return r.stdout.decode('utf-8','replace').replace('\n',' | ')
for f in sorted(glob.glob(sys.argv[1]+'/apkx/peanut-clean/res/raw/*.bks')):
    try:
        ver,it,ents,sz=parse(f)
        print('==',os.path.basename(f),sz,'bytes BKS v%d iter=%d entries=%d'%(ver,it,len(ents)),'sha256=',hashlib.sha256(open(f,'rb').read()).hexdigest()[:16])
        for e in ents:
            print('  ',e['type'],e['alias'],'nCerts=',len(e['certs']))
            for c in e['certs'][:3]: print('      ',certinfo(c)[:420])
    except Exception as ex: print('ERR',f,ex)
