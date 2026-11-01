# Writes "w1<TAB>w2" for adjacent words of the dictionary in the sentence files (Leipzig format: id<TAB>sentence)
import re, sys
words = set()
for line in open(sys.argv[1], encoding="utf-8"):
    if line.startswith(" word="):
        words.add(line[6:].split(",f=", 1)[0])
TOKEN = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*")
BREAK = re.compile(r"[,.;:!?()\[\]{}\"„“”»«…/|*+=<>#–—]|\s-\s|\d")
out = sys.stdout
for path in sys.argv[2:]:
    for line in open(path, encoding="utf-8", errors="ignore"):
        sentence = line.split("\t", 1)[-1]
        first = True
        for segment in BREAK.split(sentence):
            tokens = TOKEN.findall(segment)
            if not tokens:
                continue
            if first:
                # the first word of a sentence is capitalized, use the dictionary form
                t = tokens[0]
                if t not in words and t.lower() in words:
                    tokens[0] = t.lower()
                first = False
            prev = None
            for t in tokens:
                if t in words:
                    if prev is not None:
                        out.write(prev + "\t" + t + "\n")
                    prev = t
                else:
                    prev = None
