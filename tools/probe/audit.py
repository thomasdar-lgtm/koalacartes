"""Audit des logos : reproduit le choix de l'appli pour chaque enseigne, cherche l'appli officielle
sur le Play Store France, et dessine la planche des tuiles telles qu'elles s'afficheront."""
import re, io, sys, json, html, statistics, unicodedata, urllib.request, urllib.parse
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw

UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
SRC = open("app/src/main/java/com/koala/cartes/Stores.kt", encoding="utf-8").read()
STORES = [(m[0], m[1].replace('\\"', '"'), int(m[2], 16), m[3]) for m in
          re.findall(r's\("(\w+)", "((?:[^"\\]|\\.)*)", (0x[0-9A-F]+), "([^"]+)"', SRC)]
PLAY = dict(re.findall(r'"(\w+)" to "([\w.]+)"', SRC[SRC.index("PLAY = mapOf("):SRC.index("IOS = mapOf(")]))
IOS = dict(re.findall(r'"(\w+)" to (\d+)L', SRC[SRC.index("IOS = mapOf("):SRC.index("val all")]))

def get(u, mx=3_000_000):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "fr-FR,fr;q=0.9"}), timeout=12)
        return r.read(mx), r.geturl()
    except Exception:
        return None, u

def attr(tag, name):
    m = re.search(r'\b%s\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s>]+))' % name, tag, re.I)
    return (m.group(1) or m.group(2) or m.group(3) or "").replace("&amp;", "&") if m else None

def img(b):
    try:
        return Image.open(io.BytesIO(b)).convert("RGBA")
    except Exception:
        return None

def play_icon(pkg):
    b, _ = get(f"https://play.google.com/store/apps/details?id={pkg}&hl=fr&gl=FR")
    if not b: return None, "", ""
    h = b.decode("utf-8", "ignore")
    t = re.search(r"<title[^>]*>([^<]*)", h); title = html.unescape(t.group(1)).split(" – ")[0] if t else "?"
    dev = re.search(r'/store/apps/(?:dev|developer)\?id=([^"&]+)', h)
    m = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', h)
    u = re.sub(r"=[a-z0-9-]+$", "", html.unescape(m.group(1))) + "=s256" if m else None
    return u, title, urllib.parse.unquote_plus(dev.group(1)) if dev else "?"

def ios_icon(i):
    b, _ = get(f"https://itunes.apple.com/lookup?id={i}&country=fr")
    try: return json.loads(b)["results"][0]["artworkUrl512"]
    except Exception: return None

