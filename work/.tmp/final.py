
p = r"F:/MCProject/astral_dice_multiloader/work/fix-fabric-defensebar-position.md"
s = open(p, encoding="utf-8", newline="").read()
rest = s.replace("\r\n","")
print("bytes", len(s.encode()), "CRLF", s.count("\r\n"), "bareLF", rest.count("\n"), "loneCR", rest.count("\r"), "endsCRLF", s.endswith("\r\n"))
