#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert Blockbench ``.bbmodel`` (bedrock format) files into GeckoLib geometry
JSON (bedrock ``1.12.0``) plus the embedded texture as a plain ``.png``.

Standard library only, deterministic and idempotent: running it twice produces
byte-identical files and does not rewrite unchanged outputs.

Outputs (per model, written to every loader line so both lines stay identical):

    <line>/src/main/resources/assets/astral_dice/geo/<name>.geo.json
    <line>/src/main/resources/assets/astral_dice/textures/entity/<name>.png

Conversion rules (mirrors the shape of the in-repo sample
``neoforge-1.21.1/.../geo/sykestentacle.geo.json``):

* element ``from`` / ``to``      -> cube ``origin`` / ``size`` (``to - from``)
* element ``origin`` + rotation  -> cube ``pivot`` (only when rotation is set)
* **X 轴预补偿**：GeckoLib loader（``BakedModelFactory#constructCube`` /
  ``#updatePivot``）会再做一次 ``origin.x = -(origin.x + size.x)``、
  ``pivot.x *= -1``、``rotation = (-rx, -ry, rz)``；因此这里先做同样的镜像，
  否则实机上 187 个带旋转的 cube 会全部扭向（2026-11-10 金鲤葫芦事故）。
* per-face 4-number UV rects     -> ``{"uv": [u, v], "uv_size": [du, dv]}``;
  when either delta is negative the anchor moves to the second corner and both
  deltas flip sign, which is what Blockbench itself exports
* 2-number UV shorthand          -> ``{"uv": [u, v], "uv_size": [w, h]}`` with
  the face's own 2D extent taken from ``size``
* outliner tree                  -> ``bones`` (DFS pre-order, ``parent`` links);
  string children become cubes of the enclosing bone
* ``textures[0].source``         -> png, bytes copied verbatim (never resampled)
* ``visible_bounds_*``           -> model bounding box in blocks (16 px = 1 block)

Usage::

    python tools/bbmodel_to_geo.py            # convert, write, verify
    python tools/bbmodel_to_geo.py --check    # verify existing outputs only
    python tools/bbmodel_to_geo.py --report   # print size readings, no writes
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSET_REL = Path("src/main/resources/assets/astral_dice")
LINES = ("neoforge-1.21.1", "forge-1.20.1")

# name -> source .bbmodel (relative to the repo root)
MODELS = (
    {"name": "winegourd", "bbmodel": "models/winegourd.bbmodel"},
    {"name": "winegourdbetter", "bbmodel": "models/winegourdbetter.bbmodel"},
)

FACE_ORDER = ("north", "east", "south", "west", "up", "down")
# axis indices used as (width, height) of each face, taken from the cube size
FACE_AXES = {
    "north": (0, 1),
    "south": (0, 1),
    "east": (2, 1),
    "west": (2, 1),
    "up": (0, 2),
    "down": (0, 2),
}

PIXELS_PER_BLOCK = 16.0
FORMAT_VERSION = "1.12.0"


# --------------------------------------------------------------------------- #
# numbers
# --------------------------------------------------------------------------- #
def num(value):
    """Normalise a JSON number: keep ints, round floats, collapse ``-0.0``."""
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, int):
        return value
    result = round(float(value), 6)
    if result == 0:
        return 0
    if result == int(result) and abs(result) < 1e15:
        return int(result)
    return result


def vec(values):
    return [num(v) for v in values]


def has_rotation(values):
    if not values:
        return False
    return any(abs(num(v)) != 0 for v in values)


# --------------------------------------------------------------------------- #
# elements -> cubes
# --------------------------------------------------------------------------- #
def convert_face_uv(face, size):
    """bbmodel per-face UV -> bedrock ``{"uv": [...], "uv_size": [...]}``."""
    raw = face.get("uv")
    if raw is None:
        return None
    if isinstance(raw, dict):  # {"px": u, "py": v} shorthand
        raw = [raw.get("px"), raw.get("py")]

    if len(raw) == 4:
        u1, v1, u2, v2 = (float(x) for x in raw)
        du, dv = u2 - u1, v2 - v1
        if du < 0 or dv < 0:
            return {"uv": vec([u2, v2]), "uv_size": vec([-du, -dv])}
        return {"uv": vec([u1, v1]), "uv_size": vec([du, dv])}

    if len(raw) == 2:
        # shorthand: no explicit extent, derive it from the cube size
        u, v = (float(x) for x in raw)
        return {"uv": vec([u, v]), "uv_size": vec([size[0], size[1]])}

    raise ValueError("unsupported face uv: %r" % (raw,))


