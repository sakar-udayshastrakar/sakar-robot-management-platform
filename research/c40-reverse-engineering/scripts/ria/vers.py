import sys,zipfile,os,re
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import dexx
apk=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\robot-installation-assistant-v4.11.0-0-gb7c7c7b-prod-release-p.apk"
z=zipfile.ZipFile(apk)
pre=['Lcom/fasterxml/jackson/core/json/PackageVersion;','Lcom/fasterxml/jackson/databind/cfg/PackageVersion;','Lokhttp3/internal/Version;','Lio/sentry/SentryClient','Lio/sentry/android/core/BuildConfig','Lio/sentry/BuildConfig','Lcom/alibaba/fastjson/JSON;','Lorg/eclipse/paho/client/mqttv3/MqttConnectOptions;','Lorg/eclipse/paho/client/mqttv3/internal/ClientComms;','Lcom/yanzhenjie/andserver/BuildConfig','Lorg/bouncycastle/jce/provider/BouncyCastleProvider;','Lretrofit2/BuildConfig','Lcom/tencent/mmkv/MMKV;','Lorg/greenrobot/greendao/DaoLog;','Lorg/greenrobot/eventbus/EventBus;','Lorg/java_websocket/WebSocketImpl;','Lorg/java_websocket/AbstractWebSocket;','Lorg/glassfish/','Lcom/liulishuo/okdownload/BuildConfig','Lcom/bumptech/glide/BuildConfig','Lorg/greenrobot/greendao/BuildConfig','Lcom/blankj/utilcode/','Lorg/slf4j/','Lch/qos/logback/classic/Logger;','Lcom/google/gson/Gson;','Lio/reactivex/rxjava3/core/Observable;','Lkotlin/KotlinVersion;','Lfreemarker/template/Configuration;','Lcom/tencent/bugly/']
for n in sorted(z.namelist()):
    if n.endswith('.dex') and '/' not in n:
        for c in dexx.parse(z.read(n),pre):
            sv=c.get('sv',[])
            vals=[]
            for i,f in enumerate(c['fields'][:c.get('ns',0)]):
                if i<len(sv) and isinstance(sv[i],str) and re.search(r'\d+\.\d+',sv[i]): vals.append((f[0].split('->')[1].split(':')[0],sv[i][:60]))
            ss=[]
            for m in c['methods']:
                for s in m.get('str',[]):
                    if re.search(r'^\D{0,12}\d+\.\d+(\.\d+)?([-.][A-Za-z0-9]+)?$',s): ss.append(s)
            if vals or ss: print(c['c'],vals[:5],ss[:5])
