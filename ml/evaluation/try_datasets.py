import urllib.request

urls = [
    ('phishdb_active', 'https://raw.githubusercontent.com/mitchellkrogza/Phishing.Database/master/phishing-domains-ACTIVE.txt'),
    ('joewings_phishing_urls', 'https://raw.githubusercontent.com/joewings/phishing-urls/master/phishing-urls.txt'),
    ('ahmedrefaat_dataset', 'https://raw.githubusercontent.com/ahmedrefaat/Phishing-Website-Detection/master/dataset.csv'),
]
for name, u in urls:
    try:
        req = urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=25) as r:
            data = r.read()
        first = data.splitlines()[0][:90] if data.splitlines() else b'EMPTY'
        print(f'{name}: {len(data)} bytes, first={first!r}')
    except Exception as e:
        print(f'{name}: ERROR {type(e).__name__}: {str(e)[:140]}')
