import urllib.request, zipfile, io, os

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
url = 'https://s3-us-west-1.amazonaws.com/umbrella-static/top-1m.csv.zip'
out = os.path.join(DATA_DIR, 'umbrella_top1m.csv.zip')

print('Downloading Cisco Umbrella top-1M...')
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=120) as r:
    data = r.read()
with open(out, 'wb') as f:
    f.write(data)
print(f'Downloaded {len(data)} bytes')

z = zipfile.ZipFile(io.BytesIO(data))
z.extractall(DATA_DIR)
print('Extracted:', z.namelist())

csv_path = os.path.join(DATA_DIR, 'top-1m.csv')
with open(csv_path, 'r') as f:
    n = sum(1 for _ in f)
print(f'top-1m.csv has {n} lines')
