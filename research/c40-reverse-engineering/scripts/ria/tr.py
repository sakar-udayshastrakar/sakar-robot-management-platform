import sys,zipfile,re,os
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import dexx
dexx.TRACE=True
apk=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\robot-installation-assistant-v4.11.0-0-gb7c7c7b-prod-release-p.apk"
prefix=sys.argv[1]; meth=sys.argv[2] if len(sys.argv)>2 else ".*"
z=zipfile.ZipFile(apk)
for n in sorted(z.namelist()):
    if n.endswith(".dex") and "/" not in n:
        for c in dexx.parse(z.read(n),[prefix]):
            print("##",c['c'],n)
            for m in c['methods']:
                if not re.search(meth,m['m']): continue
                print("  M",m['m'])
                for t,v in m.get('tr',[]):
                    if t=='C' and v.startswith(('Ljava/lang/StringBuilder;->toString','Ljava/lang/StringBuilder;-><init>()')): continue
                    if t=='C' and 'StringBuilder;->append' in v: print("      +"); continue
                    print("     ",t,v[:200])
