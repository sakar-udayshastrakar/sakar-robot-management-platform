import re,sys,collections
B=sys.argv[1]
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
pats={'exec':r'Ljava/lang/Runtime;->exec|Ljava/lang/ProcessBuilder;-><init>|ProcessBuilder;->start','dexload':r'DexClassLoader|PathClassLoader;-><init>|System;->load(Library)?\(','objstream':r'ObjectInputStream;->readObject','sqlraw':r'SQLiteDatabase;->(rawQuery|execSQL)','webview':r'addJavascriptInterface|setJavaScriptEnabled|setAllowFileAccess|setAllowUniversalAccess|evaluateJavascript','trust':r'X509TrustManager;|HostnameVerifier;|ALLOW_ALL|setSSLSocketFactory|setHostnameVerifier','worldrw':r'MODE_WORLD','cipher':r'Ljavax/crypto/Cipher;->getInstance|MessageDigest;->getInstance|SecretKeySpec;-><init>','random':r'Ljava/util/Random;-><init>','install':r'application/vnd.android.package-archive|PackageInstaller'}
cur=None;curm=None
hits=collections.defaultdict(lambda: collections.defaultdict(set))
for l in open(B+'/ria/tr_all_keenon.txt',encoding='utf-8'):
    l=l.rstrip('\n')
    if l.startswith('## '): cur=l[3:].split(' ')[0];continue
    if l.startswith('  M '): curm=l.split('->')[1].split('(')[0];continue
    for k,p in pats.items():
        if re.search(p,l): hits[k][cur].add(curm)
for k,v in hits.items():
    print('==',k,len(v))
    for c,ms in sorted(v.items())[:45]: print('  ',c[1:-1].replace('com/keenon/',''),sorted(ms)[:6])
