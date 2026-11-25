cd D:/MCProject/astral_dice_multiloader/work/recon
for r in 41429965923 41430344936; do
  curl -sS --max-time 60 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/merlin-kitsune/astral_dice/actions/runs/$r/jobs" -o "_jobs_$r.json" -w "$r HTTP=%{http_code} size=%{size_download}"
  echo
done