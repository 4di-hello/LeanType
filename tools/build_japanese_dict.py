#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Builds an optimized, sorted Japanese Kana-to-Kanji lookup dictionary
(dict_japanese.dat) for LeanType CJK Plugin using SKK-JISYO.L as the source.
"""

import os
import subprocess
import sys

CACHE_FILE = os.path.join(os.path.dirname(__file__), ".skk_cache")
OUTPUT_FILE = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dict_japanese.dat"
)

VOWELS = {
    'k': [('か', 'か'), ('き', 'き'), ('く', 'く'), ('け', 'け'), ('こ', 'こ'), ('いた', 'いた'), ('いて', 'いて')],
    'g': [('が', 'が'), ('ぎ', 'ぎ'), ('ぐ', 'ぐ'), ('げ', 'げ'), ('ご', 'ご'), ('いだ', 'いだ'), ('いで', 'いで')],
    's': [('さ', 'さ'), ('し', 'し'), ('す', 'す'), ('せ', 'せ'), ('そ', 'そ'), ('した', 'した'), ('して', 'して')],
    't': [('た', 'た'), ('ち', 'ち'), ('つ', 'つ'), ('て', 'て'), ('と', 'と'), ('った', 'った'), ('って', 'って')],
    'n': [('な', 'な'), ('に', 'に'), ('ぬ', 'ぬ'), ('ね', 'ね'), ('の', 'の'), ('んだ', 'んだ'), ('んで', 'んで')],
    'b': [('ば', 'ば'), ('び', 'び'), ('ぶ', 'ぶ'), ('べ', 'べ'), ('ぼ', 'ぼ'), ('んだ', 'んだ'), ('んで', 'んで')],
    'm': [('ま', 'ま'), ('み', 'み'), ('む', 'む'), ('め', 'め'), ('も', 'も'), ('んだ', 'んだ'), ('んで', 'んで')],
    'r': [('ら', 'ら'), ('り', 'り'), ('る', 'る'), ('れ', 'れ'), ('ろ', 'ろ'), ('った', 'った'), ('って', 'って'), ('た', 'た'), ('て', 'て')],
    'w': [('わ', 'わ'), ('い', 'い'), ('う', 'う'), ('え', 'え'), ('お', 'お'), ('った', 'った'), ('って', 'って')],
    'i': [('い', 'い'), ('く', 'く'), ('かった', 'かった'), ('くて', 'くて')],
}

# Essential priority overrides to ensure canonical forms appear first
CANONICAL_OVERRIDES = {
    'いく': '行く',
    'くる': '来る',
    'みる': '見る',
    'きく': '聞く',
    'いう': '言う',
    'かく': '書く',
    'よむ': '読む',
    'おもう': '思う',
    'はなす': '話す',
    'たべる': '食べる',
    'のむ': '飲む',
    'ある': '有る',
    'いる': '居る',
    'わかる': '分かる',
    'しる': '知る',
    'もつ': '持つ',
    'まつ': '待つ',
    'たつ': '立つ',
    'すわる': '座る',
    'ねる': '寝る',
    'おきる': '起きる',
    'あう': '会う',
    'わたし': '私',
    'いぬ': '犬',
    'ねこ': '猫',
    'あめ': '雨',
    'ゆき': '雪',
    'くるま': '車',
    'でんしゃ': '電車',
    'ひと': '人',
    'おとこ': '男',
    'おんな': '女',
    'こども': '子供',
    'せんせい': '先生',
    'がっこう': '学校',
    'ともだち': '友達',
}

ALIASES = {
    'こんにちわ': ['こんにちは', '今日は'],
    'こんばんわ': ['こんばんは', '今晩は'],
    'ありがとう': ['有難う', '有り難う'],
    'おはよう': ['お早う', 'おはよう'],
    'さようなら': ['左様なら', 'さようなら'],
    'いただきます': ['頂きます', 'いただきます'],
    'ごちそうさま': ['御馳走様', 'ごちそうさま'],
}


def fetch_source_dictionary():
    if os.path.exists(CACHE_FILE) and os.path.getsize(CACHE_FILE) > 1000000:
        print(f"Reading from cache: {CACHE_FILE}")
        with open(CACHE_FILE, "r", encoding="utf-8") as f:
            return f.read()

    print("Fetching SKK-JISYO.L via gh api...")
    gh_bin = "/home/arjun/.local/bin/gh"
    if not os.path.exists(gh_bin):
        gh_bin = "gh"

    cmd = [
        gh_bin,
        "api",
        "-H",
        "Accept: application/vnd.github.v3.raw",
        "/repos/skk-dev/dict/contents/SKK-JISYO.L",
    ]
    res = subprocess.run(cmd, capture_output=True, check=True)
    text = res.stdout.decode("utf-8")

    with open(CACHE_FILE, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Saved cache to: {CACHE_FILE}")
    return text


def build():
    text = fetch_source_dictionary()
    dict_map = {}

    print("Pass 1: Processing okuri-nasi entries (nouns, particles, uninflected)...")
    for line in text.splitlines():
        if line.startswith(";;") or "/" not in line:
            continue
        parts = line.split(" ", 1)
        if len(parts) != 2:
            continue
        key, raw_cands = parts[0], parts[1]
        if any(key.endswith(c) for c in "abcdefghijklmnopqrstuvwxyz"):
            continue

        cands = []
        for c in raw_cands.split("/"):
            clean_c = c.split(";")[0].strip()
            if clean_c.startswith("[") and clean_c.endswith("]"):
                continue
            if clean_c and clean_c not in cands:
                cands.append(clean_c)

        if cands:
            if key not in dict_map:
                dict_map[key] = []
            for c in cands:
                if c not in dict_map[key]:
                    dict_map[key].append(c)

    print("Pass 2: Expanding okuri-ari entries (verbs and adjectives)...")
    for line in text.splitlines():
        if line.startswith(";;") or "/" not in line:
            continue
        parts = line.split(" ", 1)
        if len(parts) != 2:
            continue
        key, raw_cands = parts[0], parts[1]
        if len(key) >= 2 and key[-1] in VOWELS:
            cands = []
            for c in raw_cands.split("/"):
                clean_c = c.split(";")[0].strip()
                if clean_c.startswith("[") and clean_c.endswith("]"):
                    continue
                if clean_c and clean_c not in cands:
                    cands.append(clean_c)

            stem, last = key[:-1], key[-1]
            for suffix_in, suffix_out in VOWELS[last]:
                full_key = stem + suffix_in
                if full_key not in dict_map:
                    dict_map[full_key] = []
                for c in cands:
                    full_cand = c + suffix_out
                    if full_cand not in dict_map[full_key]:
                        dict_map[full_key].append(full_cand)

    print("Pass 3: Applying canonical overrides and conversational aliases...")
    for key, primary in CANONICAL_OVERRIDES.items():
        if key in dict_map:
            if primary in dict_map[key]:
                dict_map[key].remove(primary)
            dict_map[key].insert(0, primary)
        else:
            dict_map[key] = [primary]

    for key, cands in ALIASES.items():
        if key not in dict_map:
            dict_map[key] = []
        for c in reversed(cands):
            if c in dict_map[key]:
                dict_map[key].remove(c)
            dict_map[key].insert(0, c)

    print(f"Total unique dictionary keys: {len(dict_map)}")

    print(f"Writing sorted dictionary to {OUTPUT_FILE}...")
    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)

    # Sort strictly by UTF-8 bytes for binary search compatibility
    sorted_keys = sorted(dict_map.keys(), key=lambda k: k.encode("utf-8"))

    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        for k in sorted_keys:
            cands_str = ",".join(dict_map[k])
            out.write(f"{k}\t{cands_str}\n")

    file_size_kb = os.path.getsize(OUTPUT_FILE) // 1024
    print(f"Done! Created {OUTPUT_FILE} ({file_size_kb} KB)")


if __name__ == "__main__":
    build()
