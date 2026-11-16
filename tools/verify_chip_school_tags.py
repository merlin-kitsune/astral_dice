#!/usr/bin/env python3
"""Chip school item-tag guard (five required lines: neoforge-1.21.1, forge-1.20.1,
neoforge-26.1.2, fabric-1.20.1, fabric-1.21.1).

Authoritative mapping comes from each line's
src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopCatalog.java POOL:
every row is e("id", TIER_..., School.X, Category.Y).

Checks:
1) parse each line's POOL into id -> School;
2) for each line and each of the four school tags, compare the JSON values
   exactly against the POOL order (no duplicates, nothing missing, nothing extra);
3) all five lines must share one mapping and identical tag values; the four
   schools total 41 chips with counts 11/10/9/11; the 29 GENERIC chips must not
   appear in any school tag;
4) all 41 chips must be present in the same line's chips.json values;
5) each line's tag files must live in its own platform directory (item for the
   neoforge lines and fabric-1.21.1, items for the 1.20.1 lines), with no extra
   school_*.json besides the four known names and no misplaced copies;
6) ModTags.java must declare exactly the four SCHOOL_* constants, each pointing
   at its own school tag;
7) every tag JSON is read as bytes and must be CRLF, BOM-free, without bare LF
   and with a trailing newline.

All five lines are required: missing, misplaced, partly missing or extra school
tag files fail on every line.

Usage: python tools/verify_chip_school_tags.py
Standard library only.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

REQUIRED_LINES = ("neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2",
                   "fabric-1.20.1", "fabric-1.21.1")

# (tag suffix, School constant in POOL, expected chip count)
SCHOOLS = (
    ("starlight", "STARLIGHT", 11),
    ("healing", "HEALING", 10),
    ("mark", "MARK", 9),
    ("charge", "CHARGE", 11),
)

EXPECTED_TOTAL = 41
EXPECTED_GENERIC = 29
NAMESPACE = "astral_dice"
TAG_NAMES = tuple("school_%s.json" % suffix for suffix, _school, _count in SCHOOLS)
EXPECTED_CONSTS = dict(("SCHOOL_" + suffix.upper(), "school_" + suffix)
                       for suffix, _school, _count in SCHOOLS)

# Platform tag directory per line: expected, not discovered.
TAG_DIRS = {
    "neoforge-1.21.1": "item",
    "neoforge-26.1.2": "item",
    "fabric-1.21.1": "item",
    "forge-1.20.1": "items",
    "fabric-1.20.1": "items",
}

CATALOG_REL = "src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopCatalog.java"
MODTAGS_REL = "src/main/java/com/merlinkitsune/astral_dice/item/ModTags.java"
TAGS_REL = "src/main/resources/data/astral_dice/tags"

ROW_RE = re.compile(
    r'e\("([a-z0-9_]+)",\s*TIER_[A-Z]+,\s*School\.([A-Z]+),\s*Category\.[A-Z]+\)'
)

DECL_RE = re.compile(r'public static final TagKey<Item>\s+(SCHOOL_[A-Z]+)')

CONST_RE = re.compile(
    r'public static final TagKey<Item> (SCHOOL_[A-Z]+)\s*=\s*'
    r'(?:ItemTags\.create|TagKey\.create\(Registries\.ITEM,)'
    r'[^;]*?"(school_[a-z]+)"\)',
    re.S,
)

errors = []
messages = []


def fail(msg):
    errors.append(msg)


def expected_tag_dir(line_dir):
    """Fixed platform tag directory of a line, or None when the line is unknown."""
    return TAG_DIRS.get(line_dir)


def other_tag_dir(directory):
    return "items" if directory == "item" else "item"


def tag_root(line_dir, directory):
    return ROOT / line_dir / TAGS_REL / directory


def school_files(directory):
    """Sorted school_*.json names inside one tag directory."""
    if not directory.is_dir():
        return []
    return sorted(path.name for path in directory.glob("school_*.json"))


def parse_pool(line_dir):
    """Parse POOL and return (ordered id list, id -> School)."""
    path = ROOT / line_dir / CATALOG_REL
    if not path.is_file():
        fail("%s: catalog not found: %s" % (line_dir, CATALOG_REL))
        return [], {}

    text = path.read_text(encoding="utf-8")
    ids = []
    mapping = {}
    for match in ROW_RE.finditer(text):
        chip_id, school = match.group(1), match.group(2)
        if chip_id in mapping:
            fail("%s: duplicate id in POOL: %s" % (line_dir, chip_id))
            continue
        ids.append(chip_id)
        mapping[chip_id] = school
    return ids, mapping


def read_values(path, label, strict_lines=True):
    """Read a tag JSON values array.

    strict_lines applies the repository line-ending rule (CRLF, no BOM, no bare
    LF, trailing newline) and is used for the school tag files we author. Files
    that merely exist upstream (chips.json) are parsed only, because a Linux/CI
    clone legitimately carries them as LF.
    """
    rel = path.relative_to(ROOT).as_posix()
    if not path.is_file():
        fail("%s: file not found: %s" % (label, rel))
        return None
    raw = path.read_bytes()
    if strict_lines:
        if raw.startswith(b"\xef\xbb\xbf"):
            fail("%s: UTF-8 BOM in %s" % (label, rel))
            return None
        crlf = raw.count(b"\r\n")
        bare_lf = raw.count(b"\n") - crlf
        if bare_lf:
            fail("%s: %d bare LF line ending(s) in %s" % (label, bare_lf, rel))
            return None
        if not crlf:
            fail("%s: no CRLF line ending in %s" % (label, rel))
            return None
        if not raw.endswith(b"\n"):
            fail("%s: missing trailing newline in %s" % (label, rel))
            return None
    try:
        data = json.loads(raw.decode("utf-8-sig"))
    except (OSError, ValueError) as exc:
        fail("%s: JSON parse failed: %s (%s)" % (label, rel, exc))
        return None
    values = data.get("values") if isinstance(data, dict) else None
    if not isinstance(values, list):
        fail("%s: missing values array: %s" % (label, rel))
        return None
    return values


def check_modtags(line_dir):
    """Require exactly the four SCHOOL_* constants pointing at their own tags."""
    path = ROOT / line_dir / MODTAGS_REL
    if not path.is_file():
        fail("%s: ModTags.java not found" % line_dir)
        return
    text = path.read_text(encoding="utf-8")
    found = {}
    for const, tag in CONST_RE.findall(text):
        found.setdefault(const, []).append(tag)
    # Loose pass: any SCHOOL_* declaration counts, whatever factory built it.
    declared = set(DECL_RE.findall(text)) | set(found)
    for const, tag in EXPECTED_CONSTS.items():
        tags = found.get(const) or []
        if len(tags) != 1:
            fail("%s: expected one TagKey constant %s, found %d" % (line_dir, const, len(tags)))
        elif tags[0] != tag:
            fail("%s: TagKey constant %s points to %s, expected %s"
                 % (line_dir, const, tags[0], tag))
    extra = sorted(name for name in declared if name not in EXPECTED_CONSTS)
    if extra:
        fail("%s: unexpected TagKey constants: %s" % (line_dir, ", ".join(extra)))


def check_line(line_dir):
    """Validate one line; returns a summary dict or None."""
    ids, mapping = parse_pool(line_dir)
    if not mapping:
        return None

    directory = expected_tag_dir(line_dir)
    if directory is None:
        fail("%s: unknown expected tag directory (add the line to TAG_DIRS)" % line_dir)
        return None

    root = tag_root(line_dir, directory)
    if not root.is_dir():
        fail("%s: tag directory data/astral_dice/tags/%s not found" % (line_dir, directory))
        return None

    wrong_root = tag_root(line_dir, other_tag_dir(directory))
    misplaced = school_files(wrong_root)
    if misplaced:
        fail("%s: school tag files in wrong directory data/astral_dice/tags/%s: %s"
             % (line_dir, other_tag_dir(directory), ", ".join(misplaced)))

    extra = [name for name in school_files(root) if name not in TAG_NAMES]
    if extra:
        fail("%s: unexpected school tag files in data/astral_dice/tags/%s: %s"
             % (line_dir, directory, ", ".join(extra)))

    chips_values = read_values(root / "chips.json", line_dir + " chips.json",
                               strict_lines=False)
    if chips_values is None:
        return None
    chips_set = set(chips_values)

    summary = {}
    school_ids = []
    tag_values = {}
    for suffix, school, expected_count in SCHOOLS:
        expected = [NAMESPACE + ":" + cid for cid in ids if mapping.get(cid) == school]
        summary[suffix] = len(expected)
        school_ids.extend(cid for cid in ids if mapping.get(cid) == school)

        label = "%s school_%s" % (line_dir, suffix)
        actual = read_values(root / ("school_%s.json" % suffix), label)
        tag_values[suffix] = actual or []
        if actual is None:
            continue

        if len(expected) != expected_count:
            fail("%s: POOL expects %d chips, got %d" % (label, expected_count, len(expected)))
        if len(set(actual)) != len(actual):
            fail("%s: duplicate values in tag" % label)
        if actual != expected:
            missing = [v for v in expected if v not in set(actual)]
            extra_values = [v for v in actual if v not in set(expected)]
            fail("%s: values mismatch with POOL (expected %d, actual %d, missing %s, extra %s)"
                 % (label, len(expected), len(actual), missing, extra_values))

    if len(school_ids) != EXPECTED_TOTAL:
        fail("%s: four-school total %d chips, expected %d"
             % (line_dir, len(school_ids), EXPECTED_TOTAL))

    generic_ids = [cid for cid in ids if mapping.get(cid) == "GENERIC"]
    if len(generic_ids) != EXPECTED_GENERIC:
        fail("%s: GENERIC count %d chips, expected %d"
             % (line_dir, len(generic_ids), EXPECTED_GENERIC))
    tagged = set()
    for suffix, _school, _count in SCHOOLS:
        tagged.update(tag_values.get(suffix) or [])
    leaked = sorted(NAMESPACE + ":" + cid for cid in generic_ids
                    if NAMESPACE + ":" + cid in tagged)
    if leaked:
        fail("%s: GENERIC chips leaked into school tags: %s" % (line_dir, leaked))

    absent = [NAMESPACE + ":" + cid for cid in school_ids
              if NAMESPACE + ":" + cid not in chips_set]
    if absent:
        fail("%s: school chips missing from chips.json: %s" % (line_dir, absent))

    check_modtags(line_dir)

    return {"ids": ids, "mapping": mapping, "summary": summary,
            "tag_values": tag_values, "directory": directory,
            "chips": len(chips_values)}


def report(line_dir, result):
    # Buffered: emitted only when the whole run is clean, so a line that also has
    # a failure can never print an "OK" line next to its FAIL.
    messages.append("CHIP_SCHOOL_TAGS: OK %s dir=%s %s total=%d chips=%d"
                    % (line_dir, result["directory"],
                       " ".join("%s=%d" % (suffix, result["summary"][suffix])
                                for suffix, _s, _c in SCHOOLS),
                       sum(result["summary"].values()), result["chips"]))


def main():
    print("CHIP_SCHOOL_TAGS: checking lines %s" % ", ".join(REQUIRED_LINES))
    reference = None
    reference_line = None
    for line_dir in REQUIRED_LINES:
        directory = expected_tag_dir(line_dir)
        if directory is None:
            fail("%s: unknown expected tag directory (add the line to TAG_DIRS)" % line_dir)
            continue
        missing = [name for name in TAG_NAMES
                   if not (tag_root(line_dir, directory) / name).is_file()]
        if missing:
            fail("%s: missing tag files in data/astral_dice/tags/%s: %s"
                 % (line_dir, directory, ", ".join(missing)))
            misplaced = school_files(tag_root(line_dir, other_tag_dir(directory)))
            if misplaced:
                fail("%s: school tag files in wrong directory data/astral_dice/tags/%s: %s"
                     % (line_dir, other_tag_dir(directory), ", ".join(misplaced)))
            continue
        result = check_line(line_dir)
        if result is None:
            continue
        if reference is None:
            reference = result
            reference_line = line_dir
            report(line_dir, result)
            continue
        if result["mapping"] != reference["mapping"]:
            diff = sorted(set(result["mapping"].items()) ^ set(reference["mapping"].items()))
            fail("%s: id -> School mapping differs from %s: %s"
                 % (line_dir, reference_line, diff))
        if result["ids"] != reference["ids"]:
            fail("%s: POOL order differs from %s" % (line_dir, reference_line))
        for suffix, _school, _count in SCHOOLS:
            if result["tag_values"].get(suffix) != reference["tag_values"].get(suffix):
                fail("%s: school_%s values differ from %s"
                     % (line_dir, suffix, reference_line))
        report(line_dir, result)

    if reference is not None:
        if sum(reference["summary"].values()) != EXPECTED_TOTAL:
            fail("four-school total is not %d" % EXPECTED_TOTAL)
        for suffix, _school, expected_count in SCHOOLS:
            if reference["summary"][suffix] != expected_count:
                fail("school_%s count %d, expected %d"
                     % (suffix, reference["summary"][suffix], expected_count))

    if errors:
        for msg in errors:
            print("CHIP_SCHOOL_TAGS: FAIL " + msg, file=sys.stderr)
        print("CHIP_SCHOOL_TAGS: FAILED (%d problem(s))" % len(errors), file=sys.stderr)
        return 1

    if reference is None:
        print("CHIP_SCHOOL_TAGS: FAIL no line could be checked", file=sys.stderr)
        return 1

    for msg in messages:
        print(msg)
    print("CHIP_SCHOOL_TAGS: OK total=%d starlight=11 healing=10 mark=9 charge=11"
          % EXPECTED_TOTAL)
    return 0


if __name__ == "__main__":
    sys.exit(main())
