# -*- coding: utf-8 -*-
src = r"D:/MCProject/astral_dice_multiloader/work/fix/_report_body.md"
dst = r"D:/MCProject/astral_dice_multiloader/work/fix-fabric-attackbar-position.md"
t = open(src, "r", encoding="utf-8", newline="").read()
t = t.replace("\r\n", "\n").replace("\r", "\n")
if not t.endswith("\n"):
    t += "\n"
out = t.replace("\n", "\r\n")
open(dst, "w", encoding="utf-8", newline="").write(out)
b = open(dst, "rb").read()
print("bytes=%d CRLF=%d bareLF=%d CR=%d endsCRLF=%s lines=%d" % (len(b), b.count(b"\r\n"), b.count(b"\n") - b.count(b"\r\n"), b.count(b"\r") - b.count(b"\r\n"), b.endswith(b"\r\n"), out.count("\r\n")))
