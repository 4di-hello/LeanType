#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pull requests and comments in LeanBitLab/LeanType from a request file (see upstream-pr.yml).

The file has one or more blocks:

    === create
    head: fix/some-branch
    title: Title of the PR
    ---
    Text of the PR (Markdown)

    === comment 889
    ---
    Text of the comment

    === edit 889
    title: New title (optional)
    ---
    New text (optional: empty keeps the text)

Only these three actions, only in LeanBitLab/LeanType, and new PRs only from branches of this repository.
"""
import json
import re
import subprocess
import sys

UPSTREAM = "LeanBitLab/LeanType"
FORK_OWNER = "4di-hello"


def parse(text):
    blocks = []
    for part in re.split(r"^=== ", text, flags=re.M)[1:]:
        header, _, rest = part.partition("\n")
        if not rest.endswith("\n"):
            rest += "\n"
        meta_text, sep, body = rest.partition("\n---\n")
        if not sep:  # "---" right after the header line
            if rest.startswith("---\n"):
                meta_text, body = "", rest[4:]
            else:
                raise ValueError(f"block '{header}': no '---' line before the text")
        words = header.split()
        if not words or words[0] not in ("create", "comment", "edit"):
            raise ValueError(f"unknown action '{header}'")
        block = {"action": words[0], "body": body.strip()}
        if words[0] != "create":
            if len(words) != 2 or not words[1].isdigit():
                raise ValueError(f"'{header}': needs the PR number")
            block["pr"] = words[1]
        for line in meta_text.splitlines():
            if line.strip():
                key, _, value = line.partition(":")
                block[key.strip()] = value.strip()
        if words[0] == "create":
            if not block.get("head") or not block.get("title") or not block["body"]:
                raise ValueError("create needs head, title and a text")
            if not re.fullmatch(r"[\w./-]+", block["head"]):
                raise ValueError(f"bad branch name {block['head']}")
        if words[0] == "comment" and not block["body"]:
            raise ValueError("comment needs a text")
        blocks.append(block)
    if not blocks:
        raise ValueError("no request in the file")
    return blocks


def gh(*args, body=None):
    cmd = ["gh", *args, "--repo", UPSTREAM]
    if body is not None:
        cmd += ["--body-file", "-"]
    result = subprocess.run(cmd, input=body, text=True, capture_output=True)
    print(result.stdout.strip())
    if result.returncode != 0:
        print(result.stderr.strip(), file=sys.stderr)
        raise SystemExit(f"failed: {' '.join(args[:3])}")
    return result.stdout.strip()


def run(block):
    action = block["action"]
    if action == "create":
        url = gh("pr", "create", "--base", block.get("base", "main"), "--head", f"{FORK_OWNER}:{block['head']}",
                 "--title", block["title"], body=block["body"])
        print(f"::notice title=PR created::{url}")
    elif action == "comment":
        url = gh("pr", "comment", block["pr"], body=block["body"])
        print(f"::notice title=Comment on #{block['pr']}::{url}")
    else:
        args = ["pr", "edit", block["pr"]]
        if block.get("title"):
            args += ["--title", block["title"]]
        if not block.get("title") and not block["body"]:
            raise SystemExit(f"edit #{block['pr']}: nothing to change")
        gh(*args, body=block["body"] or None)
        print(f"::notice title=PR #{block['pr']} edited::{json.dumps({k: v for k, v in block.items() if k != 'body'})}")


if __name__ == "__main__":
    blocks = parse(open(sys.argv[1], encoding="utf-8").read())
    if len(sys.argv) > 2 and sys.argv[2] == "--check":
        print(json.dumps(blocks, indent=2, ensure_ascii=False))
    else:
        for b in blocks:
            run(b)
