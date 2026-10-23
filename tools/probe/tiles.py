import re, io, html, urllib.request, statistics
from PIL import Image, ImageDraw
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def get(u):
    try: return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "fr-FR"}), timeout=15).read(3_000_000)
    except Exception: return b""
def play(pkg):
    h = get(f"https://play.google.com/store/apps/details?id={pkg}&hl=fr&gl=FR").decode("utf-8", "ignore")
    m = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', h)
    return re.sub(r'=[a-z0-9-]+$', '', html.unescape(m.group(1))) + "=s256" if m else None
def gst(d): return f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://{d}&size=256"
stores = [("E.Leclerc", play("com.infomil.bill")), ("Intermarché", play("fr.stime.mcommerce")), ("JouéClub", gst("joueclub.fr")),
          ("Carrefour", play("com.carrefour.fid.android")), ("Lidl", play("com.lidl.eci.lidlplus")), ("Action", play("com.action.consumerapp")),
          ("Decathlon", gst("decathlon.fr")), ("Leroy Merlin", play("com.adeo.android.app")), ("Auchan", play("com.auchan.android"))]
S = 3  # densité
TW, TH, GAP = 105 * S, int(105 / 1.586) * S, 10 * S
out = Image.new("RGB", (3 * TW + 4 * GAP, 3 * (TH + 22 * S) + 4 * GAP), (0, 7, 26))
d = ImageDraw.Draw(out)
for i, (name, url) in enumerate(stores):
    x = GAP + (i % 3) * (TW + GAP); y = GAP + (i // 3) * (TH + 22 * S + GAP)
    b = get(url) if url else b""
    try: im = Image.open(io.BytesIO(b)).convert("RGBA")
    except Exception: im = None
    tile = Image.new("RGBA", (TW, TH), (80, 80, 80, 255))
    if im:
        w, h = im.size; ins = max(1, min(w, h) // 25); px = im.load(); cols = []; clear = 0; tot = 0
        for k in range(24):
            f = 0.2 + 0.6 * k / 23
            for (sx, sy) in [(int(w*f), ins), (int(w*f), h-1-ins), (ins, int(h*f)), (w-1-ins, int(h*f))]:
                p = px[sx, sy]; tot += 1
                if p[3] < 128: clear += 1
                else: cols.append(p[:3])
        transp = clear * 2 > tot or not cols
        bg = (255, 255, 255) if transp else tuple(int(statistics.median_low(c[j] for c in cols)) for j in range(3))
        tile = Image.new("RGBA", (TW, TH), bg + (255,))
        lh = int(TH * (0.72 if transp else 1.0)); lw = int(lh * w / h)
        lg = im.resize((lw, lh), Image.LANCZOS); tile.alpha_composite(lg, ((TW - lw) // 2, (TH - lh) // 2))
    m = Image.new("L", (TW, TH), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, TW - 1, TH - 1), radius=10 * S, fill=255)
    out.paste(tile.convert("RGB"), (x, y), m)
    d.text((x + TW // 2, y + TH + 10 * S), name, fill=(255, 255, 255), anchor="mm", font_size=11 * S)
out.save("tiles.png")
