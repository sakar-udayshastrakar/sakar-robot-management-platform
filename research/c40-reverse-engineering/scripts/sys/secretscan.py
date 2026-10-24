"""Locate secret-like string constants WITHOUT printing their values. Output: class#method, kind, length. Usage: secretscan.py <App>"""
import pickle, re, sys, os
B = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
app = sys.argv[1]
D = pickle.load(open(os.path.join(B, 'sysidx', app + '.pkl'), 'rb'))
SKIP = re.compile(r'^L(androidx|android/|com/google|org/|okhttp|com/hierynomus|com/microsoft/cognitiveservices|com/alibaba|io/|kotlin|com/tencent|com/iflytek/(cloud|speech)|net/|com/squareup|retrofit2|freemarker|javax|com/sun|junit|org/bouncycastle)')
PATS = [
    ('bearer-token', re.compile(r'Bearer\s+\S{8,}')),
    ('sk-style-api-key', re.compile(r'\bsk-[A-Za-z0-9]{16,}')),
    ('azure-like-key', re.compile(r'^[A-Za-z0-9]{70,100}$')),
    ('pem-private-key', re.compile(r'BEGIN (RSA |EC )?PRIVATE KEY')),
    ('hex32', re.compile(r'^[0-9A-Fa-f]{32}$')),
    ('hex40', re.compile(r'^[0-9A-Fa-f]{40}$')),
    ('b64-20+', re.compile(r'^[A-Za-z0-9+/]{24,}={0,2}$')),
    ('credential-word', re.compile(r'(?i)(password|passwd|secret|api_?key|access_?key|token)\s*[=:]\s*\S+')),
    ('sentry-dsn', re.compile(r'https?://[0-9a-f]{20,}@')),
    ('aes-key-16', re.compile(r'^[A-Za-z0-9]{16}$')),
]
seen = set()
for c, e in sorted(D.items()):
    if SKIP.match(c): continue
    for k, i in e['methods'].items():
        for s in i['str']:
            for kind, rx in PATS:
                if rx.search(s):
                    if kind in ('aes-key-16', 'b64-20+', 'hex32', 'hex40') and not re.search(r'(?i)key|iv|aes|secret|crypt|cipher|decrypt|encrypt|sign|auth|des|rsa', c + k): continue
                    key = (c, k, kind, len(s))
                    if key in seen: continue
                    seen.add(key)
                    print('%s#%s\t%s\tlen=%d' % (c, k, kind, len(s)))