def convert_cube(element):
    if "from" in element and "to" in element:
        origin = vec(element["from"])
        size = vec([element["to"][i] - element["from"][i] for i in range(3)])
    elif "origin" in element and "size" in element:
        origin = vec(element["origin"])
        size = vec(element["size"])
    else:
        raise ValueError("element %r has neither from/to nor origin/size"
                         % (element.get("name"),))

    # Bedrock/Blockbench 的 X 轴与 GeckoLib loader 的约定相反：loader 在
    # BakedModelFactory#constructCube 里会做 origin.x = -(origin.x + size.x)、
    # pivot.x *= -1、rotation = (-rx, -ry, rz)。这里预补偿一次，产出的 geo
    # 才能被 loader 还原成 Blockbench 里的形状（本仓既有 geo 同此约定）。
    origin = [-(origin[0] + size[0]), origin[1], origin[2]]

    cube = {"origin": origin, "size": size}

    if "inflate" in element:
        cube["inflate"] = num(element["inflate"])
    if element.get("mirror") is not None:
        cube["mirror"] = bool(element["mirror"])

    if has_rotation(element.get("rotation")):
        pivot = element.get("pivot", element.get("origin"))
        if pivot is None:
            raise ValueError("rotated element %r has no pivot"
                             % (element.get("name"),))
        rot = vec(element["rotation"])
        cube["pivot"] = vec([-float(pivot[0]), float(pivot[1]), float(pivot[2])])
        cube["rotation"] = vec([-rot[0], -rot[1], rot[2]])

    faces = element.get("faces") or {}
    uv = {}
    for face_name in FACE_ORDER:
        if face_name not in faces:
            continue
        # per-face extents always come from the real cube size
        wa, ha = FACE_AXES[face_name]
        converted = convert_face_uv(faces[face_name], [size[wa], size[ha]])
        if converted is not None:
            uv[face_name] = converted
    # faces written in a non-canonical order still round-trip
    for face_name in faces:
        if face_name not in uv and face_name not in FACE_ORDER:
            converted = convert_face_uv(faces[face_name], [size[0], size[1]])
            if converted is not None:
                uv[face_name] = converted
    if uv:
        cube["uv"] = uv
    return cube


# --------------------------------------------------------------------------- #
# outliner -> bones
# --------------------------------------------------------------------------- #
def collect_bones(nodes, by_uuid, bones, parent=None, counter=None):
    """Flatten the outliner tree into a pre-order ``bones`` list."""
    if counter is None:
        counter = [0]
    for node in nodes:
        if isinstance(node, str):
            continue  # element uuids are collected by the enclosing bone
        counter[0] += 1
        name = node.get("name") or "bone_%d" % counter[0]
        bone = {"name": name}
        if parent is not None:
            bone["parent"] = parent
        # 与 cube 一致：预补偿 loader 的 X 镜像（pivot.x 取负、rotation 的 rx/ry 取负）
        bone_pivot = vec(node.get("origin") or [0, 0, 0])
        bone["pivot"] = vec([-float(bone_pivot[0]), float(bone_pivot[1]),
                             float(bone_pivot[2])])
        if has_rotation(node.get("rotation")):
            rot = vec(node["rotation"])
            bone["rotation"] = vec([-rot[0], -rot[1], rot[2]])
        if "inflate" in node:
            bone["inflate"] = num(node["inflate"])

        cubes = []
        for child in node.get("children", []):
            if isinstance(child, str):
                element = by_uuid.get(child)
                if element is None:
                    raise ValueError("outliner references unknown element uuid %r"
                                     % (child,))
                cubes.append(convert_cube(element))
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        collect_bones(node.get("children", []), by_uuid, bones,
                      parent=name, counter=counter)
    return bones


# --------------------------------------------------------------------------- #
# texture helpers
# --------------------------------------------------------------------------- #
def decode_texture(doc):
    """Return the embedded texture bytes, verbatim."""
    textures = doc.get("textures") or []
    if not textures:
        raise ValueError("model has no textures")
    source = textures[0].get("source") or ""
    marker = "base64,"
    if not source.startswith("data:") or marker not in source:
        raise ValueError("textures[0].source is not an inline base64 data URI")
    return base64.b64decode(source.split(marker, 1)[1])


