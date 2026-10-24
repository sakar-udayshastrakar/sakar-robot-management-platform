import zipfile,glob,os,struct,subprocess,tempfile,hashlib,json,re,sys
T=r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246'
res={}
for f in sorted(glob.glob(os.path.join(T,'*.apk'))):
    z=zipfile.ZipFile(f); name=os.path.basename(f)
    info={'v1_files':[n for n in z.namelist() if re.match(r'META-INF/[^/]+\.(RSA|DSA|EC|SF|MF)$',n)]}
    d=open(f,'rb').read()
    # find EOCD
    e=d.rfind(b'PK\x05\x06'); cd_off=struct.unpack_from('<I',d,e+16)[0]
    ids=[]
    if d[cd_off-16:cd_off]==b'APK Sig Block 42':
        sz=struct.unpack_from('<Q',d,cd_off-24)[0]; start=cd_off-sz-8
        p=start+8
        while p<cd_off-24:
            l=struct.unpack_from('<Q',d,p)[0]; i=struct.unpack_from('<I',d,p+8)[0]; ids.append(hex(i)); p+=8+l
    info['signing_block_ids']=ids  # 0x7109871a=v2, 0xf05368c0=v3, 0x1b93ad61=v3.1
    certs=[]
    for n in info['v1_files']:
        if n.endswith(('.RSA','.DSA','.EC')):
            tf=tempfile.NamedTemporaryFile(delete=False,suffix='.der'); tf.write(z.read(n)); tf.close()
            try:
                out=subprocess.run(['openssl','pkcs7','-inform','DER','-in',tf.name,'-print_certs'],capture_output=True,text=True).stdout
                for pem in re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',out,re.S):
                    pf=tempfile.NamedTemporaryFile(delete=False,suffix='.pem',mode='w'); pf.write(pem); pf.close()
                    o=subprocess.run(['openssl','x509','-in',pf.name,'-noout','-subject','-issuer','-dates','-fingerprint','-sha256','-serial'],capture_output=True,text=True).stdout
                    certs.append({'from':n,'x509':o.strip().splitlines()})
                    os.unlink(pf.name)
            finally: os.unlink(tf.name)
    info['v1_certs']=certs
    res[name]=info
out=os.path.join(T,'REVERSE_ENGINEERING_RESULTS','work','apk_signatures.json')
json.dump(res,open(out,'w',encoding='utf-8'),indent=1)
for k,v in res.items():
    print(k); print(' v1:',[x for x in v['v1_files'] if x.endswith(('RSA','DSA','EC'))],' block ids:',v['signing_block_ids'])
    for c in v['v1_certs']: print('  ',c['x509'])
