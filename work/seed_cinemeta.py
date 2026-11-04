#!/usr/bin/env python3
"""Build the wallpaper library from Cinemeta — the artwork Stremio itself shows.

Cinemeta is Stremio's catalogue addon; its `background` field points at
images.metahub.space, which serves the official landscape backdrop for a title
(1920x1080, no fan art, no portrait compositions, no burned-in titles).

Usage: seed_cinemeta.py <out-dir>
"""
import json, os, re, sys, time, unicodedata, urllib.parse, urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "work/cinemeta"
UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124 Safari/537.36"}
BASE = "https://v3-cinemeta.strem.io"

MOVIES = ["Interstellar", "The Dark Knight", "The Dark Knight Rises", "Batman Begins", "Inception",
          "Tenet", "Dunkirk", "Oppenheimer", "Dune", "Dune: Part Two", "The Odyssey", "Project Hail Mary",
          "Spider-Man: Brand New Day", "Blade Runner 2049", "Blade Runner", "Alien", "Aliens",
          "2001: A Space Odyssey", "Pulp Fiction", "Se7en", "Fight Club", "The Godfather", "Goodfellas",
          "The Matrix", "Heat", "Drive", "Gladiator", "The Revenant", "No Country for Old Men",
          "Sicario", "Arrival", "Whiplash", "Parasite", "Inglourious Basterds", "Django Unchained",
          "Joker", "The Shawshank Redemption", "Forrest Gump", "The Lord of the Rings: The Fellowship of the Ring",
          "The Social Network", "Mad Max: Fury Road", "Apocalypse Now", "Taxi Driver",
          "Léon: The Professional", "The Big Lebowski", "Gravity", "The Grand Budapest Hotel"]

SERIES = ["Breaking Bad", "Better Call Saul", "The Office", "Silicon Valley", "Succession", "Severance",
          "Stranger Things", "The Last of Us", "Chernobyl", "Peaky Blinders", "Mad Men", "True Detective",
          "Game of Thrones", "The Bear", "Dark", "Fargo", "Barry", "Ted Lasso", "The Wire", "Twin Peaks",
          "House of the Dragon", "Andor", "The Mandalorian", "Shogun", "The Boys", "The Sopranos",
          "Rick and Morty", "Arcane"]


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", norm(s)).strip("-")


def get(url, binary=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.read() if binary else json.loads(r.read().decode())


def find(title, kind):
    q = urllib.parse.quote(title)
    try:
        data = get(f"{BASE}/catalog/{kind}/top/search={q}.json")
    except Exception:
        return None
    want = norm(title)
    metas = data.get("metas", [])
    # exact name first, then a name that starts with the title
    for m in metas:
        if norm(m.get("name")) == want:
            return m
    for m in metas:
        if norm(m.get("name")).startswith(want):
            return m
    return metas[0] if len(metas) == 1 else None


def run(titles, kind):
    ok = 0
    for t in titles:
        m = find(t, kind)
        if not m or not m.get("background"):
            print(f"MISS {t}")
            continue
        url = m["background"].replace("/medium/", "/large/")
        name = slug(m.get("name") or t) + ".jpg"
        path = os.path.join(OUT, name)
        if os.path.exists(path):
            print(f"have {t}")
            ok += 1
            continue
        try:
            raw = get(url, binary=True)
        except Exception as e:
            print(f"ERR  {t} {e}")
            continue
        if len(raw) < 60_000:
            print(f"tiny {t} ({len(raw)} B)")
            continue
        with open(path, "wb") as f:
            f.write(raw)
        ok += 1
        print(f"ok   {m.get('name')} ({m.get('releaseInfo','')}) {len(raw)//1024}K")
        time.sleep(0.4)
    return ok


def main():
    os.makedirs(OUT, exist_ok=True)
    a = run(MOVIES, "movie")
    b = run(SERIES, "series")
    print(f"\n{a + b} wallpapers -> {OUT}")


if __name__ == "__main__":
    main()
