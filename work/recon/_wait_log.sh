cd D:/MCProject/astral_dice_multiloader/work/recon
now=$(date +%s); target=1793617820
if [ "$now" -lt "$target" ]; then echo "sleeping $((target-now))s until quota reset"; sleep $((target-now)); fi
echo "=== jobs list for run 41392445400 ==="
curl -sS --max-time 60 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/merlin-kitsune/astral_dice/actions/runs/41392445400/jobs" -o _jobs144.json -w "HTTP=%{http_code} size=%{size_download}"
echo
head -c 400 _jobs144.json
echo
echo "=== job logs ==="
curl -sS -L --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" -H "Accept: application/vnd.github+json" -o _job144.log -w "HTTP=%{http_code} size=%{size_download}" "https://api.github.com/repos/merlin-kitsune/astral_dice/actions/jobs/125106569899/logs"
echo
file _job144.log 2>/dev/null; head -c 300 _job144.log