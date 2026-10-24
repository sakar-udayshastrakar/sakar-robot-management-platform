import sys,json,re,glob,os,struct
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
sys.path.insert(0,sys.argv[1]+'/scripts'); 
import elfinfo
res=[]
for f in sorted(glob.glob(sys.argv[1]+'/apkx/peanut-clean/lib/*/*.so')):
    r=elfinfo.elf(f)
    d=open(f,'rb').read()
    st=elfinfo.strings(d,6)
    r['file']=os.path.basename(f); r['size']=len(d)
    # export symbols: parse dynsym defined names via dynstr heuristics
    r['jni']=sorted(set(s for s in st if s.startswith('Java_')))
    r['onload']=any(s=='JNI_OnLoad' for s in st)
    r['strs_interesting']=[s for s in st if re.search(r'(http|ssl|key|pass|secret|crypt|aes|sign|/dev/|/sys/|/data/|/sdcard|su$|\.so$|keenon|peanut|sentry|dsn|libc|version|mmkv)',s,re.I)][:60]
    r.pop('sections',None)
    res.append(r)
    print('=====',r['file'],r['size'],r.get('machine'),'stripped=',r.get('stripped'),'needed=',r.get('needed'))
    print(' JNI:',r['jni'][:40]); print(' onload:',r['onload'])
    print(' undefined(sample):',[x for x in r.get('dynsym_undefined',[]) if not x.startswith('__')][:50])
    print(' strs:',r['strs_interesting'][:30])
json.dump(res,open(sys.argv[1]+'/pcl_out/elf_all.json','w'),indent=1)
