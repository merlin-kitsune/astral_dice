package com.merlinkitsune.astral_dice.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 物品模型 provider（Fabric 1.20.1 侧）。
 *
 * <p>产出与 n21 / fabric-1.21.1 <b>逐字节等价</b>：{@code assets/astral_dice/models/item/<name>.json}，
 * 内容为 {@code {"parent": "minecraft:item/generated", "textures": {"layer0": "astral_dice:item/<name>"}}}（无尾换行）。
 *
 * <h2>为什么不用 {@code FabricModelProvider}（重要，勿回退）</h2>
 * <p>原版 {@code net.minecraft.data.models.ModelProvider#run(CachedOutput)} 在生成结束前会做
 * <b>全量方块覆盖校验</b>：把 {@code BuiltInRegistries.BLOCK} 里所有注册方块与本次生成的
 * {@code blockStateOutput.registeredBlocks} 求差集，非空就抛
 * {@code IllegalStateException: Missing blockstate definitions for: [...]}。
 * <p>本模组的方块（{@code chip_shop} / {@code card_shop} / {@code event_drawer} /
 * {@code barricade_collision} / {@code abyss_tentacle_collision}）用的是<b>手写 blockstate + 手写 model</b>
 * （{@code src/main/resources/assets/astral_dice/blockstates|models/block}，datagen 不生成方块模型），
 * 于是校验必然失败 —— 实测 2026-11-10 本线 {@code runDatagen} 首次跑通时在
 * {@code ModelProvider.run(ModelProvider.java:64)} 抛
 * {@code Missing blockstate definitions for: [Block{astral_dice:card_shop}, Block{astral_dice:chip_shop}, Block{astral_dice:event_drawer}, Block{astral_dice:barricade_collision}, Block{astral_dice:abyss_tentacle_collision}]}，
 * 且失败前不写任何产物。此前该线 datagen 因此<b>从未跑通过</b>。
 * <p>n21 / forge-1.20.1 不受影响：NeoForge 的 {@code ModelProvider} 是自家实现、Forge 侧走
 * {@code ItemModelProvider}，都没有这段校验；fabric-1.21.1 已用同一手法绕开（见该线同文件 javadoc）。
 * <p>因此这里直接实现 {@link DataProvider}：物品模型就是「一个 parent + 一个贴图」的固定形状，
 * 不需要 Fabric 的模型生成器，也不会触发方块覆盖校验。方块资源继续留在
 * {@code src/main/resources/...}（手写，不进 datagen）。
 * <p>⚠️ 若将来本线真的要用 datagen 生成方块模型，必须把全部注册方块都生成一遍，否则这条校验一定会炸。
 * <p>⚠️ 生成的资源在 {@code src/generated/resources} —— 该目录必须由
 * {@code build.gradle} 的 {@code sourceSets.main.resources.srcDir(...)} 纳入资源集，否则模型不会进产物。
 * <p>注：本线 {@code event_ticket} 也由本 provider 生成（{@code src/main/resources} 里没有手写件）；
 * fabric-1.21.1 那边 {@code event_ticket} 是手写件、故被排除。
 */
public class ModItemModelProvider implements DataProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final FabricDataOutput output;

    public ModItemModelProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path dir = this.output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve(AstralDiceMod.MODID)
                .resolve("models")
                .resolve("item");

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Item item : flatItems()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!AstralDiceMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", id.getNamespace() + ":item/" + id.getPath());
            json.add("textures", textures);
            futures.add(DataProvider.saveStable(cache, json, dir.resolve(id.getPath() + ".json")));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 需要「纯贴图物品模型」的物品清单（逐条对应 n21 的 {@code basicItem(...)} 列表；156 项）。
     */
    private static List<Item> flatItems() {
        List<Item> items = new ArrayList<>();
        items.add(ModItems.DICE.get());
        items.add(ModItems.GOLDEN_DICE.get());
        items.add(ModItems.GLASS_DICE.get());
        items.add(ModItems.DIAMOND_DICE.get());
        items.add(ModItems.NETHERITE_DICE.get());
        items.add(ModItems.EMERALD_DICE.get());
        items.add(ModItems.OBSIDIAN_DICE.get());
        items.add(ModItems.NETHERRACK_DICE.get());
        items.add(ModItems.WEIRD_DICE.get());
        items.add(ModItems.CRIMSON_DICE.get());
        items.add(ModItems.AMETHYST_DICE.get());
        items.add(ModItems.ENDER_DICE.get());
        items.add(ModItems.NETHER_STAR_DICE.get());
        items.add(ModItems.ATTACK_CARD_MEDIUM.get());
        items.add(ModItems.ATTACK_CARD_LARGE.get());
        items.add(ModItems.ATTACK_CARD_EPIC.get());
        items.add(ModItems.ATTACK_CARD_SHADOW_STRIKE.get());
        items.add(ModItems.ATTACK_CARD_MEITO.get());
        items.add(ModItems.ATTACK_CARD_CHARGE.get());
        items.add(ModItems.ATTACK_CARD_FULL_POWER.get());
        items.add(ModItems.ATTACK_CARD_BITE.get());
        items.add(ModItems.ATTACK_CARD_DRAGON_ROAR.get());
        items.add(ModItems.DEFENSE_CARD_MEDIUM.get());
        items.add(ModItems.DEFENSE_CARD_LARGE.get());
        items.add(ModItems.DEFENSE_CARD_EPIC.get());
        items.add(ModItems.EFFECT_CARD_KING_POWER.get());
        items.add(ModItems.EFFECT_CARD_BERSERK.get());
        items.add(ModItems.EFFECT_CARD_UNWAVERING.get());
        items.add(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get());
        items.add(ModItems.EFFECT_CARD_BARRICADE.get());
        items.add(ModItems.EFFECT_CARD_REPULSION.get());
        items.add(ModItems.REPULSION.get());
        items.add(ModItems.BLANK_SIGN.get());
        items.add(ModItems.PARUNAN_SIGN.get());
        items.add(ModItems.JASMINE_SIGN.get());
        items.add(ModItems.MISAKI_SIGN.get());
        items.add(ModItems.MIMI_SIGN.get());
        items.add(ModItems.LULU_SIGN.get());
        items.add(ModItems.KOMACHI_SIGN.get());
        items.add(ModItems.FLASHLIGHT_CHIP.get());
        items.add(ModItems.CUTTER_CHIP.get());
        items.add(ModItems.CUTTER_BLADE_CHIP.get());
        items.add(ModItems.STAR_COIN.get());
        items.add(ModItems.STAR_COIN_BAG.get());
        items.add(ModItems.MONSTER_LASER_CARD.get());
        items.add(ModItems.MONSTER_BRICK_CARD.get());
        items.add(ModItems.ORBITAL_STRIKE_CARD.get());
        items.add(ModItems.DIRECTIONAL_BLAST_CARD.get());
        items.add(ModItems.BLANK_CHIP.get());
        items.add(ModItems.SCOPE_CHIP.get());
        items.add(ModItems.EAGLE_SCOPE_CHIP.get());
        items.add(ModItems.MEDKIT_EMERGENCY_CHIP.get());
        items.add(ModItems.MEDKIT_COMPLETE_CHIP.get());
        items.add(ModItems.VITAMIN_PILL_CHIP.get());
        items.add(ModItems.TARGET_CHIP.get());
        items.add(ModItems.MARKER_SPRAYER_CHIP.get());
        items.add(ModItems.MAGIC_TOME_CHIP.get());
        items.add(ModItems.BIG_BACKPACK_CHIP.get());
        items.add(ModItems.NINJA_STAR_CHIP.get());
        items.add(ModItems.HAND_FAN_SMALL_CHIP.get());
        items.add(ModItems.HAND_FAN_BIG_CHIP.get());
        items.add(ModItems.STAR_PLATE.get());
        items.add(ModItems.GOLDEN_STAR_PLATE.get());
        items.add(ModItems.REGENERATION_REAGENT.get());
        items.add(ModItems.CONDUCTIVE_WIRE.get());
        items.add(ModItems.STAR_COIN_DUST.get());
        items.add(ModItems.MARK_PAINT.get());
        items.add(ModItems.EIGHT_SIDED_DICE.get());
        items.add(ModItems.PADMAN_SIGN.get());
        items.add(ModItems.FANNY_SIGN.get());
        items.add(ModItems.RIN_SIGN.get());
        items.add(ModItems.LIVING_PAGE.get());
        items.add(ModItems.HAIQING_SIGN.get());
        items.add(ModItems.FATE_GUIDANCE_CARD.get());
        items.add(ModItems.PAPARA_SIGN.get());
        items.add(ModItems.BONNIE_SIGN.get());
        items.add(ModItems.FEN_SIGN.get());
        items.add(ModItems.NANCY_LU_SIGN.get());
        items.add(ModItems.MOSES_SIGN.get());
        items.add(ModItems.PANDAMAN_SIGN.get());
        items.add(ModItems.REN_SIGN.get());
        items.add(ModItems.ZHAO_SIGN.get());
        items.add(ModItems.TERU_SIGN.get());
        items.add(ModItems.NARDIS_SIGN.get());
        items.add(ModItems.MAMUSHI_SIGN.get());
        items.add(ModItems.SOURI_SIGN.get());
        items.add(ModItems.AL_SIGN.get());
        items.add(ModItems.SHERRY_SIGN.get());
        items.add(ModItems.HANNA_SIGN.get());
        items.add(ModItems.KANGEL_SIGN.get());
        items.add(ModItems.AME_SIGN.get());
        items.add(ModItems.MEGAS_SIGN.get());
        items.add(ModItems.LINGLING_SIGN.get());
        items.add(ModItems.JILL_SIGN.get());
        items.add(ModItems.DOROTHY_SIGN.get());
        items.add(ModItems.ALANA_SIGN.get());
        items.add(ModItems.DEEPSEEK_SIGN.get());
        items.add(ModItems.SYKES_SIGN.get());
        items.add(ModItems.FU_CARD.get());
        items.add(ModItems.HUO_CARD.get());
        items.add(ModItems.CHOCOLATE_CAKE.get());
        items.add(ModItems.HAMBURGER.get());
        items.add(ModItems.LUXURY_FEAST.get());
        items.add(ModItems.YOU_HAVE_I_HAVE.get());
        items.add(ModItems.EXPRESS_DELIVERY.get());
        items.add(ModItems.ATM.get());
        items.add(ModItems.BANK_CARD_LOW.get());
        items.add(ModItems.BANK_CARD_HIGH.get());
        items.add(ModItems.BANK_CARD_UNLIMITED.get());
        items.add(ModItems.BOXING_GLOVES_LOW.get());
        items.add(ModItems.BOXING_GLOVES_MEDIUM.get());
        items.add(ModItems.BOXING_GLOVES_HIGH.get());
        items.add(ModItems.SPEED_SKATES_LOW.get());
        items.add(ModItems.SPEED_SKATES_MEDIUM.get());
        items.add(ModItems.SPEED_SKATES_HIGH.get());
        items.add(ModItems.MOTO_HELMET_LOW.get());
        items.add(ModItems.MOTO_HELMET_MEDIUM.get());
        items.add(ModItems.MOTO_HELMET_HIGH.get());
        items.add(ModItems.SANDWICH_LOW.get());
        items.add(ModItems.SANDWICH_MEDIUM.get());
        items.add(ModItems.SANDWICH_HIGH.get());
        items.add(ModItems.ADRENALINE_LOW.get());
        items.add(ModItems.ADRENALINE_HIGH.get());
        items.add(ModItems.MAGIC_QUIVER.get());
        items.add(ModItems.BUFFER_SHIELD.get());
        items.add(ModItems.STAR_COIN_HAMMER.get());
        items.add(ModItems.CURSED_SWORD.get());
        items.add(ModItems.REVENGE_HALBERD.get());
        items.add(ModItems.PIERCING_GUN.get());
        items.add(ModItems.CANDY_CHIP.get());
        items.add(ModItems.FRIENDSHIP_BADGE.get());
        items.add(ModItems.SATELLITE_CHIP.get());
        items.add(ModItems.WARP_ENGINE_CHIP.get());
        items.add(ModItems.ENERGY_RECYCLER.get());
        items.add(ModItems.ELECTRIC_SWORD.get());
        items.add(ModItems.PERPETUAL_MOTION.get());
        items.add(ModItems.CURRENT_CORE_CHIP.get());
        items.add(ModItems.ADVANCED_PERIPHERALS.get());
        items.add(ModItems.BIG_BOWL_STEW_CHIP.get());
        items.add(ModItems.MEMBER_RECOMMENDATION_CHIP.get());
        items.add(ModItems.BOOKMARK_CHIP.get());
        items.add(ModItems.PIGGY_BANK_CHIP.get());
        items.add(ModItems.SMART_WATCH_CHIP.get());
        items.add(ModItems.ELECTRIC_GLOVE_CHIP.get());
        items.add(ModItems.AIRBAG_CHIP.get());
        items.add(ModItems.RAILGUN_CHIP.get());
        items.add(ModItems.PRIMORDIAL_CORE_CHIP.get());
        items.add(ModItems.WHETSTONE_CHIP.get());
        items.add(ModItems.EXTRA_BATTERY_CHIP.get());
        items.add(ModItems.PURPLE_SHOOTING_STAR_CHIP.get());
        items.add(ModItems.GOLDEN_SHOOTING_STAR_CHIP.get());
        items.add(ModItems.FLASHLIGHT_BURST_CHIP.get());
        items.add(ModItems.FORTUNE_STICK_CHIP.get());
        items.add(ModItems.ABYSS_REAGENT_CHIP.get());
        items.add(ModItems.INFINITE_GRID_CHIP.get());
        items.add(ModItems.SENSOR_CHIP.get());
        items.add(ModItems.EVENT_TICKET.get());
        return items;
    }

    @Override
    public String getName() {
        return "Astral Dice / Item Models";
    }
}
