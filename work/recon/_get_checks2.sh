cd F:/MCProject/astral_dice_multiloader/work/recon
for s in 3d7a3d7bd5f3a54e35a9e6cba974e1d00965c310 6fe38138efff047e63516391140742fff6b032c3 72d2048a1689cdd790b6e56d2def8d5d78d76e45 276f5c2e6dc7a476c1ae55924293c09f122f5d15; do
  curl -sS --max-time 90 -x http://127.0.0.1:7897 -H "User-Agent: Mozilla/5.0" "https://github.com/merlin-kitsune/astral_dice/commit/$s/checks" -o "_checks_$s.html" -w "${s:0:8} HTTP=%{http_code} size=%{size_download}"
  echo
done