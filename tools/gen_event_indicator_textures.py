"""事件指示器图标生成器：把 64×64 的 `textures/gui/event/<id>.png` NEAREST 降采样为 32×32 的
`textures/mob_effect/<id>.png`（效果 HUD 图标），两线各写一份。

用法：`python tools/gen_event_indicator_textures.py` 生成；`--check` 只校验（幂等，rc=1 表示需重生成）。
"""
import os, struct, sys, zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IDS = ["red_heat_warning", "storm_approaching", "problem_delivery"]
LINES = ["neoforge-1.21.1", "forge-1.20.1"]


def _chunks(data):
    pos, out = 8, []
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        out.append((typ, data[pos + 8:pos + 8 + ln]))
        pos += 12 + ln
    return out


def read_png(path):
    chunks = _chunks(open(path, "rb").read())
    ck = dict(chunks)
    # IDAT 可能分成多块 ⇒ 必须按顺序拼接（只取最后一块会 zlib 解压失败）
    idat = b"".join(payload for typ, payload in chunks if typ == b"IDAT")
    ihdr = ck[b"IHDR"]
    w, h, depth, ctype = struct.unpack(">IIBB", ihdr[:10])
    if depth != 8 or ctype != 6:
        raise SystemExit("仅支持 8-bit RGBA PNG: %s (depth=%d type=%d width=%d)" % (path, depth, ctype, w))
    raw = zlib.decompress(idat)
    stride, prev, rows = w * 4, bytearray(w * 4), []
    pos = 0
    for _ in range(h):
        ft = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos + stride]); pos += stride
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if ft == 1: line[i] = (line[i] + a) & 0xFF
            elif ft == 2: line[i] = (line[i] + b) & 0xFF
            elif ft == 3: line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        rows.append(bytes(line)); prev = line
    return w, h, rows


def write_png(path, size, rows):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(typ, payload):
        return struct.pack(">I", len(payload)) + typ + payload + struct.pack(">I", zlib.crc32(typ + payload) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


def downscale(w, h, rows):
    step = w // 32
    return [b"".join(rows[y * step][(x * step) * 4:(x * step) * 4 + 4] for x in range(32)) for y in range(32)]


def main():
    check = "--check" in sys.argv
    bad = 0
    for line in LINES:
        for eid in IDS:
            src = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/textures/gui/event", eid + ".png")
            dst = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/textures/mob_effect", eid + ".png")
            w, h, rows = read_png(src)
            if w % 32 or h % 32:
                raise SystemExit("源图尺寸不是 32 的整数倍: %s (%dx%d)" % (src, w, h))
            want = downscale(w, h, rows)
            if check:
                if not os.path.exists(dst):
                    print("MISSING", dst); bad += 1; continue
                if read_png(dst)[2] != want:
                    print("STALE", dst); bad += 1
            else:
                write_png(dst, 32, want)
                print("WROTE", os.path.relpath(dst, ROOT))
    if check:
        print("CHECK_RESULT:", "OK" if bad == 0 else "FAIL(%d)" % bad)
        return 1 if bad else 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
