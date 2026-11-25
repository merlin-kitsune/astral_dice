cd D:/MCProject/astral_dice_multiloader
echo "--- concurrency / shell / set - ---"
grep -n "concurrency\|^    defaults\|shell:\|set -" .github/workflows/build.yml
echo "--- env block of release step ---"
sed -n "252,271p" .github/workflows/build.yml | cat -n