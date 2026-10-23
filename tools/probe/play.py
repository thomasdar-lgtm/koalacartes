import re, sys, io, urllib.request, urllib.parse, html
from PIL import Image
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def get(u):
    try:
        r = urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "fr-FR"}), timeout=15)
        return r.read(3_000_000)
    except Exception as e:
        return b""
def icon(pkg):
    h = get(f"https://play.google.com/store/apps/details?id={pkg}&hl=fr&gl=FR").decode("utf-8", "ignore")
    t = re.search(r'<title[^>]*>([^<]*)', h); title = html.unescape(t.group(1)) if t else "?"
    m = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', h) or re.search(r'itemprop="image"[^>]*src="([^"]+)"', h)
    if not m: return title, None, "-"
    u = re.sub(r'=[a-z0-9-]+$', '', html.unescape(m.group(1))) + "=s256"
    b = get(u)
    try: d = "%dx%d" % Image.open(io.BytesIO(b)).size
    except Exception: d = "?"
    dev = re.search(r'/store/apps/(?:dev|developer)\?id=([^"&]+)', h)
    return title + " | dev=" + (urllib.parse.unquote_plus(dev.group(1)) if dev else "?"), u, d
for line in open(sys.argv[1]):
    sid, q = line.strip().split("|", 1)
    if sid == "pkg":
        for p in q.split(","):
            title, u, d = icon(p)
            print(f"  [direct] {p} | {title[:80]} | {d}")
        continue
    h = get("https://play.google.com/store/search?c=apps&hl=fr&gl=FR&q=" + urllib.parse.quote(q)).decode("utf-8", "ignore")
    pkgs = []
    for p in re.findall(r'details\?id=([\w.]+)', h):
        if p not in pkgs: pkgs.append(p)
    print(f"\n== {sid} ({q})")
    for p in pkgs[:3]:
        title, u, d = icon(p)
        print(f"  {p} | {title[:60]} | {d}")
