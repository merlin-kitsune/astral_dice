cd F:/MCProject/astral_dice_multiloader/work/recon
curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/actions/runs/41392445400" -o _run144.html -w "run144 HTTP=%{http_code} size=%{size_download}"
echo
grep -oE "annotation-message|uploading missing asset|deleting existing asset|Process completed with exit code [0-9]+|failed to|error" _run144.html | sort | uniq -c | head -20
grep -oE "/astral_dice/runs/[0-9]+" _run144.html | sort -u | head