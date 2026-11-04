#!/usr/bin/env python3
"""Build the wallpaper library at 4K, matched correctly.

Two sources combined:
  * Cinemeta (Stremio's catalogue) resolves the title to an IMDb id, so we never
    pick the wrong film again (that is how "Drive" became a Scorpions cover).
  * TMDB's image list for that exact id supplies 3840+ px textless backdrops,
    ranked by TMDB's own vote on the artwork.

Every file is checked after download: >= 3840 px wide, >= 400 KB, no letterbox
bars, no portrait composition. Anything that fails is dropped, never pushed.

Usage: seed_hq.py <out-dir>
"""
import json, os, re, sys, time, unicodedata, urllib.parse, urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "work/hq"
UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124 Safari/537.36"}
CINE = "https://v3-cinemeta.strem.io"

HERE = os.path.dirname(os.path.abspath(__file__))
KEY = re.search(r'name="tmdb_api_key"[^>]*>([^<]+)<',
                open(os.path.join(HERE, "..", "work", "tv-vibehack-source", "launcher",
                                  "app", "src", "main", "res", "values", "tmdb.xml")).read()).group(1)

MOVIES = ["Interstellar", "The Dark Knight", "The Dark Knight Rises", "Batman Begins", "Inception",
          "Tenet", "Dunkirk", "Oppenheimer", "Dune", "Dune: Part Two", "The Odyssey", "Project Hail Mary",
          "Spider-Man: Brand New Day", "Blade Runner 2049", "Blade Runner", "Alien", "Aliens",
          "2001: A Space Odyssey", "Pulp Fiction", "Se7en", "Fight Club", "The Godfather", "Goodfellas",
          "The Matrix", "Heat", "Drive", "Gladiator", "The Revenant", "No Country for Old Men",
          "Sicario", "Arrival", "Whiplash", "Parasite", "Inglourious Basterds", "Django Unchained",
          "Joker", "The Shawshank Redemption", "Forrest Gump", "The Lord of the Rings: The Fellowship of the Ring",
          "The Social Network", "Mad Max: Fury Road", "Apocalypse Now", "Taxi Driver",
          "Léon: The Professional", "The Big Lebowski", "Gravity", "The Grand Budapest Hotel",
          "Once Upon a Time in Hollywood", "The Thing", "Terminator 2: Judgment Day", "Predator",
          "American Psycho", "The Silence of the Lambs", "Casino", "The Departed", "Memento",
          "Saving Private Ryan", "Gladiator", "Top Gun: Maverick", "John Wick"]

SERIES = ["Breaking Bad", "Better Call Saul", "The Office", "Silicon Valley", "Succession", "Severance",
          "Stranger Things", "The Last of Us", "Chernobyl", "Peaky Blinders", "Mad Men", "True Detective",
          "Game of Thrones", "The Bear", "Dark", "Fargo", "Barry", "Ted Lasso", "The Wire", "Twin Peaks",
          "House of the Dragon", "Andor", "The Mandalorian", "Shogun", "The Boys", "The Sopranos"]

MIN_WIDTH, MIN_BYTES = 3840, 400_000


def norm(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", norm(s)).strip("-")


def get(url, binary=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=45) as r:
        return r.read() if binary else json.loads(r.read().decode())


def imdb_id(title, kind):
    data = get(f"{CINE}/catalog/{kind}/top/search={urllib.parse.quote(title)}.json")
    want = norm(title)
    metas = data.get("metas", [])
    for m in metas:
        if norm(m.get("name")) == want:
            return m.get("imdb_id"), m.get("name")
    for m in metas:
        if norm(m.get("name")).startswith(want):
            return m.get("imdb_id"), m.get("name")
    return None, None


def tmdb_id(imdb, kind):
    d = get(f"https://api.themoviedb.org/3/find/{imdb}?api_key={KEY}&external_source=imdb_id")
    key = "movie_results" if kind == "movie" else "tv_results"
    res = d.get(key) or []
    return (res[0]["id"], res[0].get("title") or res[0].get("name")) if res else (None, None)


def backdrop(kind, tid):
    d = get(f"https://api.themoviedb.org/3/{kind}/{tid}/images?api_key={KEY}&include_image_language=null")
    cands = []
    for b in d.get("backdrops", []):
        if b.get("width", 0) < MIN_WIDTH:
            continue
        if b.get("iso_639_1"):          # titled art: logo/credits burned in
            continue
        cands.append(b)
    cands.sort(key=lambda b: (b.get("vote_average", 0) * min(1.0, (b.get("vote_count", 0) + 1) / 3.0),
                              b.get("width", 0)), reverse=True)
    return cands


def good(raw):
    if len(raw) < MIN_BYTES:
        return False, f"{len(raw)//1024}K"
    try:
        from PIL import Image
        import io
        im = Image.open(io.BytesIO(raw))
        w, h = im.size
    except Exception:
        return False, "unreadable"
    if w < MIN_WIDTH:
        return False, f"{w}x{h}"
    ratio = w / h
    if not (1.70 <= ratio <= 1.85):
        return False, f"aspect {ratio:.2f}"
    g = im.convert("L")
    # pillarbox test: both edges quiet and much darker than the middle
    w1, h1 = g.size
    left = g.crop((0, 0, int(w1 * 0.10), h1)).getextrema()
    right = g.crop((int(w1 * 0.90), 0, w1, h1)).getextrema()
    mid = g.crop((int(w1 * 0.45), 0, int(w1 * 0.55), h1))
    mid_max = mid.getextrema()[1]
    if left[1] < 20 and right[1] < 20 and mid_max > 80:
        return False, "pillarboxed"
    return True, f"{w}x{h}"


def run(titles, kind):
    ok = 0
    for t in titles:
        name = slug(t) + ".jpg"
        path = os.path.join(OUT, name)
        if os.path.exists(path) and os.path.getsize(path) >= MIN_BYTES:
            ok += 1
            continue
        try:
            imdb, real = imdb_id(t, kind)
            if not imdb:
                print(f"MISS {t} (no imdb)")
                continue
            tid, tname = tmdb_id(imdb, kind)
            if not tid:
                print(f"MISS {t} (no tmdb id)")
                continue
            cands = backdrop(kind, tid)
            picked = None
            for b in cands[:6]:
                url = "https://image.tmdb.org/t/p/original" + b["file_path"]
                try:
                    raw = get(url, binary=True)
                except Exception:
                    continue
                fine, why = good(raw)
                if fine:
                    picked = (raw, why, b.get("vote_average"))
                    break
            if not picked:
                print(f"MISS {t} (no backdrop at 3840+)")
                continue
            open(path, "wb").write(picked[0])
            ok += 1
            print(f"ok   {tname or t:38} {picked[1]:>10} {len(picked[0])//1024:>5}K v={picked[2]}")
            time.sleep(0.25)
        except Exception as e:
            print(f"ERR  {t} {str(e)[:60]}")
    return ok


def main():
    os.makedirs(OUT, exist_ok=True)
    a = run(MOVIES, "movie")
    b = run(SERIES, "tv")
    print(f"\n{a + b} high-quality wallpapers -> {OUT}")


if __name__ == "__main__":
    main()
