# Reads "count w1<TAB>w2" lines sorted by w1 (from sort | uniq -c), keeps per word the next words that follow it
# clearly more often than they appear in general (PMI), and writes the combined word list with these bigrams
# instead of the original ones (which have at most 3 next words per word).
import math, sys, time
src, counts_file, dst, per_word, min_count, min_pmi = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), float(sys.argv[6])
unigram = {}
header = None
for line in open(src, encoding="utf-8"):
    if line.startswith(" word="):
        w, rest = line[6:].split(",f=", 1)
        unigram[w] = int(rest.split(",", 1)[0].strip())
    elif line.startswith("dictionary="):
        header = line.strip()
# how often each word appears as the second word of a pair
right = {}
grand = 0
for line in open(counts_file, encoding="utf-8"):
    parts = line.strip().split(None, 1)
    if len(parts) != 2 or "\t" not in parts[1]:
        continue
    c = int(parts[0]); w2 = parts[1].split("\t", 1)[1]
    right[w2] = right.get(w2, 0) + c; grand += c
PMI_MAX = math.log(500.0)
best = {}
def flush(w1, items, total):
    kept = []
    for c, w2 in sorted(items, key=lambda x: -x[0]):
        if c < min_count:
            break
        p = c / total
        pmi = p / (right[w2] / grand)
        if pmi < min_pmi:
            continue  # a word that is common everywhere, nothing special after w1
        u = unigram.get(w2, 15)
        s = min(1.0, math.log(pmi) / PMI_MAX)
        f = min(255, max(u + 1, int(u + (255 - u) * s)))
        kept.append((w2, f))
        if len(kept) >= per_word:
            break
    if kept:
        best[w1] = kept
cur, items, total = None, [], 0
for line in open(counts_file, encoding="utf-8"):
    parts = line.strip().split(None, 1)
    if len(parts) != 2 or "\t" not in parts[1]:
        continue
    c = int(parts[0]); w1, w2 = parts[1].split("\t", 1)
    if w1 != cur:
        if cur is not None:
            flush(cur, items, total)
        cur, items, total = w1, [], 0
    items.append((c, w2)); total += c
if cur is not None:
    flush(cur, items, total)
# same header fields and order as the original, other description, date and version
fields = dict(p.split("=", 1) for p in header.split(","))
fields["description"] = "Deutsch+ mit mehr Wortfolgen (Wortschatz Leipzig CC BY 4.0)"
fields["date"] = str(int(time.time()))
fields["version"] = "19"
order = ["dictionary", "locale", "description", "date", "version"]
new_header = ",".join(f"{k}={fields[k]}" for k in order if k in fields) + "".join(f",{k}={v}" for k, v in fields.items() if k not in order)
with open(dst, "w", encoding="utf-8") as out:
    out.write(new_header + "\n")
    word = None
    def bigrams(w):
        for w2, f in best.get(w, []):
            out.write(f"  bigram={w2},f={f}\n")
    for line in open(src, encoding="utf-8"):
        if line.startswith("dictionary=") or line.startswith("  bigram="):
            continue
        if line.startswith(" word="):
            if word is not None:
                bigrams(word)
            word = line[6:].split(",f=", 1)[0]
        out.write(line)
    if word is not None:
        bigrams(word)
n = sum(len(v) for v in best.values())
print(f"header: {new_header}")
print(f"words with next words: {len(best)}, next words: {n}")
for w in ["ich", "habe", "nicht", "der", "viele", "kannst", "bis", "danke", "wir", "morgen", "können", "Grüße", "freue"]:
    print(w, "->", ", ".join(f"{a}({b})" for a, b in best.get(w, [])[:12]))
