#!/usr/bin/env python3
"""
Builds the offline content bundled into the Android app.

Sources
  * Official theory-exam question pool of the Israeli Ministry of Transport,
    published on data.gov.il (same data as gov.il "theoryexam*_data").
  * Question pictures from gov.il (the per-language mot.gov.il host is offline,
    pictures are language-neutral so the gov.il copies are used for every language).
  * Official traffic-sign table (numbers / categories / Hebrew wording) as listed on
    Hebrew Wikipedia, sign artwork from Wikimedia Commons (see sources/*.json).

Output (written into app/src/main/assets):
  content/signs.json                  language-neutral sign catalog
  content/<lang>/questions.json       question pool for a language
  content/<lang>/signs.json           sign meanings for a language
  img/q/<image>.webp                  question pictures
  img/s/<file>.webp                   sign pictures


Sign image attribution (author / licence / source page per Commons file) is fetched once from the
Commons API into sources/commons_credits.json and written into content/signs.json ("credits").

English is the fallback language: it must be complete, other languages may miss entries (the app
then shows the English text). Adding a language: create i18n/<lang>/config.json +
i18n/<lang>/signs.json, then run
    python3 -I tools/pipeline/build_content.py en ru <lang>

Requires: Python 3.10+, Pillow (with WebP support).
"""
from __future__ import annotations

import html
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
CACHE = ROOT / ".cache"
UA = {"User-Agent": "drive-il-content-pipeline/1.0"}

DATASTORE = "https://data.gov.il/api/3/action/datastore_search?resource_id={rid}&limit=5000"
GOV_IMG = "https://www.gov.il/BlobFolder/generalpage/tq_pic_{folder}/he/TQ_PIC_{name}.jpg"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

LICENSE_MAP = {"В": "B", "B": "B", "A": "A", "1": "1", "C1": "C1", "C": "C", "D": "D"}
SIGN_CATEGORY_BY_DIGIT = {
    "1": "WARNING", "2": "MANDATORY", "3": "PRIORITY", "4": "PROHIBITORY",
    "5": "PUBLIC_TRANSPORT", "6": "INFORMATION", "7": "TRAFFIC_LIGHTS",
    "8": "ROAD_MARKINGS", "9": "ROADWORKS",
}
SIGN_CATEGORY_ORDER = list(SIGN_CATEGORY_BY_DIGIT.values())

QUESTION_IMG_QUALITY = 80
SIGN_IMG_WIDTH = 330  # one of the Wikimedia standard thumbnail steps


def fetch(url: str, retries: int = 6) -> bytes:
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={**UA, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if attempt == retries - 1 or e.code not in (429, 500, 502, 503, 504):
                raise
            time.sleep(int(e.headers.get("Retry-After") or 0) or 5 * 2 ** attempt)
        except OSError:
            if attempt == retries - 1:
                raise
            time.sleep(5 * 2 ** attempt)
    raise AssertionError


def commons_original_urls(names: list[str]) -> dict[str, str]:
    """Resolves Commons file names to standard-size thumbnail URLs (Wikimedia asks scripts
    to use thumbnails in the standard size steps rather than original files)."""
    urls = {}
    for i in range(0, len(names), 50):
        query = urllib.parse.urlencode({
            "action": "query", "prop": "imageinfo", "iiprop": "url", "format": "json",
            "iiurlwidth": str(SIGN_IMG_WIDTH),
            "titles": "|".join("File:" + n for n in names[i:i + 50])})
        data = json.loads(fetch(f"{COMMONS_API}?{query}"))
        normalized = {n["to"]: n["from"] for n in data["query"].get("normalized", [])}
        for page in data["query"]["pages"].values():
            title = normalized.get(page["title"], page["title"])[5:]
            info = page["imageinfo"][0]
            urls[title.replace(" ", "_")] = info.get("thumburl") or info["url"]
    return urls


CREDIT_FIELDS = "Artist|Attribution|LicenseShortName|LicenseUrl"


def plain_text(fragment: str) -> str:
    text = re.sub(r"</li>\s*<li>", "; ", fragment)
    text = re.sub(r"<[^>]+>", "", text)
    return re.sub(r"\s+", " ", html.unescape(text)).strip(" ;")


def single_link(fragment: str) -> str | None:
    """The link of an author field that is exactly one link (a user page), otherwise None."""
    m = re.fullmatch(r'\s*<a [^>]*href="([^"]+)"[^>]*>[^<]*</a>\s*', fragment)
    if not m or "redlink=1" in m.group(1):
        return None
    url = html.unescape(m.group(1))
    return "https:" + url if url.startswith("//") else url


def commons_credits(names: list[str]) -> dict[str, dict]:
    """TASL attribution (title, author, source, licence) for Commons files, cached in sources/."""
    path = ROOT / "sources" / "commons_credits.json"
    credits = json.loads(path.read_text("utf-8")) if path.exists() else {}
    missing = [n for n in names if n not in credits]
    for i in range(0, len(missing), 50):
        query = urllib.parse.urlencode({
            "action": "query", "prop": "imageinfo", "iiprop": "extmetadata|url", "format": "json",
            "iiextmetadatafilter": CREDIT_FIELDS, "titles": "|".join("File:" + n for n in missing[i:i + 50])})
        data = json.loads(fetch(f"{COMMONS_API}?{query}"))
        normalized = {n["to"]: n["from"] for n in data["query"].get("normalized", [])}
        for page in data["query"]["pages"].values():
            name = normalized.get(page["title"], page["title"])[5:].replace(" ", "_")
            info = page["imageinfo"][0]
            meta = {k: v.get("value", "") for k, v in info.get("extmetadata", {}).items()}
            # An explicit "Attribution" is how the author asked to be credited.
            author_html = meta.get("Attribution") or meta.get("Artist") or ""
            credits[name] = {
                "title": name.rsplit(".", 1)[0].replace("_", " "),
                "author": plain_text(author_html) or None,
                "authorUrl": single_link(author_html),
                "license": meta.get("LicenseShortName") or "Unknown",
                "licenseUrl": meta.get("LicenseUrl") or None,
                "source": info["descriptionurl"],
            }
        time.sleep(1)
    path.write_text(json.dumps(credits, ensure_ascii=False, indent=1, sort_keys=True), "utf-8")
    return credits


def cached(name: str, url: str) -> bytes:
    path = CACHE / name
    if path.exists() and path.stat().st_size > 0:
        return path.read_bytes()
    data = fetch(url)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


# ---------------------------------------------------------------- questions

def clean(fragment: str) -> str:
    text = re.sub(r"<br\s*/?>", "\n", fragment)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text).replace("\xa0", " ")
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def image_key(src: str) -> str | None:
    base = src.rsplit("/", 1)[-1]
    m = re.match(r"(?:TQ_PIC_)?(\d+)\.(?:jpe?g|png|gif)$", base, re.I)
    return m.group(1) if m else None


