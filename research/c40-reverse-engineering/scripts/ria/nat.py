import sys,os,json,hashlib,re
sys.path.insert(0,os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import elfinfo
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
d=B+'/apkx/robot-installation/lib/armeabi-v7a/'
res={}
for f in sorted(os.listdir(d)):
    p=d+f;data=open(p,'rb').read()
    e=elfinfo.elf(p)
    jni=[n for n in e['dynsym_defined'] if n.startswith('Java_') or n=='JNI_OnLoad']
    rec={'size':len(data),'sha256':hashlib.sha256(data).hexdigest(),'machine':e['machine'],'class':e['class'],'stripped':e['stripped'],'needed':e['needed'],'jni':jni,'ndef':len(e['dynsym_defined']),'nund':len(e['dynsym_undefined']),'sections':e['sections'][:40]}
    # interesting undefined imports
    und=e['dynsym_undefined']
    rec['imports_interesting']=[u for u in und if re.search(r'^(socket|connect|bind|listen|accept|send|recv|exec|fork|system|popen|dlopen|dlsym|ptrace|open|read|write|ioctl|tcgetattr|tcsetattr|cfsetospeed|cfsetispeed|chmod|setuid|SSL_|EVP_|AES|MD5|SHA|RAND|__android_log|mmap|mprotect|kill|syscall|getenv)',u)]
    res[f]=rec
json.dump(res,open(B+'/ria/native_info.json','w'),indent=1)
for f,r in res.items():
    print(f,r['size'],r['machine'],r['class'],'stripped' if r['stripped'] else 'symbols','needed',r['needed'],'JNI',len(r['jni']),r['jni'][:12],'def',r['ndef'])
