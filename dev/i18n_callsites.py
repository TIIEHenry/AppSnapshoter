#!/usr/bin/env python3
"""Cross-check R.string.* call sites against declared format args in all locales."""
import os
import re
import xml.etree.ElementTree as ET

ROOT = "app/src/main"
FILES = {
    "default": "app/src/main/res/values/strings.xml",
    "en": "app/src/main/res/values-en/strings.xml",
    "zh-rCN": "app/src/main/res/values-zh-rCN/strings.xml",
}
FMT = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(]*)(\d+)?(?:\.(\d+))?([a-zA-Z%])")


def arg_map(text):
    out, auto = {}, 0
    for m in FMT.finditer(text):
        if m.group(5) == "%":
            continue
        if m.group(1) is None:
            auto += 1
            pos = auto
        else:
            pos = int(m.group(1))
        out.setdefault(pos, m.group(5))
    return out


vals = {}
for loc, p in FILES.items():
    vals[loc] = {c.get("name"): "".join(c.itertext())
                 for c in ET.parse(p).getroot() if c.tag == "string"}

argcount = {loc: {k: len(arg_map(v)) for k, v in d.items()} for loc, d in vals.items()}


def split_args(s):
    """split a top-level comma separated arg list"""
    parts, depth, cur, instr, q = [], 0, "", False, ""
    i = 0
    while i < len(s):
        ch = s[i]
        if instr:
            if ch == "\\":
                cur += s[i:i + 2]
                i += 2
                continue
            if ch == q:
                instr = False
            cur += ch
        else:
            if ch in "\"'":
                instr, q = True, ch
                cur += ch
            elif ch in "([{":
                depth += 1
                cur += ch
            elif ch in ")]}":
                depth -= 1
                cur += ch
            elif ch == "," and depth == 0:
                parts.append(cur.strip())
                cur = ""
            else:
                cur += ch
        i += 1
    if cur.strip():
        parts.append(cur.strip())
    return parts


def balanced_call(text, open_idx):
    """return substring inside parens starting at open_idx (index of '(')"""
    depth, i, instr, q = 0, open_idx, False, ""
    while i < len(text):
        ch = text[i]
        if instr:
            if ch == "\\":
                i += 2
                continue
            if ch == q:
                instr = False
        else:
            if ch in "\"'":
                instr, q = True, ch
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    return text[open_idx + 1:i], i
        i += 1
    return None, None


CALL = re.compile(r"\bgetString\s*\(|\bgetText\s*\(|\bgetQuantityString\s*\(")

findings = []
for dirpath, _, names in os.walk(ROOT):
    for n in names:
        if not n.endswith((".kt", ".java")):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        for m in CALL.finditer(src):
            inner, end = balanced_call(src, m.end() - 1)
            if inner is None:
                continue
            args = split_args(inner)
            if not args:
                continue
            first = args[0]
            rm = re.match(r"^R\.string\.(\w+)$", first)
            if not rm:
                continue
            key = rm.group(1)
            supplied = len(args) - 1
            line = src.count("\n", 0, m.start()) + 1
            for loc in FILES:
                need = argcount[loc].get(key)
                if need is None:
                    findings.append((path, line, key, loc, "MISSING KEY", supplied, None))
                elif need != supplied:
                    findings.append((path, line, key, loc, "ARG COUNT", supplied, need))

print("=== getString(R.string.X, ...) arity mismatches ===")
seen = set()
for path, line, key, loc, kind, supplied, need in findings:
    sig = (path, line, key, loc)
    if sig in seen:
        continue
    seen.add(sig)
    print(f"{path}:{line}  {key}  [{loc}] {kind}: call passes {supplied}, resource declares {need}")
print(f"total: {len(seen)}")

# strings with args used without args (literal %1$s shown to user)
print("\n=== resources WITH args referenced by no-arg APIs (setText/Toast/setTitle/etc.) ===")
NOARG = re.compile(
    r"\b(setText|setTitle|setMessage|setHint|setPositiveButton|setNegativeButton|"
    r"setNeutralButton|setSummary|makeText|setError|setContentTitle|setContentText)\s*\("
)
count = 0
for dirpath, _, names in os.walk(ROOT):
    for n in names:
        if not n.endswith((".kt", ".java")):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        for m in NOARG.finditer(src):
            inner, end = balanced_call(src, m.end() - 1)
            if inner is None:
                continue
            for a in split_args(inner):
                rm = re.match(r"^R\.string\.(\w+)$", a.strip())
                if not rm:
                    continue
                key = rm.group(1)
                for loc in FILES:
                    if argcount[loc].get(key, 0) > 0:
                        line = src.count("\n", 0, m.start()) + 1
                        print(f"{path}:{line}  {key} [{loc}] declares "
                              f"{argcount[loc][key]} arg(s) but used without args")
                        count += 1
                        break
print(f"total: {count}")
