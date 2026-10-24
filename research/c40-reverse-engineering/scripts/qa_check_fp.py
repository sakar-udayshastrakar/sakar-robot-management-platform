import os, re, glob, hashlib
SCR = r'C:\Users\sakar\AppData\Local\Temp\claude\D--Sakar-Robotics-Projects-support-portal\5ba6b382-e7e8-4ce5-92df-45edd177f75a\scratchpad'
sd = glob.glob(os.path.join(SCR, 'debwork', 'ros-indigo-show-points*', '**', 'send_depth_node'), recursive=True)
d = open(sd[0], 'rb').read()
pw = re.search(rb'echo\s+(\S{6,20})\s*\|\s*sudo\s+-S', d).group(1)
hostapd = glob.glob(os.path.join(SCR, 'debwork', 'ros-indigo-robot-settings*', '**', 'hostapd.conf'), recursive=True)[0]
psk = None
for line in open(hostapd, 'rb').read().splitlines():
    if line.startswith(b'wpa_passphrase='):
        psk = line.split(b'=', 1)[1]
# characterize WITHOUT printing value
def ch(label, s):
    import string
    alpha = all(chr(c) in string.ascii_letters for c in s)
    digit = all(chr(c) in string.digits for c in s)
    alnum = all(chr(c) in (string.ascii_letters + string.digits) for c in s)
    print(label, 'len', len(s), 'sha256[:10]', hashlib.sha256(s).hexdigest()[:10],
          'alpha', alpha, 'lower', s == s.lower(), 'alnum_only', alnum,
          'has_digit', any(chr(c) in string.digits for c in s),
          'has_special', not alnum)
ch('sudo_pw', pw)
ch('wifi_psk', psk)
print('sudo_pw == wifi_psk :', pw == psk)
# Is it a dictionary/brand-like word? count how many times it appears in a clearly-unrelated large corpus:
# the AndroidManifest texts (brand words appear a lot; a random password would not)
man = SCR + r'\manifests'
hits = 0
for f in glob.glob(man + r'\*.xml'):
    hits += open(f, 'rb').read().count(pw)
print('sudo_pw occurrences across decoded manifests (brand-word indicator):', hits)
# occurrences in a ROS source .cmake (totally unrelated to secrets)
cm = glob.glob(os.path.join(SCR, 'debwork', '*', '**', '*Config.cmake'), recursive=True)
cmhits = sum(open(f, 'rb').read().count(pw) for f in cm[:50])
print('sudo_pw occurrences across 50 ROS *Config.cmake (brand-word indicator):', cmhits)
