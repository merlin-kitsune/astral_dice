cd F:/MCProject/astral_dice_multiloader/work/recon
echo "--- attempt job log (anon) ---"
curl -sS -i --max-time 60 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/merlin-kitsune/astral_dice/actions/jobs/125106569899/logs" | head -25
echo
echo "--- release page assets ---"
curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/releases/tag/1.5.0" -o _rel150.html -w "HTTP=%{http_code} size=%{size_download}"
echo
grep -oE "astral_dice-[A-Za-z0-9._+-]+\.jar" _rel150.html | sort | uniq -c