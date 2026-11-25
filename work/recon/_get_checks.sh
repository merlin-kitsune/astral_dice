cd D:/MCProject/astral_dice_multiloader/work/recon
for s in 3d7a3d7b 6fe38138 72d2048a 276f5c2e; do
  curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/commit/$s/checks" -o _checks_$s.html -w "$s HTTP=%{http_code} size=%{size_download}"
  echo
done
ls -la _checks_*.html