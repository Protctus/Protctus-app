import urllib.request
import urllib.error
import json

# Teri API details
base_url = "https://otp-auth-worker.jatinsharmashivpuri02122008.workers.dev"
api_key = "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2" # ISE BAAD MEIN ROTATE KAR LENA!

print("🚀 Protctus API Tester Tool")
print("-" * 30)

# User se inputs lena
email = input("1. Enter test email: ").strip()
password = input("2. Enter test password: ").strip()

headers = {
    "Content-Type": "application/json",
    "X-App-Api-Key": api_key,
    "User-Agent": "okhttp/4.12.0"  # mock android okhttp
}

def req(path, data):
    request = urllib.request.Request(base_url + path, data=json.dumps(data).encode(), headers=headers)
    try:
        resp = urllib.request.urlopen(request)
        return resp.getcode(), resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return 0, str(e)

# --- STEP 1: SEND OTP ---
print(f"\n[📡] Sending OTP to {email}...")
code, body = req("/send", {"email": email})
print(f"Server Response: [{code}] {body}")

if code == 200:
    print("\n✅ OTP Sent Successfully!")
    
    # --- STEP 2: PAUSE FOR USER INPUT ---
    print("\n⏳ Please check your inbox (and spam folder) for the OTP.")
    actual_otp = input("👉 Enter the 6-digit OTP received: ").strip()

    # --- STEP 3: VERIFY OTP ---
    print(f"\n[🔐] Verifying OTP: {actual_otp}...")
    verify_code, verify_body = req("/verify", {
        "email": email,
        "otp": actual_otp,
        "password": password
    })
    print(f"Server Response: [{verify_code}] {verify_body}")

    # --- THE FINAL VERDICT ---
    print("-" * 30)
    if verify_code == 200:
        print("🏆 VERDICT: The Cloudflare API and Firebase are working 100% PERFECTLY!")
        print("Agar app mein login nahi ho raha hai, toh bug pakka Android (ViewModel/UI) mein hai.")
    else:
        print("❌ VERDICT: Verification Failed! Problem Cloudflare Worker ya Firebase logic mein hai.")

else:
    print("\n❌ Failed to send OTP. Check Resend limit or Worker Logs.")