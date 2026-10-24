import sys,re
sys_ = __import__("sys"); sys_.stdout.reconfigure(encoding="utf-8", errors="replace")
B=sys.argv[1]; fn=sys.argv[2]; pat=sys.argv[3]; w=int(sys.argv[4]); mx=int(sys.argv[5]) if len(sys.argv)>5 else 3
t=open(B+'/apkx/robot-installation/assets/ros/dist/assets/'+fn,encoding='utf-8',errors='replace').read()
n=0
for m in re.finditer(pat,t):
    a=max(0,m.start()-w); print('>>>',m.start(),t[a:m.end()+w].replace('\n',' ')); print(); n+=1
    if n>=mx: break
