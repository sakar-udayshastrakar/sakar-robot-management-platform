#!/bin/bash
# usage: rea_batch.sh listfile  (lines: class method [overload])
B="C:/Users/sakar/AppData/Local/Temp/claude/D--Sakar-Robotics-Projects-support-portal/5ba6b382-e7e8-4ce5-92df-45edd177f75a/scratchpad"
A="D:/Sakar Robotics Projects/C40 S-LS-M014C00-RW-F00-V246/peanut-clean-v3.7.8-0-g153214a-prod-release-p.apk"
while read -r cls m ov; do
  [ -z "$cls" ] && continue
  out="$B/pcl_out/jadx/$cls.$m${ov:+.$ov}.json"
  [ -s "$out" ] && continue
  if [ -n "$ov" ]; then bash "$B/scripts/rea.sh" inspect-android-method "$A" "$cls" "$m" --overload-index "$ov" --format json > "$out" 2>"$out.err" < /dev/null
  else bash "$B/scripts/rea.sh" inspect-android-method "$A" "$cls" "$m" --format json > "$out" 2>"$out.err" < /dev/null; fi
done < "$1"
echo done > "$1.done"
