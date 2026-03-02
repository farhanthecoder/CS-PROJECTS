#!/usr/bin/env python3
"""
Partner Doodle — PC Partner Mode
Graffiti-style doodles sent straight to your partner's lock screen.
"""

import requests, time, io, sys, math, base64, json, os, random

try:
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
except ImportError:
    print("Installing Pillow…")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ── Firebase config ──────────────────────────────────────────────────────────
API_KEY    = "AIzaSyApINMWTy3PS4D5eQuuRtRb8MP3mob4a5g"
PROJECT_ID = "doodle-app-dbace"
DB_URL     = f"https://{PROJECT_ID}-default-rtdb.firebaseio.com"

IDENTITY_FILE = os.path.expanduser("~/.partnerdoodle_identity.json")

# ── Firebase Auth ─────────────────────────────────────────────────────────────

def sign_in_anonymously():
    if os.path.exists(IDENTITY_FILE):
        try:
            saved = json.load(open(IDENTITY_FILE))
            rt = saved.get("refreshToken")
            if rt:
                r = requests.post(
                    f"https://securetoken.googleapis.com/v1/token?key={API_KEY}",
                    json={"grant_type": "refresh_token", "refresh_token": rt}, timeout=10)
                d = r.json()
                if "id_token" in d:
                    uid, tok = d["user_id"], d["id_token"]
                    json.dump({"uid": uid, "refreshToken": d["refresh_token"]},
                              open(IDENTITY_FILE, "w"))
                    print(f"  (Reusing identity: …{uid[-6:].upper()})")
                    return uid, tok
        except Exception:
            pass

    r = requests.post(
        f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={API_KEY}",
        json={"returnSecureToken": True}, timeout=10)
    d = r.json()
    if "localId" not in d:
        print(f"Auth failed: {d}"); sys.exit(1)
    uid, tok = d["localId"], d["idToken"]
    json.dump({"uid": uid, "refreshToken": d["refreshToken"]}, open(IDENTITY_FILE, "w"))
    return uid, tok

# ── Pairing ───────────────────────────────────────────────────────────────────

def create_pairing_code(uid, tok):
    code = uid[-6:].upper()
    requests.put(f"{DB_URL}/pairingCodes/{code}.json?auth={tok}", json=uid, timeout=10)
    return code

def pair_with_code(my_uid, code, tok):
    r = requests.get(f"{DB_URL}/pairingCodes/{code.upper()}.json?auth={tok}", timeout=10)
    partner = r.json()
    if not isinstance(partner, str):
        return None, "Code not found. Make sure the phone app is open."
    if partner == my_uid:
        return None, "That's your own code — enter your phone's code."
    requests.put(f"{DB_URL}/users/{my_uid}/partnerId.json?auth={tok}", json=partner, timeout=10)
    requests.put(f"{DB_URL}/users/{partner}/partnerId.json?auth={tok}", json=my_uid, timeout=10)
    return partner, None

# ── Graffiti image engine ─────────────────────────────────────────────────────

NEONS = [
    (255, 60,  130),   # hot pink
    (255, 220,   0),   # neon yellow
    (  0, 255, 160),   # neon mint
    ( 80, 200, 255),   # electric blue
    (255, 110,   0),   # orange
    (210,  80, 255),   # purple
    (255, 255, 255),   # white
    (  0, 255,  80),   # lime
]

FONT_PATHS = [
    "/System/Library/Fonts/Supplemental/Impact.ttf",
    "/Library/Fonts/Impact.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Narrow Bold.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/System/Library/Fonts/Arial.ttf",
]

def get_font(size: int) -> ImageFont.ImageFont:
    for path in FONT_PATHS:
        try:
            return ImageFont.truetype(path, size)
        except Exception:
            continue
    # Pillow 10+ supports size parameter on load_default
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()

