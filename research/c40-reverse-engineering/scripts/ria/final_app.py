import json,re,sys
B=sys.argv[1]
OUT=r"D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS\partials\installation_assistant_app.md"
t=open(B+'/ria/app_template.md',encoding='utf-8').read()
t=t.replace('<!--EXPORTED_TABLE-->',open(B+'/ria/gen_exported.md',encoding='utf-8').read())
tbl=json.load(open(B+'/ria/js_ros_table.json'))
rows=['| Name | Type | Constant |','|---|---|---|']
for r in tbl: rows.append('| `%s` | `%s` | %s |'%(r['name'],r['type'] or '-',r['const']))
t=t.replace('<!--ROS_TABLE-->','\n'.join(rows))
paths=sorted(set(re.findall(r"'(/api/[A-Za-z0-9_/\.\{\}\$]+)'",open(B+'/ria/js_paths.txt',encoding='utf-8').read())))
t=t.replace('<!--SPA_REST-->','\n'.join('- `%s`'%p for p in paths))
open(OUT,'w',encoding='utf-8').write(t)
print(len(t),len(paths))
