#!/bin/bash
# usage: reacode.sh <ServiceSystem|RemoteControl|krlog> <class> <method> ; prints JADX source of method (via REA), one call at a time
S="C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad"
A="D:/Sakar Robotics Projects/C40 S-LS-M014C00-RW-F00-V246"
case "$1" in
 ServiceSystem) F="$A/ServiceSystem-v2.0.11-0-g94566e5-prod-release-p-rk3288-signed.apk";;
 RemoteControl) F="$A/RemoteControl-v3.3.1-0-gfe68b6d-prod-release-p-rk3288-signed.apk";;
 krlog) F="$A/krlog-v1.2.6-0-g18a40d0-prod-release-p-rk3288-signed.apk";;
esac
OUT="$S/rea_out/$1__$2__$3${4}.json"
timeout 590 bash "$S/scripts/rea.sh" inspect-android-method "$F" "$2" "$3" ${4:+--overload-index $4} --format json > "$OUT" 2>&1
python -X utf8 -I -c "
import json,sys
try:
    d=json.load(open(sys.argv[1],encoding='utf-8'))
    s=d['normalized_result']['source']
    print(s['text'] if isinstance(s,dict) else s)
except Exception as e:
    print('ERR',e); print(open(sys.argv[1],encoding='utf-8',errors='replace').read()[:1500])
" "$OUT"
