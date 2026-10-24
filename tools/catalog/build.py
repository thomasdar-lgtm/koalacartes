"""Construit app/src/main/assets/stores.json :
la liste maison (curated.json) + les enseignes présentes en France d'après le
Name Suggestion Index d'OpenStreetMap (licence BSD-3, données Wikidata CC0).

Usage : python3 tools/catalog/build.py <dossier du paquet npm name-suggestion-index>
"""
import json, re, sys, unicodedata, urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NSI = Path(sys.argv[1])

# Types de commerce où l'on trouve des cartes de fidélité
SHOP_EXCLUDE = {
    "funeral_directors", "gold_buyer", "pawnbroker", "storage_rental", "charity", "outpost", "ticket",
    "car", "money_lender", "vacant", "electrical", "trade", "medical_supply", "hearing_aids", "craft",
    "curtain", "kitchen", "bed", "e-cigarette", "mobile_phone", "telecommunication", "second_hand",
    "travel_agency", "art", "bag", "games", "tool_hire",
}
OTHER_ALLOW = {
    "amenity/fast_food", "amenity/restaurant", "amenity/cafe", "amenity/ice_cream", "amenity/fuel",
    "amenity/cinema", "amenity/pharmacy", "amenity/car_wash", "leisure/fitness_centre",
    "leisure/sports_centre", "leisure/bowling_alley", "leisure/amusement_arcade", "tourism/hotel",
}
FRANCE = {"fr", "fx"}


def norm(s):
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9&]+", " ", s).strip()


def slug(s):
    return re.sub(r"[^a-z0-9]+", "", norm(s)) or "x"


def relevant(cat):
    if cat.startswith("shop/"):
        return cat[5:] not in SHOP_EXCLUDE
    return cat in OTHER_ALLOW


def in_france(item):
    inc = [str(x).lower() for x in item.get("locationSet", {}).get("include", [])]
    exc = [str(x).lower() for x in item.get("locationSet", {}).get("exclude", [])]
    if any(x in FRANCE for x in exc):
        return False
    return any(x in FRANCE or x.startswith("fr-") for x in inc)


def host(url):
    h = urllib.parse.urlparse(url).hostname or ""
    return h[4:] if h.startswith("www.") else h


nsi = json.load(open(NSI / "dist/json/nsi.json", encoding="utf-8"))["nsi"]
wd = json.load(open(NSI / "dist/wikidata/wikidata.json", encoding="utf-8"))["wikidata"]
curated = json.load(open(ROOT / "tools/catalog/curated.json", encoding="utf-8"))

# Enseignes OSM qui sont en réalité une enseigne de la liste maison
ALIAS = {
    "match": "match", "ristorantedelarte": "delarte", "lafayette": "pharmalafayette", "myauchan": "auchan",
    "monop": "monoprix", "centrecommercialeleclerc": "leclerc", "flyingtigercopenhagen": "flyingtiger",
    "bm": "bm", "babou": "bm", "ugc": "ugc", "cgr": "cgr",
}

# Index des noms de la liste maison pour rattacher les données OSM aux enseignes existantes
by_name = {}
for e in curated:
    for n in [e["name"], *e["keys"]]:
        by_name.setdefault(slug(n), e)

found = {}  # QID -> enseigne OSM
for path, block in nsi.items():
    if not path.startswith("brands/") or not relevant(path[7:]):
        continue
    for it in block["items"]:
        tags = it.get("tags", {})
        qid = tags.get("brand:wikidata")
        name = tags.get("brand") or it.get("displayName")
        if not qid or not name or not in_france(it):
            continue
        f = found.setdefault(qid, {"name": name, "cats": set(), "match": set()})
        f["cats"].add(path[7:])
        f["match"].update(it.get("matchNames", []))

stats = {"rattachées": 0, "nouvelles": 0, "sous-enseignes": 0}
extra = []
for qid, f in found.items():
    w = wd.get(qid, {})
    logo = (w.get("logos") or {}).get("wikidata")
    site = next(iter(w.get("officialWebsites") or []), None) or (w.get("identities") or {}).get("website")
    e = by_name.get(slug(f["name"])) or next((c for c in curated if c["id"] == ALIAS.get(slug(f["name"]))), None)
    if e is not None:
        stats["rattachées"] += 1
        if logo and "logo" not in e:
            e["logo"] = logo
        continue
    extra.append({"qid": qid, "name": f["name"], "cats": sorted(f["cats"]), "match": sorted(f["match"]),
                  "domain": host(site) if site else "", "logo": logo})

# Sous-enseignes (« Carrefour City », « Auchan Drive »…) : même carte que l'enseigne mère
parents = {norm(e["name"]): e for e in curated}
parents.update({norm(k): e for e in curated for k in e["keys"] if len(norm(k)) >= 4})
kept = []
for x in sorted(extra, key=lambda x: len(x["name"])):
    nx = norm(x["name"])
    parent = next((p for k, p in parents.items() if nx.startswith(k + " ")), None)
    if parent is not None:
        stats["sous-enseignes"] += 1
        lst = parent["keys"] if "keys" in parent else parent["match"]
        if nx not in map(norm, lst):
            lst.append(nx)
        continue
    kept.append(x)
    parents[nx] = x

ids = {e["id"] for e in curated}
out = list(curated)
seen = {slug(e["name"]) for e in curated}
for x in sorted(kept, key=lambda x: norm(x["name"])):
    if slug(x["name"]) in seen:
        continue
    seen.add(slug(x["name"]))
    sid = slug(x["name"])
    while sid in ids:
        sid += "_"
    ids.add(sid)
    # Reconnaissance au scan : seulement les noms assez distinctifs
    keys = sorted({norm(n) for n in [x["name"], *x["match"]] if len(norm(n)) >= 5})
    e = {"id": sid, "name": x["name"], "domain": x["domain"], "keys": keys, "osm": True}
    if x["logo"]:
        e["logo"] = x["logo"]
    out.append(e)
    stats["nouvelles"] += 1

(ROOT / "app/src/main/assets").mkdir(parents=True, exist_ok=True)
json.dump(out, open(ROOT / "app/src/main/assets/stores.json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print(f"total {len(out)} | maison {len(curated)} | " + " | ".join(f"{k} {v}" for k, v in stats.items()))
print("avec logo Wikimédia :", sum(1 for e in out if e.get("logo")))
