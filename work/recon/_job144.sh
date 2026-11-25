cd D:/MCProject/astral_dice_multiloader/work/recon
curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/actions/runs/41392445400/job/125106569899" -o _job144.html -w "HTTP=%{http_code} size=%{size_download}"
echo
grep -oE "delete-asset|uploading missing asset|deleting existing asset|asset already|not found|Process completed with exit code [0-9]+|Republish|republish" _job144.html | sort | uniq -c
echo "--- step names (from job page) ---"
grep -oE "js-check-steps|data-step-name=\"[^\"]+\"" _job144.html | sort -u | head -30