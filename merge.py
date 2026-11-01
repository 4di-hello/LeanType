# Reads "count w1 w2" lines sorted by w1 (from sort | uniq -c), keeps the most likely next words per word and
# writes the combined word list with these bigrams instead of the original ones.
import math, sys, time
src, counts_file, dst, per_word, min_count = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
unigram = {}
for line in open(src, encoding="utf-8"):
    if line.startswith(" word="):
        w, rest = line[6:].split(",f=", 1)
        unigram[w] = int(rest.split(",", 1)[0].strip())
best = {}
def flush(w1, items, total):
    items.sort(key=lambda x: -x[0])
    kept = []
    for c, w2 in items[:per_word]:
        if c < min_count:
            break
        p = c / total
        # probability on the frequency scale of the dictionary (15..255, log scale from p = 1e-4 to 1)
        f = 15 + int(240 * max(0.0, (math.log(p) - math.log(1e-4)) / -math.log(1e-4)))
        kept.append((w2, max(min(f, 255), unigram.get(w2, 0))))
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
with open(dst, "w", encoding="utf-8") as out:
    word = None
    for line in open(src, encoding="utf-8"):
        if line.startswith("dictionary="):
            parts = [p for p in line.strip().split(",") if not p.startswith(("description=", "date=", "version="))]
            parts += ["description=Deutsch+ (mehr Wortfolgen; Wortschatz Leipzig, CC BY 4.0)", f"date={int(time.time())}", "version=19"]
            out.write(",".join(parts) + "\n")
            continue
        if line.startswith("  bigram="):
            continue
        if line.startswith(" word=") and word is not None:
            for w2, f in best.get(word, []):
                out.write(f"  bigram={w2},f={f}\n")
        if line.startswith(" word="):
            word = line[6:].split(",f=", 1)[0]
        out.write(line)
    if word is not None:
        for w2, f in best.get(word, []):
            out.write(f"  bigram={w2},f={f}\n")
n = sum(len(v) for v in best.values())
print(f"words with next words: {len(best)}, next words: {n}")
for w in ["ich", "habe", "nicht", "der", "Viele", "kannst", "bis", "Danke", "wir", "morgen"]:
    print(w, "->", ", ".join(f"{a}({b})" for a, b in best.get(w, [])[:12]))
