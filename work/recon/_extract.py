import os
REPO = r"D:/MCProject/astral_dice_multiloader"
p = os.path.join(REPO, ".github/workflows/build.yml")
wf = open(p, encoding="utf-8").read().splitlines(True)
start = next(i for i,l in enumerate(wf) if l.strip() == "LINES=(")
end = next(i for i,l in enumerate(wf) if "no jars collected at all" in l)
block = "".join(wf[start:end+3])
block = "\n".join(l[10:] if l.startswith(" "*10) else l for l in block.splitlines())
block = block.replace(chr(34)+"$proj"+chr(34)+"/build/libs", chr(34)+"$SANDBOX/$proj"+chr(34)+"/build/libs")
out = os.path.join(REPO, "work/recon/_repro_release_step.sh")
hdr = "#!/usr/bin/env bash\n"
hdr += "# verbatim extraction of .github/workflows/build.yml lines %d-%d (release-step jar collection).\n" % (start+1, end+1)
hdr += "# ONLY change: jar globs read from $SANDBOX/<proj>/build/libs (no Gradle, no gh, no network).\n"
hdr += "# Required env: BASE=<version>, GITHUB_SHA=<commit>, SANDBOX=<fake build root>\n"
hdr += "set -euo pipefail\n"
hdr += "cd " + REPO + "\n"
open(out, "w", encoding="utf-8", newline="\n").write(hdr + block + "\n")
print("extracted lines", start+1, "-", end+1)
print(block[:600])