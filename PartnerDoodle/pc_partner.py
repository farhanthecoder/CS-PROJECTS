#!/usr/bin/env python3
"""
Partner Doodle — PC Partner Mode
Run this on your Mac to act as the partner phone.
It will give you a pairing code to enter in the Android app,
then send doodles that appear on your phone's lock screen.
"""

import requests
import json
import time
import io
import sys
import math

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Installing Pillow...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image, ImageDraw

# ── Firebase config (from google-services.json) ─────────────────────────────
API_KEY       = "AIzaSyApINMWTy3PS4D5eQuuRtRb8MP3mob4a5g"
PROJECT_ID    = "doodle-app-dbace"
DB_URL        = f"https://{PROJECT_ID}-default-rtdb.firebaseio.com"
STORAGE_BUCKET = f"{PROJECT_ID}.firebasestorage.app"

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
    url = f"{DB_URL}/pairingCodes/{code}.json?auth={id_token}"
    requests.put(url, json=uid, timeout=10)
    return code

def pair_with_code(my_uid, code, id_token):
    url = f"{DB_URL}/pairingCodes/{code.upper()}.json?auth={id_token}"
    resp = requests.get(url, timeout=10)
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
    """Draw a filled heart centred at (cx, cy)."""
    points = []
    for i in range(360):
        angle = math.radians(i)
        x = size * 16 * math.sin(angle) ** 3
        y = -size * (13 * math.cos(angle)
                     - 5 * math.cos(2 * angle)
                     - 2 * math.cos(3 * angle)
                     - math.cos(4 * angle))
        points.append((cx + x, cy + y))
    draw.polygon(points, fill=color)

def make_doodle_image(message: str) -> Image.Image:
    W, H = 1080, 1920
    img = Image.new("RGB", (W, H), DARK)
    draw = ImageDraw.Draw(img)

    # Gradient-ish background bars
    for i in range(H):
        t = i / H
        r = int(28 + t * 10)
        g = int(28 + t * 5)
        b = int(46 + t * 20)
        draw.line([(0, i), (W, i)], fill=(r, g, b))

    # Big heart
    draw_heart(draw, W // 2, H // 2 - 200, 18, PINK)

    # Smaller decorative hearts
    draw_heart(draw, 180, H // 2 - 500, 7, (*PURPLE, 180))
    draw_heart(draw, 900, H // 2 - 400, 5, (*PINK, 140))
    draw_heart(draw, 120, H // 2 + 100, 4, (*PINK, 120))
    draw_heart(draw, 950, H // 2 + 200, 6, (*PURPLE, 160))

    # Message — split into lines of ~20 chars
    words = message.split()
    lines, current = [], ""
    for word in words:
        if len(current) + len(word) + 1 <= 22:
            current += (" " if current else "") + word
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)

    # Draw each line (big font simulated by scaled text)
    font_size = 80
    y_start = H // 2 + 80
    for idx, line in enumerate(lines):
        # PIL default font is small; we scale by drawing at larger bbox
        # Use a simple approach: draw text and scale
        tmp = Image.new("RGBA", (W, 120), (0, 0, 0, 0))
        td = ImageDraw.Draw(tmp)
        td.text((W // 2, 60), line, fill=WHITE, anchor="mm")
        # Scale up x3
        scaled = tmp.resize((W * 2, 240), Image.NEAREST)
        # Paste centred
        paste_x = (W - W * 2) // 2
        paste_y = y_start + idx * 100 - 40
        img.paste(scaled, (paste_x, paste_y), scaled)

    # Timestamp at bottom
    ts = time.strftime("%-I:%M %p · %b %-d")
    tmp2 = Image.new("RGBA", (W, 60), (0, 0, 0, 0))
    td2 = ImageDraw.Draw(tmp2)
    td2.text((W // 2, 30), f"from your PC  •  {ts}", fill=(*PINK, 220), anchor="mm")
    scaled2 = tmp2.resize((W * 2, 120), Image.NEAREST)
    img.paste(scaled2, ((W - W * 2) // 2, H - 200), scaled2)

    return img

# ── Firebase Storage upload ───────────────────────────────────────────────────

def upload_doodle(uid: str, id_token: str, img: Image.Image) -> str | None:
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=88)
    img_bytes = buf.getvalue()

    # Upload
    path = f"doodles%2F{uid}%2Flatest.jpg"
    storage_url = (
        f"https://firebasestorage.googleapis.com/v0/b/{STORAGE_BUCKET}/o"
        f"?uploadType=media&name=doodles/{uid}/latest.jpg"
    )
    headers = {
        "Authorization": f"Bearer {id_token}",
        "Content-Type": "image/jpeg",
    }
    resp = requests.post(storage_url, headers=headers, data=img_bytes, timeout=30)
    data = resp.json()

    if "downloadTokens" not in data:
        print(f"  Storage error: {data}")
        return None

    token = data["downloadTokens"]
    encoded = f"doodles%2F{uid}%2Flatest.jpg"
    download_url = (
        f"https://firebasestorage.googleapis.com/v0/b/{STORAGE_BUCKET}/o/"
        f"{encoded}?alt=media&token={token}"
    )

    # Write URL to Realtime DB so the phone listener fires
    doodle_data = {
        "url": download_url,
        "timestamp": {".sv": "timestamp"},
        "fromUserId": uid,
    }
    db_resp = requests.put(
        f"{DB_URL}/doodles/{uid}.json?auth={id_token}",
        json=doodle_data, timeout=10
    )
    if db_resp.status_code != 200:
        print(f"  DB write error: {db_resp.text}")
        return None

    return download_url

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
    print(f"   2. Tap 'Enter Partner's Code'")
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

    print("💕  Paired! Sending a test doodle to your lock screen…\n")
    img = make_doodle_image("Hello from your Mac! 💕")
    url = upload_doodle(uid, id_token, img)
    if url:
        print("✅  Doodle sent! Check your phone's lock screen.\n")
    else:
        print("❌  Upload failed. Check your Firebase Storage rules.\n")
        return

    # Interactive loop
    print("─" * 50)
    print("Send more doodles (type a message and press Enter).")
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
        url = upload_doodle(uid, id_token, img)
        if url:
            print("✅  Sent! Check your lock screen.\n")
        else:
            print("❌  Failed to send.\n")

    print("\nGoodbye! 💌\n")

if __name__ == "__main__":
    main()
