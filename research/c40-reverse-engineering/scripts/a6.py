import os,sys,json,re
import xml.etree.ElementTree as ET
SP=sys.argv[1]
W=SP+'/debwork'
out=open(SP+'/an/launch.txt','w',encoding='utf-8')
nodes=[]
for deb in sorted(os.listdir(W)):
    pkg=deb.split('_')[0].replace('ros-indigo-','')
    for root,dirs,files in os.walk(W+'/'+deb):
        for fn in sorted(files):
            if not fn.endswith('.launch'): continue
            p=os.path.join(root,fn)
            rel=os.path.relpath(p,W+'/'+deb).replace(chr(92),'/').replace('opt/ros/indigo/share/','')
            if 'roslaunch/resources' in rel: continue
            try:
                t=ET.parse(p).getroot()
            except Exception as e:
                out.write('## %s %s PARSE_ERROR %r\n'%(pkg,rel,e)); continue
            out.write('## %s :: %s\n'%(pkg,rel))
            for el in t.iter():
                if el.tag=='node':
                    a=el.attrib
                    ch=[]
                    for c in el:
                        if c.tag=='remap': ch.append('remap %s->%s'%(c.get('from'),c.get('to')))
                        elif c.tag=='param': ch.append('param %s=%s'%(c.get('name'),(c.get('value') or '')[:60]))
                        elif c.tag=='rosparam': ch.append('rosparam %s'%(c.get('file') or (c.text or '').strip()[:60]))
                    out.write('  NODE pkg=%s type=%s name=%s ns=%s args=%s if=%s | %s\n'%(a.get('pkg'),a.get('type'),a.get('name'),a.get('ns'),a.get('args'),a.get('if') or a.get('unless'),'; '.join(ch)))
                    nodes.append((rel,a.get('pkg'),a.get('type'),a.get('name')))
                elif el.tag=='include':
                    out.write('  INCLUDE %s if=%s\n'%(el.get('file'),el.get('if') or el.get('unless')))
                elif el.tag=='rosparam' and el.get('file'):
                    out.write('  ROSPARAM %s\n'%el.get('file'))
                elif el.tag=='param' and el.get('name') and el.get('value') is not None and el.tag!='node':
                    pass
json.dump(nodes,open(SP+'/an/launch_nodes.json','w'))
print(len(nodes))
