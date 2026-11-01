import urllib.request

sources = {
    'openphish': 'https://raw.githubusercontent.com/openphish/public_feed/master/phishing.txt',
    'urlhaus': 'https://urlhaus.abuse.ch/downloads/text/',
    'phishstats': 'https://www.phishstats.info/2024_phishing_domains.json',
}
for name, u in sources.items():
    print('Testing:', name, u)
    try:
        req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
        r = urllib.request.urlopen(req, timeout=15)
        data = r.read(200)
        print(f'  OK {r.status} len={r.headers.get("Content-Length")}')
        print(f'  sample: {data[:100]}')
    except Exception as e:
        print(f'  ERR: {e}')