def png_size(data):
    """Read (width, height) from a PNG IHDR chunk."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG file")
    if data[12:16] != b"IHDR":
        raise ValueError("PNG is missing IHDR")
    width, height = struct.unpack(">II", data[16:24])
    return int(width), int(height)


# --------------------------------------------------------------------------- #
# geometry document
# --------------------------------------------------------------------------- #
def bounding_box(elements):
    lo = [float("inf")] * 3
    hi = [float("-inf")] * 3
    for element in elements:
        if "from" in element and "to" in element:
            low, high = element["from"], element["to"]
        else:
            low = element["origin"]
            high = [element["origin"][i] + element["size"][i] for i in range(3)]
        for i in range(3):
            lo[i] = min(lo[i], low[i], high[i])
            hi[i] = max(hi[i], low[i], high[i])
    return lo, hi


def build_geometry(doc, png_dims):
    elements = doc.get("elements") or []
    by_uuid = {element["uuid"]: element for element in elements}

    bones = collect_bones(doc.get("outliner") or [], by_uuid, [])

    resolution = doc.get("resolution") or {}
    texture_width = resolution.get("width") or png_dims[0]
    texture_height = resolution.get("height") or png_dims[1]

    lo, hi = bounding_box(elements)
    size_px = [hi[i] - lo[i] for i in range(3)]
    center_px = [(hi[i] + lo[i]) / 2.0 for i in range(3)]

    identifier = doc.get("model_identifier") or "unknown"
    geometry = {
        "format_version": FORMAT_VERSION,
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": "geometry.%s" % identifier,
                    "texture_width": num(texture_width),
                    "texture_height": num(texture_height),
                    "visible_bounds_width": round(size_px[0] / PIXELS_PER_BLOCK, 4),
                    "visible_bounds_height": round(size_px[1] / PIXELS_PER_BLOCK, 4),
                    "visible_bounds_offset": [
                        round(-center_px[0] / PIXELS_PER_BLOCK, 4),
                        round(center_px[1] / PIXELS_PER_BLOCK, 4),
                        round(center_px[2] / PIXELS_PER_BLOCK, 4),
                    ],
                },
                "bones": bones,
            }
        ],
    }
    stats = {
        "bones": len(bones),
        "cubes": sum(len(bone.get("cubes", [])) for bone in bones),
        "elements": len(elements),
        "bbox_px": size_px,
        "bbox_blocks": [s / PIXELS_PER_BLOCK for s in size_px],
    }
    return geometry, stats


# --------------------------------------------------------------------------- #
# deterministic JSON writer (matches the in-repo sample's layout)
# --------------------------------------------------------------------------- #
def _scalar(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return "null"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return repr(round(value, 6))
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    raise TypeError("not a scalar: %r" % (value,))


def _is_scalar_list(value):
    return isinstance(value, list) and all(
        isinstance(item, (int, float, str, bool)) or item is None
        for item in value
    )


def _emit(value, indent, inline):
    """Render ``value`` at ``indent`` (an int depth); ``inline`` forces one line."""
    pad = "\t" * indent
    if _scalar_safe(value):
        return _scalar(value)
    if _is_scalar_list(value):
        return "[" + ", ".join(_scalar(item) for item in value) + "]"
    if isinstance(value, list):
        if not value:
            return "[]"
        inner_pad = "\t" * (indent + 1)
        items = []
        for item in value:
            rendered = _emit(item, indent + 1, False)
            # expanded items start their own line, so they carry their indent
            if not _scalar_safe(item) and not _is_scalar_list(item):
                rendered = inner_pad + rendered
            items.append(rendered)
        return "[\n" + ",\n".join(items) + "\n" + pad + "]"
    if isinstance(value, dict):
        if not value:
            return "{}"
        if inline:
            return "{" + ", ".join(
                "%s: %s" % (json.dumps(key, ensure_ascii=False),
                            _emit(item, indent, True))
                for key, item in value.items()
            ) + "}"
        body = ",\n".join(
            "%s%s: %s" % ("\t" * (indent + 1),
                          json.dumps(key, ensure_ascii=False),
                          _emit(item, indent + 1, key == "uv" and isinstance(item, dict)))
            for key, item in value.items()
        )
        return "{\n" + body + "\n" + pad + "}"
    raise TypeError("unsupported value: %r" % (value,))


def _scalar_safe(value):
    return isinstance(value, (int, float, str, bool)) or value is None


def dumps(document):
    return _emit(document, 0, False)


# --------------------------------------------------------------------------- #
# file plumbing
# --------------------------------------------------------------------------- #
def sha256(data):
    return hashlib.sha256(data).hexdigest()


def write_if_changed(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.read_bytes() == data:
        return False
    path.write_bytes(data)
    return True


def targets(name):
    """Every (line, geo path, png path) the model must land in."""
    for line in LINES:
        base = ROOT / line / ASSET_REL
        yield (
            line,
            base / "geo" / ("%s.geo.json" % name),
            base / "textures" / "entity" / ("%s.png" % name),
        )


def convert(model):
    source = ROOT / model["bbmodel"]
    doc = json.loads(source.read_text(encoding="utf-8"))
    png = decode_texture(doc)
    dims = png_size(png)
    geometry, stats = build_geometry(doc, dims)
    text = dumps(geometry).replace("\n", "\r\n") + "\r\n"
    json.loads(text)  # self-check: our writer must emit valid JSON
    return text.encode("utf-8"), png, dims, stats


# --------------------------------------------------------------------------- #
# commands
# --------------------------------------------------------------------------- #
def cmd_write():
    changed_any = False
    for model in MODELS:
        text, png, dims, stats = convert(model)
        print("[%s] bones=%d cubes=%d elements=%d png=%dx%d"
              % (model["name"], stats["bones"], stats["cubes"],
                 stats["elements"], dims[0], dims[1]))
        for line, geo_path, png_path in targets(model["name"]):
            geo_changed = write_if_changed(geo_path, text)
            png_changed = write_if_changed(png_path, png)
            changed_any = changed_any or geo_changed or png_changed
            print("    %-18s geo %-8s png %-8s %s"
                  % (line, "written" if geo_changed else "unchanged",
                     "written" if png_changed else "unchanged",
                     sha256(text)[:12]))
    return 0 if verify_quiet(changed_any) else 1


def verify_quiet(changed):
    problems = verify()
    if problems:
        for problem in problems:
            print("FAIL: %s" % problem, file=sys.stderr)
        return False
    print("verify: ok%s" % ("" if changed else " (nothing to write)"))
    return True


def verify():
    """Structural + cross-line byte identity checks. Returns a problem list."""
    problems = []
    for model in MODELS:
        try:
            text, png, dims, stats = convert(model)
        except Exception as error:  # noqa: BLE001 - report, never crash the run
            problems.append("%s: conversion failed: %s" % (model["name"], error))
            continue
        seen = {}
        for line, geo_path, png_path in targets(model["name"]):
            if not geo_path.exists():
                problems.append("%s missing" % geo_path.relative_to(ROOT))
                continue
            if not png_path.exists():
                problems.append("%s missing" % png_path.relative_to(ROOT))
                continue
            geo_bytes = geo_path.read_bytes()
            png_bytes = png_path.read_bytes()
            try:
                parsed = json.loads(geo_bytes.decode("utf-8"))
            except Exception as error:  # noqa: BLE001
                problems.append("%s: json.load failed: %s"
                                % (geo_path.relative_to(ROOT), error))
                continue
            bones = parsed["minecraft:geometry"][0]["bones"]
            cubes = sum(len(bone.get("cubes", [])) for bone in bones)
            if len(bones) != stats["bones"]:
                problems.append("%s: bones=%d, expected %d"
                                % (geo_path.relative_to(ROOT), len(bones),
                                   stats["bones"]))
            if cubes != stats["elements"]:
                problems.append("%s: cubes=%d, expected %d elements"
                                % (geo_path.relative_to(ROOT), cubes,
                                   stats["elements"]))
            if png_size(png_bytes) != dims:
                problems.append("%s: png size %r != %r"
                                % (png_path.relative_to(ROOT),
                                   png_size(png_bytes), dims))
            if b"\n" in geo_bytes.replace(b"\r\n", b""):
                problems.append("%s: contains bare LF (repo json uses CRLF)"
                                % geo_path.relative_to(ROOT))
            seen[line] = {"geo": sha256(geo_bytes), "png": sha256(png_bytes)}
        if len(seen) > 1:
            geo_hashes = {entry["geo"] for entry in seen.values()}
            png_hashes = {entry["png"] for entry in seen.values()}
            if len(geo_hashes) != 1:
                problems.append("%s: geo differs across lines: %r"
                                % (model["name"], seen))
            if len(png_hashes) != 1:
                problems.append("%s: png differs across lines: %r"
                                % (model["name"], seen))
    return problems


def cmd_check():
    problems = verify()
    for problem in problems:
        print("FAIL: %s" % problem, file=sys.stderr)
    if problems:
        return 1
    print("check: ok")
    return 0


def cmd_report():
    for model in MODELS:
        _text, _png, dims, stats = convert(model)
        width, height, depth = stats["bbox_blocks"]
        print("%s: bones=%d cubes=%d png=%dx%d" % (
            model["name"], stats["bones"], stats["cubes"], dims[0], dims[1]))
        print("    width_blocks=%.3f height_blocks=%.3f depth_blocks=%.3f"
              % (width, height, depth))
        print("    sized() candidate: %.3f x %.3f" % (max(width, depth), height))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true",
                       help="verify outputs without writing")
    group.add_argument("--report", action="store_true",
                       help="print size readings only")
    args = parser.parse_args(argv)
    if args.check:
        return cmd_check()
    if args.report:
        return cmd_report()
    return cmd_write()


if __name__ == "__main__":
    raise SystemExit(main())