def stamp_word(canvas: Image.Image, word: str, cx: int, cy: int,
               angle: float, color: tuple, size: int, rng: random.Random):
    """Render a single word with thick outline + glow, rotated onto the canvas."""
    font = get_font(size)

    # Measure
    probe = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    bb = probe.textbbox((0, 0), word, font=font)
    tw = bb[2] - bb[0]
    th = bb[3] - bb[1]
    pad = max(size // 3, 20)
    W, H = tw + pad * 2, th + pad * 2

    # Word layer (RGBA)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ox, oy = pad - bb[0], pad - bb[1]

    # Thick black outline — draw text offset in 8 directions
    outline = max(size // 14, 4)
    for dx in range(-outline, outline + 1, max(1, outline // 2)):
        for dy in range(-outline, outline + 1, max(1, outline // 2)):
            if dx == 0 and dy == 0: continue
            ld.text((ox + dx, oy + dy), word, font=font, fill=(0, 0, 0, 240))

    # Coloured shadow offset (+4, +5) for depth
    ld.text((ox + 4, oy + 5), word, font=font,
            fill=tuple(max(0, c - 80) for c in color) + (200,))

    # Main text
    ld.text((ox, oy), word, font=font, fill=color + (255,))

    # Subtle spray-paint glow: blur a copy and composite behind
    glow = layer.filter(ImageFilter.GaussianBlur(radius=size // 10))
    combined = Image.alpha_composite(glow, layer)

    # Rotate with expand so nothing gets clipped
    rotated = combined.rotate(angle, expand=True, resample=Image.BICUBIC)

    # Paste onto canvas centred at (cx, cy)
    px = cx - rotated.width  // 2
    py = cy - rotated.height // 2
    canvas.paste(rotated, (px, py), rotated)


def drip(draw: ImageDraw.ImageDraw, x: int, y: int,
         color: tuple, rng: random.Random):
    """Paint a random graffiti drip below a word."""
    for _ in range(rng.randint(1, 3)):
        dx  = x + rng.randint(-30, 30)
        dy  = y
        length = rng.randint(30, 100)
        width  = rng.randint(4, 10)
        # Teardrop shape
        draw.rectangle([dx - width//2, dy, dx + width//2, dy + length],
                       fill=color + (160,))
        draw.ellipse([dx - width//2, dy + length - width//2,
                      dx + width//2, dy + length + width//2],
                     fill=color + (160,))


def make_doodle_image(message: str) -> Image.Image:
    rng = random.Random(abs(hash(message + str(time.time()))))
    W, H = 720, 1280

    # ── Background: dark wall with subtle spray texture ──────────────────────
    canvas = Image.new("RGBA", (W, H), (10, 10, 18, 255))
    bd = ImageDraw.Draw(canvas)

    # Faint colour wash blobs
    for _ in range(6):
        bx = rng.randint(0, W); by = rng.randint(0, H)
        bw = rng.randint(200, 500); bh = rng.randint(200, 500)
        bc = rng.choice(NEONS)
        blob = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
        ImageDraw.Draw(blob).ellipse([0, 0, bw, bh], fill=bc + (18,))
        canvas.paste(blob, (bx - bw//2, by - bh//2), blob)

    # Fine spray dots
    for _ in range(800):
        sx = rng.randint(0, W); sy = rng.randint(0, H)
        sr = rng.randint(1, 3)
        sc = rng.choice(NEONS)
        bd.ellipse([sx-sr, sy-sr, sx+sr, sy+sr], fill=sc + (rng.randint(8, 35),))

    # ── Words ────────────────────────────────────────────────────────────────
    words = message.upper().split()
    if not words:
        words = ["HEY!"]

    n = len(words)
    max_len = max(len(w) for w in words)

    # Base font size: large for few/short words, smaller for many/long words
    base = int(min(680 / max(max_len, 2), 260 / max(n, 1)))
    base = max(base, 55)

    # Divide canvas height into n zones, placing one word per zone
    zone_h = H // n
    placements = []
    for i, word in enumerate(words):
        y_lo = zone_h * i + zone_h // 5
        y_hi = zone_h * (i + 1) - zone_h // 5
        cy = rng.randint(y_lo, y_hi)
        # Horizontal scatter — keep roughly centred but offset randomly
        cx = W // 2 + rng.randint(-W // 5, W // 5)
        cx = max(120, min(W - 120, cx))
        angle = rng.uniform(-38, 38)
        size  = base + rng.randint(-15, 25)
        color = NEONS[(i * 3 + rng.randint(0, 2)) % len(NEONS)]
        placements.append((word, cx, cy, angle, size, color))

    for word, cx, cy, angle, size, color in placements:
        stamp_word(canvas, word, cx, cy, angle, color, size, rng)
        # Random drip under some words
        if rng.random() < 0.45:
            drip(ImageDraw.Draw(canvas), cx + rng.randint(-40, 40),
                 cy + size // 2 + 10, color, rng)

    # ── Decorative stars / asterisks ─────────────────────────────────────────
    for _ in range(rng.randint(5, 12)):
        sx = rng.randint(30, W - 30); sy = rng.randint(30, H - 30)
        sc = rng.choice(NEONS)
        sr = rng.randint(6, 18)
        star_img = Image.new("RGBA", (sr*4, sr*4), (0, 0, 0, 0))
        sd = ImageDraw.Draw(star_img)
        cx2, cy2 = sr*2, sr*2
        for a in range(0, 360, 45):
            rad = math.radians(a)
            ex = cx2 + int(sr * math.cos(rad))
            ey = cy2 + int(sr * math.sin(rad))
            sd.line([cx2, cy2, ex, ey], fill=sc + (200,), width=2)
        canvas.paste(star_img, (sx - sr*2, sy - sr*2), star_img)

    return canvas.convert("RGB")


# ── Send doodle via RTDB (Base64, no Storage needed) ─────────────────────────

def send_doodle(uid: str, tok: str, img: Image.Image) -> bool:
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=75)
    b64 = base64.b64encode(buf.getvalue()).decode()
    resp = requests.put(
        f"{DB_URL}/doodles/{uid}.json?auth={tok}",
        json={"imageData": b64, "timestamp": {".sv": "timestamp"}, "fromUserId": uid},
        timeout=15)
    return resp.status_code == 200


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("\n" + "═" * 50)
    print("  💌  Partner Doodle — PC Partner Mode")
    print("═" * 50 + "\n")

    print("Connecting to Firebase…")
    uid, tok = sign_in_anonymously()
    code = create_pairing_code(uid, tok)

    # Check if already paired
    r = requests.get(f"{DB_URL}/users/{uid}/partnerId.json?auth={tok}", timeout=10)
    existing_partner = r.json() if r.status_code == 200 else None

    if existing_partner and isinstance(existing_partner, str):
        print(f"\n✅  Already paired! Sending straight to your lock screen.\n")
        print("─" * 50)
        print("Type a message and press Enter to send a graffiti doodle.")
        print("Type 'quit' to exit.\n")
    else:
        print(f"\n✅  Your PC's pairing code:  \033[1;35m{code}\033[0m")
        print("\n👉  On your phone:")
        print("    1. Open Partner Doodle")
        print("    2. Tap 'Enter Partner's Code'")
        print(f"   3. Type  \033[1;35m{code}\033[0m  → tap Connect\n")
        phone_code = input("Then enter your PHONE's pairing code here: ").strip().upper()
        if not phone_code:
            print("No code entered. Exiting."); return
        print("\nPairing…")
        _, err = pair_with_code(uid, phone_code, tok)
        if err:
            print(f"❌  {err}"); return
        print("💕  Paired!\n")

    # Send a welcome doodle
    print("Sending a test doodle…")
    img = make_doodle_image("YO!")
    if send_doodle(uid, tok, img):
        print("✅  Doodle sent! Check your lock screen.\n")
    else:
        print("❌  Send failed. Check your Firebase Realtime Database rules.\n")
        return

    print("─" * 50)
    print("Type any message → graffiti doodle appears on the lock screen.")
    print("Type 'quit' to exit.\n")

    while True:
        try:
            msg = input("Your message: ").strip()
        except (KeyboardInterrupt, EOFError):
            break
        if msg.lower() in ("quit", "exit", "q"):
            break
        if not msg:
            msg = "💕"
        img = make_doodle_image(msg)
        if send_doodle(uid, tok, img):
            print("✅  Sent!\n")
        else:
            print("❌  Failed.\n")

    print("\nGoodbye! 💌\n")

if __name__ == "__main__":
    main()
