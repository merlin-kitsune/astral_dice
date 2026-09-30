"""forge-1.20.1 datagen → fabric-1.20.1 datagen 机械转换。

转换面（**只动加载器/平台相关，配方语义逐字保留**）：
  1. 基类 RecipeProvider → FabricRecipeProvider、PackOutput → FabricDataOutput
  2. 删 Forge 专属 import
  3. 两处 NBT Ingredient → Items.POTION（1.20.1 原版无 IngredientType，见 KNOWN-ISSUES KI-F1）
输出到 fabric-1.20.1 的 datagen 包。
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# ⚠️ 2026-10-01 修正:原为**硬编码**独立 worktree 路径(`astral_dice_multiloader_fabric`)——
#    该 worktree 已随第四条线并入 `multi-main` 而删除 ⇒ 硬编码会指向不存在的路径。
#    改为 `__file__` 相对解析(与本目录其它脚本同口径),在合并后的四线树里直接可用。
SRC = ROOT / "forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen"
DST = ROOT / "fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen"
DST.mkdir(parents=True, exist_ok=True)

log = []


def convert_recipe_provider(text: str) -> str:
    # --- import 替换 ---
    pairs = [
        ("import net.minecraft.data.PackOutput;",
         "import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;"),
        ("import net.minecraft.data.recipes.RecipeProvider;",
         "import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;"),
        ("import net.minecraftforge.common.crafting.conditions.IConditionBuilder;\n", ""),
    ]
    for a, b in pairs:
        if a not in text:
            log.append(f"[WARN] import 未命中: {a[:60]}")
        text = text.replace(a, b)

    # --- 类声明 ---
    old_decl = "public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {"
    if old_decl not in text:
        log.append("[WARN] 类声明未命中")
    text = text.replace(old_decl, "public class ModRecipeProvider extends FabricRecipeProvider {")

    # --- 构造器 ---
    old_ctor = "public ModRecipeProvider(PackOutput output) {"
    if old_ctor not in text:
        log.append("[WARN] 构造器未命中")
    text = text.replace(old_ctor, "public ModRecipeProvider(FabricDataOutput output) {")

    # --- ① StrictNBTIngredient（心意相连徽章 / 治疗药水）---
    m = re.search(
        r"\.define\('Z',\s*net\.minecraftforge\.common\.crafting\.StrictNBTIngredient\.of\(.*?\)\)\)",
        text, re.S)
    if m:
        text = text[:m.start()] + ".define('Z', Items.POTION)" + text[m.end():]
        log.append("[OK] StrictNBTIngredient → Items.POTION")
    else:
        log.append("[WARN] StrictNBTIngredient 未命中")

    # --- ② PartialNBTIngredient（肾上腺素·低 / 再生药水）---
    m = re.search(
        r"\.define\('Z',\s*net\.minecraftforge\.common\.crafting\.PartialNBTIngredient\.of\(Items\.POTION,\s*"
        r"potionTag\(\"minecraft:regeneration\"\)\)",
        text, re.S)
    if m:
        text = text[:m.start()] + ".define('Z', Items.POTION)" + text[m.end():]
        log.append("[OK] PartialNBTIngredient → Items.POTION")
    else:
        log.append("[WARN] PartialNBTIngredient 未命中")

    # --- ③ 删掉只为 NBT ingredient 存在的 potionTag 辅助方法 ---
    m = re.search(
        r"\n\s*// 1\.20\.1 无 1\.21 的 DataComponentIngredient.*?\n\s*private static net\.minecraft\.nbt\.CompoundTag potionTag\(String potionId\) \{.*?\n\s*\}\n",
        text, re.S)
    if m:
        text = text[:m.start()] + (
            "\n    // ⚠️ 2026-09-29(移植):原 Forge 版此处有 `potionTag(...)` 辅助方法，"
            "供 `PartialNBTIngredient` / `StrictNBTIngredient` 匹配「指定药水」。\n"
            "    // 1.20.1 **原版没有** IngredientType 扩展点（`unzip -l` 原版 jar 只有 "
            "`Ingredient$Value/$ItemValue/$TagValue`）⇒ Fabric 侧无法表达 NBT 匹配，\n"
            "    // 两个配方（肾上腺素·低 / 心意相连徽章）的药水材料已放宽为 `Items.POTION`（任意药水）。\n"
            "    // 详见 KNOWN-ISSUES **KI-F1**。\n"
        ) + text[m.end():]
        log.append("[OK] potionTag 辅助方法 → 说明注释")
    else:
        log.append("[WARN] potionTag 未命中")

    return text


def convert_item_model_provider(text: str) -> str:
    items = re.findall(r"basicItem\((\w+(?:\.\w+)+)\.get\(\)\);", text)
    log.append(f"[OK] 提取到 {len(items)} 个 basicItem")
    return items


def main() -> int:
    # ---- ModRecipeProvider ----
    t = (SRC / "ModRecipeProvider.java").read_text(encoding="utf-8")
    out = convert_recipe_provider(t)
    (DST / "ModRecipeProvider.java").write_text(out, encoding="utf-8", newline="\n")

    # ---- ModItemModelProvider（重写为 Fabric 形态）----
    t2 = (SRC / "ModItemModelProvider.java").read_text(encoding="utf-8")
    items = convert_item_model_provider(t2)
    body = "\n".join(
        f"        // {i+1:3d}\n        generators.generateFlatItem({n}.get(), ModelTemplates.FLAT_ITEM);"
        if False else f"        generators.generateFlatItem({n}.get(), ModelTemplates.FLAT_ITEM);"
        for i, n in enumerate(items))
    header = '''package com.merlinkitsune.astral_dice.datagen;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelTemplates;

/**
 * 物品模型 provider(Fabric 侧)。
 *
 * <p>对应 forge 线的 {@code ModItemModelProvider#basicItem(item)} —— 两者的产出**逐字节等价**:
 * 都是 {@code assets/<modid>/models/item/<name>.json},内容为
 * {@code {"parent": "item/generated", "textures": {"layer0": "<modid>:item/<name>"}}}。
 * <p>本模组**没有方块** ⇒ {@link #generateBlockStateModels} 空实现。
 * <p>⚠️ 生成的资源在 {@code src/generated/resources} —— 该目录必须由
 * {@code build.gradle} 的 {@code sourceSets.main.resources.srcDir(...)} 纳入资源集,
 * 否则模型不会进产物(见 KNOWN-ISSUES KI-F4)。
 */
public class ModItemModelProvider extends FabricModelProvider {
    public ModItemModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
        // 本模组无方块模型(全部是物品)。
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
'''
    (DST / "ModItemModelProvider.java").write_text(
        header + body + "\n    }\n}\n", encoding="utf-8", newline="\n")
    log.append(f"[OK] ModItemModelProvider 已重写({len(items)} 项)")

    print("\n".join(log))
    return 0


if __name__ == "__main__":
    sys.exit(main())
