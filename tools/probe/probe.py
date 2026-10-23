import json, re, sys, io, urllib.request, urllib.parse
from PIL import Image
UA = "Mozilla/5.0 (Linux; Android 15; SM-S931B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

def get(url, mx=3_000_000):
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "fr-FR,fr;q=0.9"}), timeout=12)
        return r.status, r.read(mx), r.geturl(), r.headers.get("content-type", "")
    except urllib.error.HTTPError as e:
        return e.code, b"", url, ""
    except Exception as e:
        return type(e).__name__, b"", url, ""

def attr(tag, name):
    m = re.search(r'\b%s\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s>]+))' % name, tag, re.I)
    return (m.group(1) or m.group(2) or m.group(3) or "").replace("&amp;", "&") if m else None

def dims(b):
    try:
        im = Image.open(io.BytesIO(b)); return f"{im.format} {im.size[0]}x{im.size[1]}"
    except Exception:
        return "pas une image"

SUMMARY = []
for domain in sys.argv[1:]:
    print(f"\n===== {domain}")
    hosts = [domain] if domain.startswith("www.") else [domain, "www." + domain]
    cands = []
    for h in hosts:
        st, body, final, ct = get(f"https://{h}/", 800_000)
        print(f"page https://{h}/ -> {st} final={final} {len(body)}o")
        if st != 200 or not body: continue
        html = body.decode("utf-8", "ignore")
        for tag in re.findall(r"<link\b[^>]*>", html, re.I):
            rel = (attr(tag, "rel") or "").lower()
            if "icon" in rel or "manifest" in rel:
                href = attr(tag, "href"); print(f"  link rel={rel} sizes={attr(tag,'sizes')} href={href}")
                if not href: continue
                u = urllib.parse.urljoin(final, href)
                if "manifest" in rel.split():
                    s2, mb, mf, _ = get(u, 300_000)
                    print(f"  manifest {u} -> {s2}")
                    try:
                        for ic in json.loads(mb).get("icons", []):
                            cands.append(urllib.parse.urljoin(mf, ic.get("src", ""))); print(f"    icon {ic.get('sizes')} {ic.get('src')}")
                    except Exception as e: print("    manifest illisible", e)
                else:
                    cands.append(u)
        break
    for h in hosts:
        cands += [f"https://{h}/apple-touch-icon.png", f"https://{h}/apple-touch-icon-precomposed.png"]
        cands.append(f"https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://{h}&size=256")
    cands += [f"https://www.google.com/s2/favicons?domain={domain}&sz=256", f"https://icons.duckduckgo.com/ip3/{domain}.ico"]
    seen = set(); best = [0, ""]
    for u in cands:
        if u in seen: continue
        seen.add(u)
        st, b, final, ct = get(u)
        d = dims(b) if b else '-'
        print(f"  [{st}] {d} {ct} {u}")
        m = re.search(r"(\d+)x(\d+)", d)
        if m:
            sz = min(int(m.group(1)), int(m.group(2)))
            if sz > best[0]: best[:] = [sz, u]
    SUMMARY.append(f"{best[0]:>4}px  {domain}  {best[1]}")

print("\n===== RÉSUMÉ (meilleure taille trouvée)")
print("\n".join(sorted(SUMMARY)))
