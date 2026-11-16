# -*- coding: utf-8 -*-
import sys, pathlib
sys.path.insert(0, 'tools')
import curseforge_upload as cf

samples = [
    "astral_dice-1.5.0+fabric_1.20.1.jar",
    "astral_dice-1.5.0+neoforge_1.21.1.jar",
    "astral_dice-1.5.0+forge_1.20.1.jar",
    "astral_dice-1.5.0+neoforge_26.1.2.jar",
    "astral_dice-1.5.0-hotfix+fabric_1.20.1.jar",
    "astral_dice-1.5.1+forge_1.20.1.jar",
    "astral_dice-1.6.2+fabric_1.20.1.jar",
    "astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar",
]
print("== 显示名 + 主线判定 ==")
for name in samples:
    ver, loader, mc = cf.parse_jar(pathlib.Path(name))
    print("%-46s -> %-42s mainline=%s" % (name, cf.default_display_name(ver, loader, mc), cf.is_mainline_version(ver)))

print()
print("== 未知加载器回落 ==")
print("default_display_name('1.5.0','quilt','1.20.1') =", cf.default_display_name('1.5.0','quilt','1.20.1'))

class A: pass
a = A(); a.display_name=None; a.changelog=None; a.no_changelog=False; a.changelog_type='markdown'; a.manual_release=False
print()
print("== 真实将 POST 的 metadata（小版本：不应出现 changelog/changelogType） ==")
for fname in ("astral_dice-1.5.0-hotfix+fabric_1.20.1.jar", "astral_dice-1.5.0+neoforge_1.21.1.jar"):
    ver, loader, mc = cf.parse_jar(pathlib.Path(fname))
    cl, note = cf.decide_changelog(ver, None, False)
    job = {"ver": ver, "loader": loader, "mc": mc, "jar": pathlib.Path(fname),
           "display": cf.default_display_name(ver, loader, mc),
           "releaseType": cf.infer_release_type(ver), "gameVersions": [9638, 9639],
           "_changelog": cl, "_relations": None}
    md = cf.upload_metadata(a, job)
    md2 = dict(md)
    if "changelog" in md2:
        md2["changelog"] = "<%d 字符>" % len(md2["changelog"])
    print(fname)
    print("    ", note)
    print("     keys =", sorted(md2))
    print("     json =", __import__("json").dumps(md2, ensure_ascii=False))