def parse_question(rec: dict, topics: dict[str, str]) -> dict:
    m = re.match(r"\s*(\d+)\.\s*(.*)", rec["title2"], re.S)
    if not m:
        raise ValueError(f"Unexpected title: {rec['title2']!r}")
    qid, text = int(m.group(1)), clean(m.group(2))
    body = rec["description4"]

    answers, correct = [], -1
    for i, li in enumerate(re.findall(r"<li>(.*?)</li>", body, re.S)):
        if 'id="correctAnswer' in li:
            correct = i
        answers.append(clean(li))
    if len(answers) != 4 or correct < 0:
        raise ValueError(f"Question {qid}: {len(answers)} answers, correct={correct}")

    licenses: list[str] = []
    for span in re.findall(r"<span[^>]*>([^<]*«[^<]*)</span>", body):
        if span.strip().startswith("|"):
            licenses = [LICENSE_MAP[t] for t in re.findall(r"«([^»]+)»", span) if t in LICENSE_MAP]
    imgs = [image_key(s) for s in re.findall(r'<img[^>]+src="([^"]+)"', body)]
    imgs = [i for i in imgs if i]

    topic = topics.get(rec["category"].strip())
    if topic is None:
        raise ValueError(f"Question {qid}: unknown category {rec['category']!r}")
    q = {"id": qid, "topic": topic, "text": text, "answers": answers, "correct": correct,
         "licenses": sorted(set(licenses), key=list(LICENSE_MAP.values()).index)}
    if imgs:
        q["image"] = imgs[0]
    return q


def build_questions(lang: str, cfg: dict) -> list[dict]:
    raw = json.loads(cached(f"pool_{lang}.json", DATASTORE.format(rid=cfg["datastoreResourceId"])))
    records = raw["result"]["records"]
    questions = [parse_question(r, cfg["topics"]) for r in records]
    questions.sort(key=lambda q: q["id"])
    ids = [q["id"] for q in questions]
    assert len(ids) == len(set(ids)), "duplicate question ids"
    print(f"[{lang}] {len(questions)} questions, {sum('image' in q for q in questions)} with pictures")
    return questions


def write_question_images(questions: list[dict]) -> None:
    out = ASSETS / "img" / "q"
    out.mkdir(parents=True, exist_ok=True)
    for name in sorted({q["image"] for q in questions if "image" in q}):
        target = out / f"{name}.webp"
        if target.exists():
            continue
        data = None
        for folder in ("01", "02", "03"):
            try:
                candidate = cached(f"q/{name}.jpg", GOV_IMG.format(folder=folder, name=name))
                Image.open(io.BytesIO(candidate)).verify()
                data = candidate
                break
            except Exception:  # noqa: BLE001 - try next folder
                (CACHE / f"q/{name}.jpg").unlink(missing_ok=True)
        if data is None:
            raise RuntimeError(f"Picture {name} not found")
        Image.open(io.BytesIO(data)).convert("RGB").save(target, "WEBP", quality=QUESTION_IMG_QUALITY, method=6)


