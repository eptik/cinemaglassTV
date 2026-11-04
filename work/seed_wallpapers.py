#!/usr/bin/env python3
"""Seed the launcher's wallpaper library from Wallhaven's public API.

Wallhaven serves 4K originals over plain HTTPS (no bot wall), which is what the
TV's harvest-and-push workflow needs. Each title is searched, filtered to
16:9 >= 3840x2160, and the darkest clean candidate is kept at native size.
"""
import json, os, re, sys, time, urllib.parse, urllib.request
from PIL import Image, ImageStat
import io

OUT = sys.argv[1] if len(sys.argv) > 1 else "work/wall-db"
UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124 Safari/537.36"}

TITLES = [
    # --- TV ---------------------------------------------------------------
    ("Breaking Bad", "tv"), ("The Office", "tv"), ("Silicon Valley", "tv"),
    ("Better Call Saul", "tv"), ("Succession", "tv"), ("Severance", "tv"),
    ("Stranger Things", "tv"), ("The Last of Us", "tv"), ("Chernobyl", "tv"),
    ("Peaky Blinders", "tv"), ("Mad Men", "tv"), ("True Detective", "tv"),
    ("Game of Thrones", "tv"), ("The Bear", "tv"), ("Dark", "tv"),
    ("Fargo", "tv"), ("Barry", "tv"), ("Ted Lasso", "tv"), ("The Wire", "tv"),
    ("Twin Peaks", "tv"),
    # --- films ------------------------------------------------------------
    ("Pulp Fiction", "movie"), ("Se7en", "movie"), ("The Godfather", "movie"),
    ("Goodfellas", "movie"), ("Whiplash", "movie"), ("Arrival", "movie"),
    ("Sicario", "movie"), ("No Country for Old Men", "movie"), ("The Revenant", "movie"),
    ("Drive", "movie"), ("Heat", "movie"), ("Gladiator", "movie"),
    ("Blade Runner 2049", "movie"), ("Alien", "movie"), ("2001 A Space Odyssey", "movie"),
    ("Parasite", "movie"), ("Inglourious Basterds", "movie"), ("Django Unchained", "movie"),
    ("Batman Begins", "movie"), ("The Dark Knight", "movie"), ("The Dark Knight Rises", "movie"),
    ("Joker", "movie"), ("Tenet", "movie"), ("Dunkirk", "movie"),
    ("The Shawshank Redemption", "movie"), ("Forrest Gump", "movie"),
    ("The Lord of the Rings", "movie"), ("The Social Network", "movie"),
    ("Interstellar", "movie"), ("Dune", "movie"), ("Oppenheimer", "movie"),
    ("The Matrix", "movie"), ("Fight Club", "movie"), ("Apocalypse Now", "movie"),
    ("Taxi Driver", "movie"), ("The Big Lebowski", "movie"), ("Léon", "movie"),
]

QUERY_SUFFIX = {"tv": " tv series", "movie": " movie"}

# Wallhaven matches tags, so a bare title often misses; these are the tag-friendly forms.
ALIASES = {
    "The Office": ["the office dwight", "the office michael scott"],
    "Silicon Valley": ["silicon valley hbo", "silicon valley richard hendricks"],
    "Succession": ["succession waystar", "succession roy family"],
    "Severance": ["severance lumon", "severance mark scout"],
    "Mad Men": ["mad men draper", "don draper"],
    "True Detective": ["true detective rust cohle", "true detective matthew mcconaughey"],
    "Barry": ["barry hbo bill hader"],
    "Ted Lasso": ["ted lasso afc richmond"],
    "Twin Peaks": ["twin peaks cooper", "twin peaks red room"],
    "Se7en": ["seven brad pitt", "se7en kevin spacey"],
    "The Godfather": ["godfather marlon brando", "the godfather vito"],
    "Goodfellas": ["goodfellas deniro", "goodfellas joe pesci"],
    "Whiplash": ["whiplash jk simmons", "whiplash drums"],
    "Arrival": ["arrival amy adams", "arrival heptapod"],
    "Sicario": ["sicario benicio del toro", "sicario emily blunt"],
    "No Country for Old Men": ["no country anton chigurh"],
    "Parasite": ["parasite bong joon ho", "parasite 2019 ki-taek"],
    "Inglourious Basterds": ["inglourious basterds ald rane", "inglourious basterds brad pitt"],
    "Django Unchained": ["django unchained foxx", "django tarantino"],
    "Batman Begins": ["batman begins bale", "christian bale batman"],
    "The Dark Knight Rises": ["dark knight rises bale", "bane tom hardy"],
    "Tenet": ["tenet nolan", "tenet john david washington"],
}


def get(url, binary=False, tries=4):
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read() if binary else json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 429:                      # wallhaven throttles hard
                time.sleep(15 * (attempt + 1))
                continue
            raise
        except Exception as e:
            last = e
            time.sleep(3)
    raise last


def score(data):
    im = Image.open(io.BytesIO(data)).convert("L").resize((480, 270))
    px = list(im.getdata())
    return ImageStat.Stat(im).mean[0], sum(1 for p in px if p < 28) / len(px)


def slug(s):
    s = s.replace("'", "").replace("é", "e").replace(": ", " ").replace("&", "and")
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def search(term):
    api = ("https://wallhaven.cc/api/v1/search?q=" + urllib.parse.quote(term) +
           "&categories=111&purity=100&ratios=16x9&atleast=3840x2160"
           "&sorting=relevance&order=desc")
    return get(api).get("data", [])


def fetch(title, kind):
    res = []
    for term in [title + QUERY_SUFFIX[kind]] + ALIASES.get(title, []):
        try:
            res = search(term)
        except Exception as e:
            return None, f"api {e}"
        time.sleep(3)
        if res:
            break
    if not res:
        return None, "no results"
    best = None
    for w in res[:6]:
        try:
            raw = get(w["path"], binary=True)
        except Exception:
            continue
        if len(raw) < 350_000:          # over-compressed or flat
            continue
        try:
            mean, dark = score(raw)
        except Exception:
            continue
        if mean > 150:
            continue
        cand = (mean, len(raw), raw, w["resolution"])
        if best is None or cand[0] < best[0]:
            best = cand
        if mean < 60:
            break
    if best is None:
        return None, "no usable image"
    name = slug(title) + ".jpg"
    if os.path.exists(os.path.join(OUT, name)):
        return None, "already have"
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(best[2])
    return (name, best[3], best[1] // 1024, round(best[0], 1)), "ok"


def main():
    os.makedirs(OUT, exist_ok=True)
    ok = miss = 0
    for title, kind in TITLES:
        try:
            info, status = fetch(title, kind)
        except Exception as e:
            info, status = None, str(e)[:40]
        if info:
            ok += 1
            print(f"ok   {title:34} {info[1]:>10} {info[2]:>5}K lum={info[3]}")
        else:
            miss += 1
            print(f"MISS {title:34} {status}")
    print(f"\n{ok} added, {miss} missing -> {OUT}")


if __name__ == "__main__":
    main()
