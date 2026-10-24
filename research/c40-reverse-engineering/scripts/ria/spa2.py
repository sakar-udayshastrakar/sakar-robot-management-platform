import re,sys
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
B=sys.argv[1]
o=open(B+'/ria/spa_en_obj.txt',encoding='utf-8').read()
BS=chr(92)
kvre=re.compile(r'([A-Za-z0-9_]+):"((?:[^"'+BS+BS+r']|'+BS+BS+r'.)*)"')
secs=sys.argv[2].split(',')
n=int(sys.argv[3])
for sec in secs:
    m=re.search(r'(?<![A-Za-z])'+sec+r':\{(.*?)\},[a-zA-Z]+:\{',o,re.S)
    if not m: print(sec,'?');continue
    kv=kvre.findall(m.group(1))
    print('##',sec,len(kv))
    for k,v in kv[:n]: print('   ',k,'=',v[:110])
