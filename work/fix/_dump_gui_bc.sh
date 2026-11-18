#!/usr/bin/env bash
set -e
cd /f/MCProject/astral_dice_multiloader
mkdir -p work/fix/bc
J=$(ls .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/*/*.jar | head -1)
echo "JAR=$J"
unzip -o -q "$J" "net/minecraft/client/gui/Gui.class" -d work/fix/bc
javap -p -c -p work/fix/bc/net/minecraft/client/gui/Gui.class > work/fix/gui_bc.txt
echo "LINES=$(wc -l < work/fix/gui_bc.txt)"
grep -n "private void renderPlayerHealth" work/fix/gui_bc.txt
