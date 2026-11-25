cd D:/MCProject/astral_dice_multiloader/work/recon
curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/commit/be6bc10ea3906c0ff307baffd55be43f8b853e0e/checks" -o _checks_be6.html -w "HTTP=%{http_code} size=%{size_download}"
echo
grep -c "missing jar" _checks_be6.html || true
grep -oE "/merlin-kitsune/astral_dice/runs/[0-9]+" _checks_be6.html | sort -u | head
echo "--- hints ---"
grep -oE "(Build|failure|Failure)" _checks_be6.html | sort | uniq -c | head