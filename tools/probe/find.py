import re, io, json, html, unicodedata, urllib.request, urllib.parse
from PIL import Image
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
def get(u, mx=3_000_000):
    try: return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "fr-FR"}), timeout=15).read(mx)
    except Exception: return b""
def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in s if unicodedata.category(c) != "Mn")).strip()
for line in open("tools/probe/find.txt", encoding="utf-8"):
    sid, name = line.strip().split("|")
    key = norm(name).split()[0]
    print(f"\n== {sid} ({name})")
    j = get("https://itunes.apple.com/search?" + urllib.parse.urlencode({"term": name, "country": "fr", "entity": "software", "limit": 8}))
    try:
        for r in json.loads(j).get("results", []):
            if key in norm(r["trackName"]) or key in norm(r.get("sellerName", "")):
                print(f"  ios {r['trackId']} | {r['trackName'][:45]} | {r.get('sellerName','')[:35]}")
    except Exception as e: print("  ios err", e)
    for q in [name, name + " fidélité"]:
        h = get("https://play.google.com/store/search?c=apps&hl=fr&gl=FR&q=" + urllib.parse.quote(q)).decode("utf-8", "ignore")
        for p in list(dict.fromkeys(re.findall(r"details\?id=([\w.]+)", h)))[:8]:
            d = get(f"https://play.google.com/store/apps/details?id={p}&hl=fr&gl=FR").decode("utf-8", "ignore")
            t = re.search(r"<title[^>]*>([^<]*)", d); title = html.unescape(t.group(1)).split(" – ")[0] if t else "?"
            dev = re.search(r'/store/apps/(?:dev|developer)\?id=([^"&]+)', d)
            dev = urllib.parse.unquote_plus(dev.group(1)) if dev else "?"
            if key in norm(title) or key in norm(dev):
                print(f"  play {p} | {title[:45]} | {dev[:35]}")
