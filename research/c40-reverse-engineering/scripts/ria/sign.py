import zipfile,hashlib,re,sys,struct
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
apk=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\robot-installation-assistant-v4.11.0-0-gb7c7c7b-prod-release-p.apk"
raw=open(apk,'rb').read()
print('size',len(raw),'sha256',hashlib.sha256(raw).hexdigest(),'md5',hashlib.md5(raw).hexdigest())
print('v2/v3 signing block magic present:', b'APK Sig Block 42' in raw)
z=zipfile.ZipFile(apk)
rsa=z.read('META-INF/CERT.RSA')
def tlv(d,p):
    t=d[p];p+=1
    l=d[p];p+=1
    if l&0x80:
        n=l&0x7f;l=int.from_bytes(d[p:p+n],'big');p+=n
    return t,p,l
# find certificate: search for sequence beginning; crude approach - locate X.509 by scanning for 0x30 0x82 and parse
OIDS={b'\x55\x04\x03':'CN',b'\x55\x04\x06':'C',b'\x55\x04\x07':'L',b'\x55\x04\x08':'ST',b'\x55\x04\x0a':'O',b'\x55\x04\x0b':'OU'}
def names(d):
    out=[]
    for m in re.finditer(rb'\x06\x03(\x55\x04[\x03\x06\x07\x08\x0a\x0b])[\x0c\x13\x16\x14](.)',d):
        k=OIDS[m.group(1)];ln=m.group(2)[0];s=d[m.end():m.end()+ln].decode('utf-8','replace');out.append((k,s))
    return out
i=rsa.find(b'\x30\x82',20)
print(names(rsa)[:12])
dates=re.findall(rb'\x17\x0d(\d{12})Z',rsa)
print('validity',[x.decode() for x in dates[:2]])
algs={b'\x2a\x86\x48\x86\xf7\x0d\x01\x01\x0b':'sha256WithRSA',b'\x2a\x86\x48\x86\xf7\x0d\x01\x01\x05':'sha1WithRSA'}
for k,v in algs.items():
    if k in rsa: print('alg',v)
# SHA-256 of cert DER: find cert as the first SEQUENCE inside certificates [0]
m=re.search(rb'\xa0\x82(..)\x30\x82(..)',rsa)
mf=z.read('META-INF/MANIFEST.MF').decode('utf-8','replace')
print('manifest header',mf[:120].replace('\r\n',' | '))
print('entries digests:',set(re.findall(r'(SHA-?\d*)-Digest',mf)))
print('Created-By' , re.findall(r'Created-By: (.*)',mf)[:1])
sf=z.read('META-INF/CERT.SF').decode('utf-8','replace')
print('SF head',sf[:200].replace('\r\n',' | '))
