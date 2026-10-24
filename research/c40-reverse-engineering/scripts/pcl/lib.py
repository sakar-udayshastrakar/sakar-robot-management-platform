import json,sys,re,collections
sys.stdout.reconfigure(encoding="utf-8",errors="replace")
B="C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad"
D=B+'/dex/peanut-clean/'
DEXN=['classes']+['classes%d'%k for k in range(2,8)]
def load():
    cls=[]
    for f in DEXN:
        for c in json.load(open(D+f+'.dex.classes.json',encoding='utf-8')):
            c['dex']=f+'.dex'; cls.append(c)
    return cls
def strings():
    out={}
    for f in DEXN:
        out[f+'.dex']=open(D+f+'.dex.strings.txt',encoding='utf-8',errors='replace').read().split('\n')
    return out
