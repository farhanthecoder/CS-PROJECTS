#!/usr/bin/env python3
"""
Partner Doodle — PC Partner Mode
Run this on your Mac to act as the partner phone.
Doodles are stored as Base64 JPEG directly in Firebase Realtime Database —
no Firebase Storage (paid plan) required.
"""

import requests
import time
import io
import sys
import math
import base64

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Installing Pillow…")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image, ImageDraw

# ── Firebase config ──────────────────────────────────────────────────────────
API_KEY    = "AIzaSyApINMWTy3PS4D5eQuuRtRb8MP3mob4a5g"
PROJECT_ID = "doodle-app-dbace"
DB_URL     = f"https://{PROJECT_ID}-default-rtdb.firebaseio.com"

# ── Firebase Auth ─────────────────────────────────────────────────────────────

def sign_in_anonymously():
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={API_KEY}"
    resp = requests.post(url, json={"returnSecureToken": True}, timeout=10)
    data = resp.json()
    if "localId" not in data:
        print(f"Auth failed: {data}")
        sys.exit(1)
    return data["localId"], data["idToken"]

# ── Pairing ───────────────────────────────────────────────────────────────────

def create_pairing_code(uid, id_token):
    code = uid[-6:].upper()
    requests.put(
        f"{DB_URL}/pairingCodes/{code}.json?auth={id_token}",
        json=uid, timeout=10
    )
    return code

def pair_with_code(my_uid, code, id_token):
    resp = requests.get(
        f"{DB_URL}/pairingCodes/{code.upper()}.json?auth={id_token}",
        timeout=10
    )
    partner_uid = resp.json()
    if not partner_uid or not isinstance(partner_uid, str):
        return None, "Code not found. Make sure your phone app is open and try again."
    if partner_uid == my_uid:
        return None, "That's your own code — enter your phone's code."

    requests.put(f"{DB_URL}/users/{my_uid}/partnerId.json?auth={id_token}",
                 json=partner_uid, timeout=10)
    requests.put(f"{DB_URL}/users/{partner_uid}/partnerId.json?auth={id_token}",
                 json=my_uid, timeout=10)
    return partner_uid, None

# ── Doodle image creation ─────────────────────────────────────────────────────

PINK   = (255, 59, 128)
PURPLE = (124, 58, 237)
WHITE  = (255, 255, 255)
DARK   = (28, 28, 46)

def draw_heart(draw, cx, cy, size, color):
    points = []
    for i in range(360):
        a = math.radians(i)
        x = size * 16 * math.sin(a) ** 3
        y = -size * (13 * math.cos(a) - 5 * math.cos(2*a)
                     - 2 * math.cos(3*a) - math.cos(4*a))
        points.append((cx + x, cy + y))
    draw.polygon(points, fill=color)

def make_doodle_image(message: str) -> Image.Image:
    W, H = 720, 1280
    img = Image.new("RGB", (W, H), DARK)
    draw = ImageDraw.Draw(img)

    # Subtle gradient background
    for i in range(H):
        t = i / H
        draw.line([(0, i), (W, i)],
                  fill=(int(28 + t*12), int(28 + t*6), int(46 + t*24)))

    # Hearts
    draw_heart(draw, W // 2, H // 2 - 160, 14, PINK)
    draw_heart(draw, 130, H // 2 - 380, 5,  (*PURPLE, 160))
    draw_heart(draw, 600, H // 2 - 300, 4,  (*PINK,   140))
    draw_heart(draw, 100, H // 2 +  80, 3,  (*PINK,   120))
    draw_heart(draw, 640, H // 2 + 160, 5,  (*PURPLE, 150))

    # Message text (PIL default font — small but readable)
    words = message.split()
    lines, cur = [], ""
    for w in words:
        if len(cur) + len(w) + 1 <= 24:
            cur += (" " if cur else "") + w
        else:
            if cur: lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)

    y = H // 2 + 70
    for line in lines:
        draw.text((W // 2, y), line, fill=WHITE, anchor="mm")
        y += 28

    # Timestamp
    ts = time.strftime("%-I:%M %p  ·  %b %-d")
    draw.text((W // 2, H - 120), f"from your Mac  •  {ts}",
              fill=(*PINK, 200), anchor="mm")

    return img

# ── Send doodle via RTDB (Base64, no Storage needed) ─────────────────────────

def send_doodle(uid: str, id_token: str, img: Image.Image) -> bool:
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=72)
    b64 = base64.b64encode(buf.getvalue()).decode("utf-8")

    payload = {
        "imageData":  b64,
        "timestamp":  {".sv": "timestamp"},
        "fromUserId": uid,
    }
    resp = requests.put(
        f"{DB_URL}/doodles/{uid}.json?auth={id_token}",
        json=payload, timeout=15
    )
    return resp.status_code == 200

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("\n" + "═" * 50)
    print("  💌  Partner Doodle — PC Partner Mode")
    print("═" * 50 + "\n")

    print("Connecting to Firebase…")
    uid, id_token = sign_in_anonymously()
    code = create_pairing_code(uid, id_token)

    print(f"\n✅  Your PC's pairing code:  \033[1;35m{code}\033[0m")
    print("\n👉  On your phone:")
    print("    1. Open Partner Doodle")
    print("    2. Tap 'Enter Partner's Code'")
    print(f"   3. Type  \033[1;35m{code}\033[0m  → tap Connect\n")

    phone_code = input("Then enter your PHONE's pairing code here: ").strip().upper()
    if not phone_code:
        print("No code entered. Exiting.")
        return

    print("\nPairing…")
    partner_uid, err = pair_with_code(uid, phone_code, id_token)
    if err:
        print(f"❌  {err}")
        return

    print("💕  Paired! Sending a test doodle to your lock screen…")
    img = make_doodle_image("Hello from your Mac! 💕")
    if send_doodle(uid, id_token, img):
        print("✅  Doodle sent! Check your phone's lock screen.\n")
    else:
        print("❌  Send failed. Check your Firebase Realtime Database rules.\n")
        return

    print("─" * 50)
    print("Send more doodles — type a message and press Enter.")
    print("Type 'quit' to exit.\n")

    while True:
        try:
            msg = input("Your message: ").strip()
        except (KeyboardInterrupt, EOFError):
            break
        if msg.lower() in ("quit", "exit", "q"):
            break
        if not msg:
            msg = "Thinking of you 💕"
        img = make_doodle_image(msg)
        if send_doodle(uid, id_token, img):
            print("✅  Sent! Check your lock screen.\n")
        else:
            print("❌  Failed to send.\n")

    print("\nGoodbye! 💌\n")

if __name__ == "__main__":
    main()
