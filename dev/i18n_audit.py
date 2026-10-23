#!/usr/bin/env python3
"""Compare Android string resources across locales: missing keys and format-arg mismatches."""
import re
import sys
import xml.etree.ElementTree as ET
from collections import OrderedDict

FILES = {
    "default": "app/src/main/res/values/strings.xml",
    "en": "app/src/main/res/values-en/strings.xml",
    "zh-rCN": "app/src/main/res/values-zh-rCN/strings.xml",
}

# Android/java format specifiers, e.g. %1$s, %2$d, %s, %%
FMT = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(]*)(\d+)?(?:\.(\d+))?([a-zA-Z%])")


def line_numbers(path):
    """map name -> line number of the <string>/<plurals>/<string-array> element"""
    out = {}
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            m = re.search(r'<(string|plurals|string-array)\s+name="([^"]+)"', line)
            if m and m.group(2) not in out:
                out[m.group(2)] = i
    return out


def parse(path):
    tree = ET.parse(path)
    root = tree.getroot()
    strings = OrderedDict()
    plurals = OrderedDict()
    arrays = OrderedDict()
    for child in root:
        name = child.get("name")
        if child.tag == "string":
            strings[name] = "".join(child.itertext())
        elif child.tag == "plurals":
            plurals[name] = {
                item.get("quantity"): "".join(item.itertext()) for item in child
            }
        elif child.tag == "string-array":
            arrays[name] = ["".join(i.itertext()) for i in child]
    return strings, plurals, arrays


def specs(text):
    """Return ordered list of (argIndexOrNone, conversion) ignoring %%."""
    res = []
    for m in FMT.finditer(text):
        if m.group(5) == "%":
            continue
        res.append((m.group(1), m.group(5)))
    return res


def normalized(text):
    """Return dict argPosition -> conversion; positional args auto-numbered."""
    out = {}
    auto = 0
    for idx, conv in specs(text):
        if idx is None:
            auto += 1
            pos = auto
        else:
            pos = int(idx)
        out.setdefault(pos, conv)
    return out


def main():
    data = {}
    lines = {}
    for loc, path in FILES.items():
        data[loc] = parse(path)
        lines[loc] = line_numbers(path)

    d_str, d_plu, d_arr = data["default"]
    print(f"=== counts ===")
    for loc in FILES:
        s, p, a = data[loc]
        print(f"{loc:10s} strings={len(s):4d} plurals={len(p):3d} string-arrays={len(a):3d}")

    # translatable="false" detection
    translatable_false = set()
    tree = ET.parse(FILES["default"])
    for child in tree.getroot():
        if child.get("translatable") == "false":
            translatable_false.add(child.get("name"))

    print(f"\ndefault translatable=\"false\" keys: {len(translatable_false)}")

    for loc in ("en", "zh-rCN"):
        s, p, a = data[loc]
        missing_s = [k for k in d_str if k not in s and k not in translatable_false]
        missing_p = [k for k in d_plu if k not in p]
        missing_a = [k for k in d_arr if k not in a]
        extra_s = [k for k in s if k not in d_str]
        print(f"\n=== {loc}: MISSING strings ({len(missing_s)}) ===")
        for k in missing_s:
            print(f"  {k}   (values/strings.xml:{lines['default'].get(k)})")
        if missing_p:
            print(f"=== {loc}: MISSING plurals ({len(missing_p)}) ===")
            for k in missing_p:
                print(f"  {k}   (values/strings.xml:{lines['default'].get(k)})")
        if missing_a:
            print(f"=== {loc}: MISSING string-arrays ({len(missing_a)}) ===")
            for k in missing_a:
                print(f"  {k}   (values/strings.xml:{lines['default'].get(k)})")
        if extra_s:
            print(f"=== {loc}: EXTRA strings not in default ({len(extra_s)}) ===")
            for k in extra_s:
                print(f"  {k}   ({FILES[loc]}:{lines[loc].get(k)})")

    print("\n=== FORMAT-ARG MISMATCHES vs default ===")
    problems = 0
    for loc in ("en", "zh-rCN"):
        s, p, a = data[loc]
        for k, dv in d_str.items():
            if k not in s:
                continue
            want = normalized(dv)
            got = normalized(s[k])
            if want != got:
                problems += 1
                print(f"[{loc}] string/{k}")
                print(f"    default ({FILES['default']}:{lines['default'].get(k)}): {want}  |  {dv!r}")
                print(f"    {loc}     ({FILES[loc]}:{lines[loc].get(k)}): {got}  |  {s[k]!r}")
        for k, dv in d_plu.items():
            if k not in p:
                continue
            for q, dtext in dv.items():
                if q not in p[k]:
                    continue
                want, got = normalized(dtext), normalized(p[k][q])
                if want != got:
                    problems += 1
                    print(f"[{loc}] plurals/{k}[{q}] default={want} {loc}={got}")
        for k, dv in d_arr.items():
            if k not in a or len(a[k]) != len(dv):
                if k in a and len(a[k]) != len(dv):
                    problems += 1
                    print(f"[{loc}] string-array/{k} SIZE default={len(dv)} {loc}={len(a[k])}"
                          f"  (values/strings.xml:{lines['default'].get(k)}, {FILES[loc]}:{lines[loc].get(k)})")
                continue
    print(f"\ntotal format-arg / array-size mismatches: {problems}")

    # Mixed positional + non-positional inside one string (aapt error / runtime risk)
    print("\n=== MIXED positional and implicit args in one string ===")
    for loc, path in FILES.items():
        s, p, a = data[loc]
        for k, v in s.items():
            sp = specs(v)
            if sp and any(i is None for i, _ in sp) and any(i is not None for i, _ in sp):
                print(f"[{loc}] {k} ({path}:{lines[loc].get(k)}): {v!r}")

    # multi-arg strings without positional indices
    print("\n=== multi-arg strings WITHOUT positional indices (%1$s style) ===")
    for loc, path in FILES.items():
        s, p, a = data[loc]
        for k, v in s.items():
            sp = specs(v)
            if len(sp) > 1 and all(i is None for i, _ in sp):
                print(f"[{loc}] {k} ({path}:{lines[loc].get(k)}): {v!r}")


if __name__ == "__main__":
    main()
