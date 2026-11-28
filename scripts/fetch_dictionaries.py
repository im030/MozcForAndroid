#!/usr/bin/env python3
"""Downloads the supplementary dictionaries that get baked into mozc.data.

Run by Gradle's fetchDictionaries task, which loads this script after the mozc submodule patches
are applied and before generateSystemDictionary. The app itself never touches the network.

Every source is pinned to a commit — never a branch — and files are checked for format
(right columns, entries present, enough rows), not by hash. A file that fails the check is
re-fetched, and Gradle's outputs-based up-to-date check skips the whole task when the installed
files are already in place.

Usage: fetch_dictionaries.py [file.txt ...]
With no arguments, fetches every source whose installed file is missing or fails the format
check. With arguments, fetches only the named sources.
"""

import bz2
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "third_party" / "dictionaries"
FILTER_SCRIPT = ROOT / "scripts" / "filter_katakana_english.py"

# 名詞,一般 in data/dictionary_oss/id.def — the id upstream gives its own katakana headwords.
NOUN = "名詞"

# ncaq/dic-nico-intersection-pixiv — titles appearing in both Niconico Pedia and Pixiv
# Encyclopedia, already in mozc user-dictionary TSV format (reading / word / part-of-speech).
NICO_PIXIV_COMMIT = "db8953c31d30528515d9d1ccf103cd337f4a108d"
NICO_PIXIV_URL = "https://raw.githubusercontent.com/ncaq/dic-nico-intersection-pixiv/{}/public/dic-nico-intersection-pixiv-google.txt".format(NICO_PIXIV_COMMIT)
NICO_FILE = "dic-nico-intersection-pixiv.txt"
NICO_MIN_ENTRIES = 10_000

# KEINOS/google-ime-user-dictionary-ja-en — katakana loanword to English spelling, derived from
# EDICT. Ships as a directory of files split at Google IME's old 10,000-row limit, so they are
# concatenated back into one; mozc has no such limit. Cloned at a commit rather than downloaded
# as an archive: GitHub generates those archives on demand and their bytes are not guaranteed
# stable over time, so a hash pinned to one is a build that breaks for no reason later.
JA_EN_COMMIT = "7d241dafcf6ee1f9eafefc0ae7a929c095860246"
JA_EN_REPO = "https://github.com/KEINOS/google-ime-user-dictionary-ja-en.git"
JA_EN_FILE = "katakana-english.txt"
JA_EN_MIN_ENTRIES = 10_000

# utuhiro78/mozcdic-ut-jawiki — readings and surfaces harvested from the Japanese Wikipedia.
# Distributed in mozc's *system* dictionary format (reading, lid, rid, cost, surface), and the
# lid, rid and cost columns are placeholders that merge-ut-dictionaries fills in later — in the
# raw file every one of them is 0000/0000/8000. There is no part of speech to carry over, so the
# entries are imported as 名詞.
JAWIKI_COMMIT = "88dddd9d7bc5657861aff3820fd2abc7d5186851"
JAWIKI_URL = "https://raw.githubusercontent.com/utuhiro78/mozcdic-ut-jawiki/{}/mozcdic-ut-jawiki.txt.bz2".format(JAWIKI_COMMIT)
JAWIKI_FILE = "jawiki.txt"
JAWIKI_MIN_ENTRIES = 500_000

MIN_ENTRIES = {
    NICO_FILE: NICO_MIN_ENTRIES,
    JA_EN_FILE: JA_EN_MIN_ENTRIES,
    JAWIKI_FILE: JAWIKI_MIN_ENTRIES,
}


def check_format(name, path):
    """Returns (ok, entries, detail) — whether the file holds usable {name} rows.

    A file that fails the check is re-fetched, so the message is deliberately as precise as
    the caller needs.
    """
    entries = 0
    try:
        with open(path, encoding="utf-8") as f:
            for raw in f:
                line = raw.rstrip("\n").strip()
                if not line or (name == NICO_FILE and line.startswith("#")):
                    continue
                parts = line.split("\t")
                if len(parts) < 3 or not parts[0].strip() or not parts[1].strip():
                    return False, entries, f"line {entries + 1} is not reading<TAB>surface[<TAB>pos]"
                entries += 1
    except OSError as e:
        return False, 0, str(e)
    minimum = MIN_ENTRIES[name]
    if entries < minimum:
        return False, entries, f"only {entries} entries (minimum {minimum})"
    return True, entries, None

USER_AGENT = "mozc-for-android-dictionary-fetcher/1.0"


def fail(message):
    print("error: " + message, file=sys.stderr)
    raise SystemExit(1)


def download(url, dest, desc):
    for attempt in range(1, 6):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as f:
                shutil.copyfileobj(resp, f)
            return
        except Exception as e:
            print(f"    {desc}: attempt {attempt} failed: {e}", file=sys.stderr)
            if attempt == 5:
                fail(f"could not download {desc}: {url}")
            time.sleep(2)


def run(cmd, desc, **kwargs):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, check=True, **kwargs)
    except subprocess.CalledProcessError as e:
        fail(f"{desc}: {e.cmd!r} failed with {e.returncode}: {e.stderr}")


