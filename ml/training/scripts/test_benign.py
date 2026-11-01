import urllib.request

sources = {
    'umbrella_zip': 'https://s3-us-west-1.amazonaws.com/umbrella-static/top-1m.csv.zip',
    'umbrella_csv': 'http://s3-us-west-1.amazonaws.com/umbrella-static/top-1m.csv',
    'tranco': 'https://tranco-list.com/download/3Z3JD/full',
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
