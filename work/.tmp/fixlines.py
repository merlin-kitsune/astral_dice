
p = r"D:/MCProject/astral_dice_multiloader/work/fix-fabric-defensebar-position.md"
raw = open(p, encoding="utf-8", newline="").read()
t = raw.replace("\r\n", "\n")
BT = chr(96)
pairs = [
 ("原 :54-57 附近", "原 :54-56"),
 ("- " + BT + ":55-58" + BT + " 类字段注释（未变）", "- " + BT + ":53-57" + BT + " 两个累加量字段（未变）"),
 ("- " + BT + ":59-64" + BT + " 原版左列基线常量", "- " + BT + ":59-63" + BT + " 原版左列基线常量（javadoc 59-62、常量 63）"),
 ("- " + BT + ":65-69" + BT + " 行高常量", "- " + BT + ":65-68" + BT + " 行高常量（javadoc 65-67、常量 68）"),
 ("- " + BT + ":70-121" + BT + " " + BT + "leftSeed(...)" + BT + "（含原版原文引用 + 字节码偏移 + displayHealth 说明）",
  "- " + BT + ":70-119" + BT + " " + BT + "leftSeed(...)" + BT + "（javadoc 70-112 含原版原文/字节码偏移/displayHealth 说明；方法体 113-119）"),
 ("- " + BT + ":123-133" + BT + " 两个右列常量的注释按「前序右列修复」同步（给 " + BT + "RESET_RIGHT_NO_AIR" + BT + " 补全 javadoc）",
  "- " + BT + ":121-131" + BT + " 两个右列常量的注释按「前序右列修复」同步（" + BT + "RESET_RIGHT" + BT + " 121-125、" + BT + "RESET_RIGHT_NO_AIR" + BT + " 127-131）"),
]
for o, n in pairs:
    c = t.count(o)
    assert c == 1, (c, o[:50])
    t = t.replace(o, n)
out = t.replace("\n", "\r\n")
open(p, "w", encoding="utf-8", newline="").write(out)
rest = out.replace("\r\n","")
print("ok bytes", len(out.encode()), "CRLF", out.count("\r\n"), "bareLF", rest.count("\n"), "loneCR", rest.count("\r"))