def install(tmp, name, desc):
    dest = DEST / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    ok, count, reason = check_format(name, tmp)
    if not ok:
        fail(f"{desc}: {name} is not in the expected format ({reason})")
    shutil.copyfile(tmp, dest)
    os.chmod(dest, 0o644)
    print(f"    {desc}: {count} entries -> {dest}")


def fetch_nico():
    dest_name = NICO_FILE
    tmp = tempfile.mkdtemp(prefix="nico-")
    try:
        raw = Path(tmp) / "raw.txt"
        print("==> Fetching dic-nico-intersection-pixiv")
        download(NICO_PIXIV_URL, raw, "nico")
        install(raw, dest_name, "nico")
    except Exception:
        shutil.rmtree(tmp, ignore_errors=True)
        raise
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def fetch_ja_en():
    dest_name = JA_EN_FILE
    work = tempfile.mkdtemp(prefix="ja-en-")
    try:
        print("==> Fetching google-ime-user-dictionary-ja-en")
        repo = Path(work) / "ja-en"
        run(["git", "init", "-q", str(repo)], "git init")
        run(["git", "-C", str(repo), "remote", "add", "origin", JA_EN_REPO], "git remote")
        run(["git", "-C", str(repo), "fetch", "-q", "--depth", "1", "origin", JA_EN_COMMIT], "git fetch")
        run(["git", "-C", str(repo), "checkout", "-q", "FETCH_HEAD"], "git checkout")

        # Take only the katakana-English directory and only lines that parse as
        # reading <TAB> word <TAB> part-of-speech — that rejects the Word documents wholesale.
        matches = sorted(p for p in repo.rglob("*.txt") if "Google-ime-jp-カタカナ英語辞典" in str(p))
        if not matches:
            fail("no katakana-English .txt files found in the repository")
        # The repository also carries .docx files saved with a .txt suffix. The shell pipeline
        # filtered them out by accident — the regex rejects their non-text lines wholesale — so
        # decode tolerantly and let the same filter do the same job.
        raw_text = ""
        for p in matches:
            raw_text += p.read_bytes().decode("utf-8", errors="ignore")
        filtered = []
        for line in raw_text.splitlines():
            parts = line.split("\t")
            if len(parts) >= 3 and parts[0] and parts[1] and _is_katakana_reading(parts[0]):
                filtered.append("\t".join([parts[0], parts[1], parts[2]]))

        merged = Path(work) / "merged.txt"
        proc = subprocess.run(
            [sys.executable, str(FILTER_SCRIPT)],
            input="\n".join(filtered) + "\n",
            capture_output=True, text=True, check=True,
        )
        with open(merged, "w", encoding="utf-8") as f:
            f.write(proc.stdout)
        install(merged, dest_name, "ja-en")
    except Exception:
        shutil.rmtree(work, ignore_errors=True)
        raise
    finally:
        shutil.rmtree(work, ignore_errors=True)


def _is_katakana_reading(s):
    # The character class in the original awk was locale-sensitive (measured at 26,828 entries
    # under ja_JP.UTF-8 and 24,621 under C.UTF-8). Python's regex is locale-independent, which is
    # what we want for a reproducible build — but the accepted character set must stay the same.
    return re.fullmatch(r"[ぁ-ゖー]+", s) is not None


def fetch_jawiki():
    dest_name = JAWIKI_FILE
    work = tempfile.mkdtemp(prefix="jawiki-")
    try:
        print("==> Fetching mozcdic-ut-jawiki")
        bz2_path = Path(work) / "jawiki.txt.bz2"
        download(JAWIKI_URL, bz2_path, "jawiki")

        lines = set()
        with bz2.open(bz2_path, "rb") as f:
            for raw in f.read().decode("utf-8").splitlines():
                parts = raw.split("\t")
                if len(parts) >= 5 and parts[0] and parts[4]:
                    lines.add(f"{parts[0]}\t{parts[4]}\t{NOUN}")
        merged = Path(work) / "jawiki.txt"
        with open(merged, "w", encoding="utf-8") as f:
            f.write("\n".join(sorted(lines)) + "\n")
        install(merged, dest_name, "jawiki")
    except Exception:
        shutil.rmtree(work, ignore_errors=True)
        raise
    finally:
        shutil.rmtree(work, ignore_errors=True)


FETCHERS = {
    NICO_FILE: fetch_nico,
    JA_EN_FILE: fetch_ja_en,
    JAWIKI_FILE: fetch_jawiki,
}


def is_installed(name):
    dest = DEST / name
    if not dest.is_file():
        return False
    ok, _, reason = check_format(name, dest)
    if not ok:
        print(f"    {name} fails the format check ({reason}); re-fetching", file=sys.stderr)
    return ok


def main():
    names = sys.argv[1:] if len(sys.argv) > 1 else list(FETCHERS)
    failed = False
    for name in names:
        if name not in FETCHERS:
            fail(f"unknown dictionary: {name} (expected one of {', '.join(FETCHERS)})")
        if is_installed(name):
            print(f"    {name} already present and format-valid")
            continue
        try:
            FETCHERS[name]()
        except SystemExit:
            raise
        except Exception as e:
            print(f"error: {e}", file=sys.stderr)
            failed = True
    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
