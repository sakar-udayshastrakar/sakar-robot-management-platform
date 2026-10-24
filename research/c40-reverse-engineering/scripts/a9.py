import os,sys,re,json
SP=sys.argv[1]
W=SP+'/debwork'
elf=json.load(open(SP+'/an/elf.json'))
res={}
badpref=('/opt','/etc','/home','/usr','/tmp','/dev','/proc','/sys','/work','/var','/workdir','/lib','/bin','/root','/catkin','/run','/mnt')
rx_name=re.compile(rb'^/?[A-Za-z][A-Za-z0-9_]*(?:/[A-Za-z0-9_~]+)+$')
rx_slash=re.compile(rb'^/[A-Za-z][A-Za-z0-9_]*$')
rx_type=re.compile(rb'^(?:keenon_[a-z_]+|std_msgs|geometry_msgs|nav_msgs|sensor_msgs|tf2_msgs|tf|actionlib_msgs|visualization_msgs|map_msgs|cartographer_ros_msgs|peanut_localization|costmap_2d|web_backend|robot_settings|kplanner|navfn|std_srvs|diagnostic_msgs|rosgraph_msgs|dynamic_reconfigure|teb_local_planner|costmap_converter)/[A-Za-z0-9_]+$')
for r in elf:
    if r.get('type')!='EXEC' or r.get('machine')!='ARM': continue
    if '/lib/' not in r['path'] or not r['path'].startswith('opt/ros/indigo/lib/'): continue
    p=os.path.join(W,[d for d in os.listdir(W) if d.startswith(r['deb']+'_')][0],r['path'])
    try: d=open(p,'rb').read()
    except Exception: continue
    names=set();types=set()
    for m in re.finditer(rb'[\x21-\x7e]{4,120}',d):
        s=m.group()
        if rx_type.match(s) and not s.endswith((b'Request',b'Response',b'Goal',b'Result',b'Feedback')):
            types.add(s.decode())
        elif (rx_name.match(s) or rx_slash.match(s)) and s.startswith(b'/') and not s.startswith(tuple(x.encode() for x in badpref)):
            names.add(s.decode())
    res[r['deb'].replace('ros-indigo-','')+':'+r['path'].split('/')[-1]]={'names':sorted(names),'types':sorted(types)}
json.dump(res,open(SP+'/an/topics.json','w'),indent=0)
print(len(res))