# ---------------------------------------------------------------- signs

def sign_sort_key(number: str) -> tuple:
    m = re.match(r"(\d+)(.*)", number)
    return int(m.group(1)), m.group(2)


def sign_image(commons_name: str, urls: dict[str, str]) -> str:
    stem = re.sub(r"[^A-Za-z0-9_-]+", "_", commons_name.rsplit(".", 1)[0]).strip("_").lower()
    target = ASSETS / "img" / "s" / f"{stem}.webp"
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        src = cached(f"s/{commons_name}", urls[commons_name])
        img = Image.open(io.BytesIO(src))
        img.seek(0)
        if img.width > SIGN_IMG_WIDTH:
            img = img.resize((SIGN_IMG_WIDTH, round(img.height * SIGN_IMG_WIDTH / img.width)), Image.LANCZOS)
        img.convert("RGBA").save(target, "WEBP", quality=85, method=6)
        time.sleep(1)  # be polite to Wikimedia
    return target.name


FALLBACK_LANG = "en"  # must be complete; the app shows it for anything missing in another language
MARKINGS_REFERENCE_LANG = "ru"  # pool used to find "what does this marking mean" questions


def build_sign_catalog(reference_questions: list[dict]) -> tuple[list[dict], dict[str, dict]]:
    master = json.loads((ROOT / "sources" / "signs_master_he.json").read_text("utf-8"))
    files = sorted({f for e in master for f in e["files"]})
    file_credits = commons_credits(files)
    credits = {}
    urls = {} if all((CACHE / "s" / f).exists() for f in files) else commons_original_urls(files)
    catalog = []
    for entry in sorted(master, key=lambda e: sign_sort_key(e["num"])):
        catalog.append({
            "key": entry["num"],
            "number": entry["num"],
            "category": SIGN_CATEGORY_BY_DIGIT[entry["num"][0]],
            "images": [sign_image(f, urls) for f in entry["files"]],
        })
        for f in entry["files"]:
            credits[sign_image(f, urls)] = file_credits[f]
    # Road markings are barely covered by the Commons artwork; the official pool contains
    # "what does this marking mean" picture questions, which are added as catalog entries.
    for q in reference_questions:
        if (q["topic"] == "SIGNS" and "image" in q and re.search(r"разметк", q["text"], re.I)
                and re.match(r"Что (обозначает|означает)", q["text"])):
            catalog.append({"key": f"q{q['id']}", "number": None, "category": "ROAD_MARKINGS",
                            "images": [], "questionImage": q["image"], "questionId": q["id"]})
    return catalog, credits


# ---------------------------------------------------------------- main

def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), "utf-8")


def main(langs: list[str]) -> None:
    questions_by_lang = {}
    for lang in langs:
        cfg = json.loads((ROOT / "i18n" / lang / "config.json").read_text("utf-8"))
        questions = build_questions(lang, cfg)
        questions_by_lang[lang] = questions
        write_question_images(questions)
        write_json(ASSETS / "content" / lang / "questions.json",
                   {"language": lang, "source": cfg["source"], "questions": questions})

    reference = questions_by_lang.get(MARKINGS_REFERENCE_LANG) or build_questions(
        MARKINGS_REFERENCE_LANG,
        json.loads((ROOT / "i18n" / MARKINGS_REFERENCE_LANG / "config.json").read_text("utf-8")))
    catalog, credits = build_sign_catalog(reference)
    write_json(ASSETS / "content" / "signs.json",
               {"categories": SIGN_CATEGORY_ORDER, "signs": catalog, "credits": credits})

    for lang in langs:
        tr = json.loads((ROOT / "i18n" / lang / "signs.json").read_text("utf-8"))
        by_id = {q["id"]: q for q in questions_by_lang[lang]}
        meanings = {}
        for s in catalog:
            if "questionId" in s and s["questionId"] in by_id:
                q = by_id[s["questionId"]]
                meanings[s["key"]] = q["answers"][q["correct"]]
            elif s["key"] in tr["signs"]:
                meanings[s["key"]] = tr["signs"][s["key"]]
            elif lang == FALLBACK_LANG:
                raise RuntimeError(f"[{lang}] missing text for sign {s['key']}")
            else:
                print(f"[{lang}] no text for sign {s['key']}, the app falls back to {FALLBACK_LANG}")
        write_json(ASSETS / "content" / lang / "signs.json",
                   {"categories": tr["categories"], "meanings": meanings})
    print(f"signs: {len(catalog)} entries")


if __name__ == "__main__":
    main(sys.argv[1:] or [FALLBACK_LANG, "ru"])
