#!/bin/bash
# usage: dd.sh <ServiceSystem|RemoteControl|krlog> <classregex> [methodregex]
A="/d/Sakar Robotics Projects/C40 S-LS-M014C00-RW-F00-V246"
case "$1" in
 ServiceSystem) F="$A/ServiceSystem-v2.0.11-0-g94566e5-prod-release-p-rk3288-signed.apk";;
 RemoteControl) F="$A/RemoteControl-v3.3.1-0-gfe68b6d-prod-release-p-rk3288-signed.apk";;
 krlog) F="$A/krlog-v1.2.6-0-g18a40d0-prod-release-p-rk3288-signed.apk";;
esac
shift
exec python -X utf8 -I "$(dirname "$0")/dexdis.py" "$F" "$@"