def site_cands(domain):
    if not domain: return []
    hosts = [domain] if domain.startswith("www.") else [domain, "www." + domain]
    c = []
    for h in hosts:
        b, final = get(f"https://{h}/", 600_000)
        if not b: continue
        page = b.decode("utf-8", "ignore")
        links = re.findall(r"<link\b[^>]*>", page, re.I)
        for tag in links:
            rel = (attr(tag, "rel") or "").lower()
            if "manifest" in rel.split():
                mb, mf = get(urllib.parse.urljoin(final, attr(tag, "href") or ""), 200_000)
                try:
                    ics = json.loads(mb).get("icons", [])
                    ics = sorted(ics, key=lambda i: -int((i.get("sizes") or "0x").split("x")[0] or 0))
                    c += [urllib.parse.urljoin(mf, i["src"]) for i in ics if not i.get("src", "").split("?")[0].endswith(".svg")]
                except Exception:
                    pass
        found = []
        for tag in links:
            rel = (attr(tag, "rel") or "").lower(); href = attr(tag, "href")
            if "icon" not in rel or "mask" in rel or not href or href.split("?")[0].lower().endswith(".svg"): continue
            sz = (attr(tag, "sizes") or "").lower().split("x")[0]
            sz = int(sz) if sz.isdigit() else (180 if "apple" in rel else 32)
            found.append((sz + (1000 if "apple" in rel else 0), urllib.parse.urljoin(final, href)))
        c += [u for _, u in sorted(found, reverse=True)]
        break
    for h in hosts: c += [f"https://{h}/apple-touch-icon.png", f"https://{h}/apple-touch-icon-precomposed.png"]
    for h in hosts: c.append(f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://{h}&size=256")
    c += [f"https://www.google.com/s2/favicons?domain={domain}&sz=256", f"https://icons.duckduckgo.com/ip3/{domain}.ico"]
    return list(dict.fromkeys(c))

def choose(cands):
    best = (None, 0, "")
    for u in cands:
        b, _ = get(u)
        im = img(b) if b else None
        if not im: continue
        m = min(im.size)
        if m >= 180: return im, m, u
        if m > best[1]: best = (im, m, u)
    return best if best[1] >= 32 else (None, best[1], best[2])

def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    return re.sub(r"[^a-z0-9]+", " ", "".join(ch for ch in s if unicodedata.category(ch) != "Mn")).strip()

def discover(name):
    b, _ = get("https://play.google.com/store/search?c=apps&hl=fr&gl=FR&q=" + urllib.parse.quote(name))
    if not b: return []
    pk = list(dict.fromkeys(re.findall(r"details\?id=([\w.]+)", b.decode("utf-8", "ignore"))))[:4]
    out = []
    for p in pk:
        u, title, dev = play_icon(p)
        if norm(name).split()[0] in norm(title) or norm(name).replace(" ", "") in norm(title).replace(" ", ""):
            out.append((p, title, dev))
    return out

def audit(st):
    sid, name, color, domain = st
    first = []
    if sid in PLAY: first.append(play_icon(PLAY[sid])[0])
    if sid in IOS: first.append(ios_icon(IOS[sid]))
    res = choose([u for u in first if u] + site_cands(domain))
    disc = []
    return st, res, disc

def tile(im, color, TW, TH, R):
    if im is None:
        t = Image.new("RGBA", (TW, TH), ((color >> 16) & 255, (color >> 8) & 255, color & 255, 255))
    else:
        w, h = im.size; ins = max(1, min(w, h) // 25); px = im.load(); cols = []; clear = tot = 0
        for k in range(24):
            f = 0.2 + 0.6 * k / 23
            for sx, sy in [(int(w * f), ins), (int(w * f), h - 1 - ins), (ins, int(h * f)), (w - 1 - ins, int(h * f))]:
                p = px[sx, sy]; tot += 1
                if p[3] < 128: clear += 1
                else: cols.append(p[:3])
        tr = clear * 2 > tot or not cols
        bg = (255, 255, 255) if tr else tuple(int(statistics.median_low(c[j] for c in cols)) for j in range(3))
        t = Image.new("RGBA", (TW, TH), bg + (255,))
        f = min(1, max(0.45, min(w, h) / 160))
        lh = int(TH * f * (0.72 if tr else 1)); lw = max(1, int(lh * w / h))
        t.alpha_composite(im.resize((lw, lh), Image.LANCZOS), ((TW - lw) // 2, (TH - lh) // 2))
    m = Image.new("L", (TW, TH), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, TW - 1, TH - 1), radius=R, fill=255)
    return t, m

with ThreadPoolExecutor(16) as ex:
    results = list(ex.map(audit, STORES))

S = 2; TW, TH, GAP, LAB = 105 * S, int(105 / 1.586) * S, 10 * S, 30 * S
COLS = 8; rows = (len(results) + COLS - 1) // COLS
sheet = Image.new("RGB", (COLS * TW + (COLS + 1) * GAP, rows * (TH + LAB + GAP) + GAP), (0, 7, 26))
d = ImageDraw.Draw(sheet)
for i, ((sid, name, color, domain), (im, size, src), disc) in enumerate(results):
    x = GAP + (i % COLS) * (TW + GAP); y = GAP + (i // COLS) * (TH + LAB + GAP)
    t, m = tile(im, color, TW, TH, 10 * S)
    sheet.paste(t.convert("RGB"), (x, y), m)
    ok = size >= 96
    d.text((x + TW // 2, y + TH + 9 * S), name[:20], fill=(255, 255, 255), anchor="mm", font_size=10 * S)
    d.text((x + TW // 2, y + TH + 21 * S), f"{size}px" if im else "AUCUN", fill=(120, 220, 120) if ok else (255, 90, 90), anchor="mm", font_size=9 * S)
sheet.save("tiles.png")
print("enseignes:", len(results), " nettes:", sum(1 for r in results if r[1][1] >= 96), " sans logo:", sum(1 for r in results if r[1][0] is None))

for (sid, name, color, domain), (im, size, src), disc in sorted(results, key=lambda r: r[1][1]):
    flag = "OK " if size >= 96 else "BAD"
    print(f"{flag} {size:>4} {sid:<16} {src[:90]}")
    for p, t, dv in disc:
        print(f"         play? {p} | {t[:50]} | {dv[:40]}")
