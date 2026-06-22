import urllib.request
import json
import ssl

url = 'https://otp-auth-worker.jatinsharmashivpuri02122008.workers.dev/send'
data = json.dumps({'email':'huntwter.dev@gmail.com','action':'send_otp'}).encode('utf-8')
headers = {
    'Content-Type': 'application/json',
    'X-App-Api-Key': 'c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'
}

try:
    req = urllib.request.Request(url, data=data, headers=headers)
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with urllib.request.urlopen(req, context=ctx) as response:
        print("Status:", response.status)
        print("Response:", response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print("HTTP Error:", e.code)
    print("Response:", e.read().decode('utf-8'))
except Exception as e:
    print("Error:", e)
