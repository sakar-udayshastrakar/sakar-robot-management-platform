import zipfile,glob,os,hashlib,csv,json,sys
T=r'D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246'
out=os.path.join(T,'REVERSE_ENGINEERING_RESULTS','ARTIFACT_INVENTORY_EMBEDDED.csv')
def ftype(n):
    e=os.path.splitext(n)[1].lower()
    return {'.so':'native-lib','.dex':'dex','.xml':'xml','.json':'json','.png':'image','.jpg':'image','.db':'database','.sqlite':'database','.pem':'certificate','.crt':'certificate','.bks':'keystore','.jks':'keystore','.p12':'keystore','.jar':'jar','.properties':'properties','.arsc':'resource-table','.html':'web','.js':'web','.wav':'audio','.mp3':'audio','.ogg':'audio','.bin':'binary','.yaml':'yaml','.yml':'yaml','.sh':'script','.txt':'text','.pak':'chromium-pak','.dat':'data','.so.1':'native-lib'}.get(e,'other(%s)'%e)
rows=[]
for f in sorted(glob.glob(os.path.join(T,'*.apk'))):
    z=zipfile.ZipFile(f); parent=os.path.basename(f)
    for i in z.infolist():
        if i.is_dir(): continue
        d=z.read(i.filename)
        rows.append([parent+'!/'+i.filename,parent,i.filename,ftype(i.filename),len(d),hashlib.sha256(d).hexdigest()])
dw=r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad\debwork'
meta=json.load(open(r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad\debs.json',encoding='utf-8'))
for k,v in meta.items():
    dn=os.path.basename(k)
    for fl in v.get('files',[]):
        if fl['type']!='file': continue
        p=os.path.join(dw,dn[:-4],fl['path'].lstrip('./'))
        h=''
        if os.path.isfile(p):
            h=hashlib.sha256(open(p,'rb').read()).hexdigest()
        rows.append([dn+'!/'+fl['path'].lstrip('./'),dn,fl['path'],ftype(fl['path']),fl['size'],h or 'NOT_EXTRACTED'])
with open(out,'w',newline='',encoding='utf-8') as fh:
    w=csv.writer(fh); w.writerow(['embedded_id','container','inner_path','inner_type','size_bytes','sha256']); w.writerows(rows)
print(len(rows))
