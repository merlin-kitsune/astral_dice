package com.merlinkitsune.astral_dice.event;
import com.merlinkitsune.astral_dice.network.ModNetwork;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.starenginelib.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.InvestigationEventUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.sign.LuluSignItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem;
import com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.client.KeyBindingSetup;
import com.merlinkitsune.astral_dice.combat.DiceCombatContext;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 物品 tooltip 统一染色规则（权威副本；可读版见 docs/tooltip-color-rules.md）。
 *
 * <p>基调：普通文本用灰 {@code §7}；行级 {@code withStyle(...)} 可为整行指定语义底色（标题金、负面行红、
 * 骰子词条各行语义色），与行内规则并行、互不覆盖。颜色即语义 —— 黄只出现在数值上，蓝只出现在时间上。
 *
 * <p><b>规则 0（回落码 = 本行底色码）</b>：行内高亮值结束后，必须用「本行底色」的色码回落，禁止用
 * {@code §r}。tooltip 每一行都经 {@code Tooltip.splitTooltip -> Font.split(component, 170)} 渲染，其
 * defaultStyle 传的是 {@code Style.EMPTY}（{@code Font.java:328} → {@code StringSplitter.java:259} →
 * {@code StringDecomposer.java:114}），因此 {@code §r} 复位为「无颜色 = 白」、{@code §7} 复位为灰，两者都
 * 不等于本行底色。底色码：灰 {@code §7}、金 {@code §6}、粉 {@code §d}、青 {@code §b}、蓝 {@code §9}、
 * 红 {@code §c}、绿 {@code §a}、深绿 {@code §2}、黄 {@code §e}、白 {@code §f}（如金底 {@code 冷却 §95:00§6）}、
 * 粉底 {@code 瞬移到 §e16§d 格内}）。值串末尾无后续文本的回落码不影响显示，保持原样即可。
 *
 * <p><b>规则 1（非时间数值 -&gt; 黄 {@code §e}）</b>：所有非时间数值（点数/层数/次数/格数/区间/百分比/
 * 星级/倍率/距离/费用/兑换比例）一律黄色，并连同其前后紧邻的符号一起染色（{@code +3}、{@code -2}、
 * {@code 50%}、{@code ×2}、{@code ★3}、{@code 1~10}、{@code 2:1}）；值后按规则 0 回落本行底色。
 *
 * <p><b>规则 2（时间 -&gt; 蓝 {@code §9}）</b>：所有时间值一律蓝色，形态为 {@code M:SS} 与 {@code N 秒} /
 * {@code Ns} / {@code N seconds}，同样包含其前后符号（{@code -10秒}、{@code 180 秒}）。秒数格式化统一走
 * {@link #formatSignTime(int)}；{@code 2:1} 这类兑换比例属于规则 1，不是时间。
 *
 * <p><b>规则 3（效果条目 -&gt; 整段蓝）</b>：文本形如 {@code 效果名 (时间)}（含无空格写法
 * {@code 效果名(时间)}）时，名称与时间必须同色、整段蓝色；禁止「名称灰 + 时间蓝」的割裂写法。
 *
 * <p><b>规则 4（含 {@code %} 的文案必须走 {@code tt(...)}）</b>：{@code Component.translatable} 会经
 * {@code TranslatableContents.decomposeTemplate}（{@code TranslatableContents.java:124-171}）把 {@code %%}
 * 拆成独立的无样式 {@code TEXT_PERCENT} 片段，落在高亮区内的 {@code %} 会掉成行底色（灰）；
 * {@link #translationString(String, Object...)} 先用 {@code String.format} 把 {@code %%} 收成 {@code %}，
 * 再整体放进 {@code Component.literal}，颜色才不会丢。故凡 lang 值内含 {@code %%} 的行必须用
 * {@code tt(...)}，不得直接用 {@code Component.translatable(...)}。
 *
 * <p><b>例外（优先级：例外 &gt; 规则 3 &gt; 规则 2 &gt; 规则 1）</b>：
 * <ol>
 *   <li>条目已用 {@code §c} 的保持红色（负面效果条目、负面数值、状态警示行、名称类红色），规则 1/2/3 不再改写；</li>
 *   <li>行级 {@code withStyle(...)} 语义色不作为改写对象，行内数值/时间照常着色；</li>
 *   <li>行内回落码一律按规则 0 处理（{@code §r} / {@code §7} 都不是「恢复本行底色」）；仅值串末尾、
 *       后面没有任何文本（含 {@code \n} 之后的文本）的回落码不做规范化；</li>
 *   <li>{@code §f} 仅用于 {@code sign.key_hint}（白色行）；</li>
 *   <li>列表序号（{@code 1.}）与标签序号（{@code 第一诅咒} / {@code Curse 1} / {@code T4}）不染色；</li>
 *   <li>连接词性质的 {@code +}（如 {@code §e+3§7 + §9黑暗 (0:03)§7} 中间那个）保持灰色。</li>
 * </ol>
 *
 * <p>审计：{@code pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1}（退出码 0 = 无违规）。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class ModTooltipHandler {
    private static void addSignKeyHint(List<Component> tooltip) {
        // 按键名使用独立黄色 Component,避免翻译占位符插入时丢失 §e 染色
        Component key = Component.literal(signKeyName()).withStyle(ChatFormatting.YELLOW);
        tooltip.add(Component.translatable("tooltip.astral_dice.sign.key_hint", key)
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.empty());
    }

    // 主动技能标题(金色,含技能名;冷却倒计时由下方"冷却中"行单独展示)
    private static void addSignActiveTitle(List<Component> tooltip, String skillName) {
        tooltip.add(tt("tooltip.astral_dice.sign.active_title", skillName)
                .withStyle(ChatFormatting.GOLD));
    }

    // 被动技能标题(金色,含技能名)
    private static void addSignPassiveTitle(List<Component> tooltip, String skillName) {
        tooltip.add(tt("tooltip.astral_dice.sign.passive_title", skillName)
                .withStyle(ChatFormatting.GOLD));
    }

    // 备注区多行内容(紫色;无列表符号;无缩进;行内 § 码可覆盖,时间保持黄色)
    // 行内原有的 §7 会重置为灰色,这里统一替换为 §d,使未单独着色的文本保持备注区粉色。
    private static void addSignNoteLines(List<Component> tooltip, String langKey, Object... args) {
        String text = translationString(langKey, args);
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            tooltip.add(Component.literal("§d" + line.trim().replace("§7", "§d")));
        }
    }

    // 读取 lang key 的多行描述,逐行添加(前缀灰色;行内 § 码着色重点)
    // 约定:lang 中每行以 "\n" 分隔;以两个空格开头的行视为子项(带 "- " 符号),其余为普通项(无符号)。
    // 渲染:无缩进;子项加 "§7- " 前缀。
    private static void addSignLines(List<Component> tooltip, String langKey, Object... args) {
        String text = translationString(langKey, args);
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            if (line.startsWith("  ")) {
                // 子项:带符号(前缀灰色,行内 § 码覆盖)
                tooltip.add(Component.literal("§7- " + line.trim()));
            } else {
                // 普通项:无符号
                tooltip.add(Component.literal("§7" + line));
            }
        }
    }

    // 立牌自身计数器(金色数值,其余灰色)
    private static void addSignCounter(List<Component> tooltip, String langKey, Object... args) {
        tooltip.add(Component.empty());
        tooltip.add(tt(langKey, args).withStyle(ChatFormatting.GRAY));
    }

    // 筹码 tooltip 多行文本:lang 值内嵌 "\n" 时逐行拆分添加,避免换行符被渲染成占位方块。
    // 空白行保留为空行;行内 § 码着色保留;基础色按 style 参数。
    private static void addChipLines(List<Component> tooltip, String langKey, ChatFormatting style,
                                     Object... args) {
        String text = translationString(langKey, args);
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                tooltip.add(Component.empty());
            } else {
                tooltip.add(Component.literal(line).withStyle(style));
            }
        }
    }

    // 治愈类 tooltip 统一显示当前治愈点/上限
    private static void addHealingPointsCounter(List<Component> tooltip, Player player) {
        if (player == null) return;
        addSignCounter(tooltip, "tooltip.astral_dice.healing_points",
                HealingManager.getPoints(player), HealingManager.getCap(player));
    }

    // 充能类筹码 tooltip 统一显示当前充能/上限(5 个充能筹码全部调用)
    private static void addChargeCounter(List<Component> tooltip, Player player) {
        if (player == null) return;
        addSignCounter(tooltip, "tooltip.astral_dice.chip.charge",
                ChargeManager.getStacks(player), GameplayConstants.CHARGE_MAX_STACKS);
    }

    // 翻译文本修正:将 %% 转义为普通 % 后放入 Component.literal,
    // 避免 Minecraft 将 %% 拆成无样式片段导致 % 号丢失颜色。
    private static String translationString(String key, Object... args) {
        String raw = net.minecraft.locale.Language.getInstance().getOrDefault(key, key);
        return String.format(raw, args);
    }

    private static net.minecraft.network.chat.MutableComponent tt(String key, Object... args) {
        return Component.literal(translationString(key, args));
    }

    // 冷却中提示(红色)
    private static void addSignCooldownRemaining(List<Component> tooltip, Player player) {
        if (player == null) return;
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        int remainingTicks = cdEnd > 0 ? (int) (cdEnd - player.level().getGameTime()) : 0;
        if (remainingTicks > 0) {
            tooltip.add(tt("tooltip.astral_dice.sign.cooldown_remaining", remainingTicks / 20)
                    .withStyle(ChatFormatting.RED));
        }
    }

    // 秒数 → 立牌 tooltip 时间格式(蓝):§9MM:SS§7(如 60 → §91:00§7)
    private static String formatSignTime(int seconds) {
        return String.format("§9%d:%02d§7", seconds / 60, seconds % 60);
    }

    // 秒数 → 纯 M:SS 文本(不含染色码,由所在组件的样式着色;如 60 → 1:00)
    private static String formatMmSs(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // 立牌主动技能按键显示名(客户端取实际映射,服务端/异常回退 "J")
    private static String signKeyName() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            try {
                return com.merlinkitsune.astral_dice.client.KeyBindingSetup.ACTIVATE_SIGN_KEY
                        .getTranslatedKeyMessage().getString();
            } catch (Throwable ignored) {
            }
        }
        return "J";
    }

    // 卡牌栏按键显示名(客户端取实际映射,服务端/异常回退 "H")
    private static String cardInventoryKeyName() {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            try {
                return com.merlinkitsune.astral_dice.client.KeyBindingSetup.OPEN_CARD_INVENTORY_KEY
                        .getTranslatedKeyMessage().getString();
            } catch (Throwable ignored) {
            }
        }
        return "H";
    }

    // 效果牌 tooltip:当前出牌周期出牌数(current/max)
    private static void addEffectCardPlayCountTooltip(List<Component> tooltip, Player player) {
        if (player == null) {
            tooltip.add(tt("tooltip.astral_dice.card.play_count", "?", "?")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(tt("tooltip.astral_dice.card.play_count",
                            EffectCardPeriod.getPlayCount(player), EffectCardPeriod.getMaxAllowed(player))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // 效果牌 tooltip:当前周期已激活伤害效果牌的总伤害加成
    private static void addActiveDamageBonusTooltip(List<Component> tooltip, Player player) {
        if (player == null) {
            tooltip.add(tt("tooltip.astral_dice.card.active_damage_bonus", "?")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int bonus = 0;
        // 伤害效果牌统一加成 = 忍者立牌「效果牌伤害增益」+ 书签筹码(见 SpellDamageRegistry.effectCardDamageBonus)
        int cardBonus = com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.effectCardDamageBonus(player);
        if (player.hasEffect(ModEffects.MONSTER_LASER.get())) bonus += 4 + cardBonus;
        if (player.hasEffect(ModEffects.MONSTER_BRICK.get())) bonus += 6 + cardBonus;
        if (player.hasEffect(ModEffects.ORBITAL_STRIKE.get())) bonus += 8 + cardBonus;
        if (player.hasEffect(ModEffects.DIRECTIONAL_BLAST.get())) bonus += 5 + cardBonus;
        // 活体书页不再提供「效果期间的被动法伤加成」(2026-09-25 重写):其伤害是一次**命中结算**,
        // 不再叠加到其它远程/魔法伤害上 ⇒ 本行不再计入,否则会把一次性命中当成全周期增益重复显示。
        tooltip.add(tt("tooltip.astral_dice.card.active_damage_bonus", bonus)
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var tooltip = event.getToolTip();
        Player player = event.getEntity();

        if (stack.is(ModItems.DICE.get()) || stack.is(ModItems.GOLDEN_DICE.get()) || stack.is(ModItems.DIAMOND_DICE.get())
                || stack.is(ModItems.NETHERITE_DICE.get()) || stack.is(ModItems.GLASS_DICE.get())
                || stack.is(ModItems.EMERALD_DICE.get()) || stack.is(ModItems.OBSIDIAN_DICE.get())
                || stack.is(ModItems.NETHERRACK_DICE.get()) || stack.is(ModItems.WEIRD_DICE.get())
                || stack.is(ModItems.CRIMSON_DICE.get()) || stack.is(ModItems.AMETHYST_DICE.get())
                || stack.is(ModItems.ENDER_DICE.get()) || stack.is(ModItems.NETHER_STAR_DICE.get())) {
            WeaponEnhancement enhancement = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, null);
            int starLevel = 0;
            int maxCost = 3;
            List<AppliedStone> stones = List.of();
            int usedCost = 0;
            int maxDefenseCost = 3;
            int usedDefenseCost = 0;
            if (enhancement != null) {
                starLevel = enhancement.starLevel();
                maxCost = GameplayConstants.cardCostForStar(enhancement.starLevel());
                stones = enhancement.appliedStones();
                usedCost = enhancement.usedCost();
                maxDefenseCost = GameplayConstants.cardCostForStar(enhancement.starLevel());
                usedDefenseCost = enhancement.usedDefenseCost();
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.empty()
                    .append(tt("tooltip.astral_dice.dice_desc_prefix").withStyle(ChatFormatting.GOLD))
                    .append(tt("tooltip.astral_dice.dice_desc_blessing",
                            formatMmSs(GameplayConstants.DICE_BLESSING_DURATION_SECONDS)).withStyle(ChatFormatting.BLUE))
                    .append(tt("tooltip.astral_dice.dice_desc_middle").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(cardInventoryKeyName()).withStyle(ChatFormatting.YELLOW))
                    .append(tt("tooltip.astral_dice.dice_desc_suffix").withStyle(ChatFormatting.GRAY)));
            if (starLevel > 0) {
                tooltip.add(tt("tooltip.astral_dice.star_level", starLevel)
                        .withStyle(ChatFormatting.GOLD));
            }
            if (starLevel < 3) {
                int req = switch (starLevel) {
                    case 0 -> 15;
                    case 1 -> 20;
                    case 2 -> 25;
                    default -> -1;
                };
                tooltip.add(tt("tooltip.astral_dice.card.upgrade_hint", starLevel, starLevel + 1, req)
                        .withStyle(ChatFormatting.YELLOW));
            }
            // 下界之星骰子(T4):卡牌栏与费用上限恒为最高档、不随星级变化 → 不显示星级费用行
            if (!stack.is(ModItems.NETHER_STAR_DICE.get())) {
                String cost = usedCost + "/" + maxCost;
                tooltip.add(tt("tooltip.astral_dice.cost", cost)
                        .withStyle(ChatFormatting.GRAY));
                String defCost = usedDefenseCost + "/" + maxDefenseCost;
                tooltip.add(tt("tooltip.astral_dice.defense_cost", defCost)
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!stones.isEmpty()) {
                tooltip.add(tt("tooltip.astral_dice.applied_stones")
                        .withStyle(ChatFormatting.GREEN));
                for (AppliedStone stone : stones) {
                    if ("shadow_strike".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §5暗影突袭 §e+3§7 固定 §7| §9黑暗(3秒)§7 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("meito".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §d名刀·噶呜切 §e1~20§7 攻击 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("charge".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §e蓄力 §e+5§7 固定(赐福期间) §7| 赐福结束后返还§c全力攻击")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("full_power".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §c全力攻击 §e+6§7 攻击力 本次攻击的最终攻击力§e+50%§7 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_medium".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §b中 §e1~3§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_large".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §d大 §e1~6§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_epic".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §6特大 §e1~10§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    String stoneName = switch (stone.type()) {
                        case "medium" -> "§b中";
                        case "large" -> "§d大";
                        case "epic" -> "§6特大";
                        default -> stone.type();
                    };
                    String range = switch (stone.type()) {
                        case "medium" -> "1~3";
                        case "large" -> "1~6";
                        case "epic" -> "1~10";
                        default -> "?";
                    };
                    tooltip.add(Component.literal(" §7- " + stoneName + " §e" + range + "§7 攻击 §7[剩余:§e" + stone.uses() + "§7]")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            if (stack.is(ModItems.EMERALD_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.emerald_dice_trade")
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
            if (stack.is(ModItems.OBSIDIAN_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.obsidian_dice_defense")
                        .withStyle(ChatFormatting.BLUE));
                tooltip.add(tt("tooltip.astral_dice.obsidian_dice_fire")
                        .withStyle(ChatFormatting.GOLD));
            }
            if (stack.is(ModItems.WEIRD_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.weird_dice_cooldown")
                        .withStyle(ChatFormatting.GREEN));
                tooltip.add(tt("tooltip.astral_dice.weird_dice_lowroll")
                        .withStyle(ChatFormatting.RED));
            }
            if (stack.is(ModItems.CRIMSON_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.crimson_dice_highroll")
                        .withStyle(ChatFormatting.GOLD));
                tooltip.add(tt("tooltip.astral_dice.crimson_dice_roll1")
                        .withStyle(ChatFormatting.RED));
            }
            if (stack.is(ModItems.AMETHYST_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.amethyst_dice_proc")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (stack.is(ModItems.ENDER_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.ender_dice_totem")
                        .withStyle(ChatFormatting.GOLD));
                tooltip.add(tt("tooltip.astral_dice.ender_dice_teleport")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(tt("tooltip.astral_dice.ender_dice_rainwater")
                        .withStyle(ChatFormatting.RED));
            }
            if (stack.is(ModItems.NETHER_STAR_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.nether_star_dice_maxcard")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(tt("tooltip.astral_dice.nether_star_dice_starattr")
                        .withStyle(ChatFormatting.GOLD));
            }
            if (stack.is(ModItems.GLASS_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.glass_dice_max")
                        .withStyle(ChatFormatting.AQUA));
                tooltip.add(tt("tooltip.astral_dice.glass_dice_death")
                        .withStyle(ChatFormatting.RED));
            }
            if (stack.is(ModItems.NETHERRACK_DICE.get())) {
                tooltip.add(tt("tooltip.astral_dice.netherrack_dice_mining")
                        .withStyle(ChatFormatting.GOLD));
                tooltip.add(tt("tooltip.astral_dice.netherrack_dice_piglin")
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        if (stack.is(ModItems.ATTACK_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            // 费用:黄色 "Cost: " + ⨀(每 1 费一个符号),置于 tooltip 最上方
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("medium"));
            tooltip.add(tt("tooltip.astral_dice.card.attack_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("large"));
            tooltip.add(tt("tooltip.astral_dice.card.attack_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("epic"));
            tooltip.add(tt("tooltip.astral_dice.card.attack_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_SHADOW_STRIKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("shadow_strike", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("shadow_strike"));
            tooltip.add(tt("tooltip.astral_dice.card.shadow_strike", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_MEITO.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("meito", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("meito"));
            tooltip.add(tt("tooltip.astral_dice.card.meito", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_CHARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("charge", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("charge"));
            tooltip.add(tt("tooltip.astral_dice.card.charge", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_FULL_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("full_power", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("full_power"));
            tooltip.add(tt("tooltip.astral_dice.card.full_power", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("defense_medium"));
            tooltip.add(tt("tooltip.astral_dice.card.defense_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("defense_large"));
            tooltip.add(tt("tooltip.astral_dice.card.defense_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = ModDataComponents.CARD_USES.getOrDefault(stack, AppliedStone.defaultUses("defense_epic"));
            tooltip.add(tt("tooltip.astral_dice.card.defense_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EFFECT_CARD_KING_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.king_power").withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_BERSERK.get())) {
            tooltip.add(Component.empty());
            // 第一行 = 精简用法(左键对其他玩家 / 右键对自身),第二行 = 效果本身(2026-09-25 用户裁决)
            tooltip.add(Component.translatable("tooltip.astral_dice.card.berserk")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("effect.astral_dice.berserk.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_UNWAVERING.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.unwavering.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.fight_poison_with_poison.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", effectCardCooldownSeconds(player)).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.BLANK_SIGN.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.blank_sign")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.BLANK_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.blank_chip")
                    .withStyle(ChatFormatting.GRAY));
        }
        // 新材料(1.2.0)合成材料 tip
        if (stack.is(ModItems.REGENERATION_REAGENT.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.regeneration_reagent")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.CONDUCTIVE_WIRE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.conductive_wire")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.STAR_COIN_DUST.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.star_coin_dust")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MARK_PAINT.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.material.mark_paint")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.PARUNAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "套现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.parunan_active");
            addSignPassiveTitle(tooltip, "传奇商人");
            addSignLines(tooltip, "tooltip.astral_dice.sign.parunan_passive",
                    formatSignTime(GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS),
                    GameplayConstants.MAX_STARLIGHT);
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.parunan_starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.JASMINE_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "能量过载");
            addSignLines(tooltip, "tooltip.astral_dice.sign.jasmine_active");
            addSignPassiveTitle(tooltip, "移动充能");
            addSignLines(tooltip, "tooltip.astral_dice.sign.jasmine_passive",
                    GameplayConstants.JASMINE_MAX_BONUS);
            int atkBonus = JasmineSignItem.getAttackBonus(stack);
            int defBonus = JasmineSignItem.getDefenseBonus(stack);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.jasmine_bonus", atkBonus, defBonus);
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.MISAKI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "樱花裂空斩");
            addSignLines(tooltip, "tooltip.astral_dice.sign.misaki_active");
            addSignPassiveTitle(tooltip, "剑气");
            addSignLines(tooltip, "tooltip.astral_dice.sign.misaki_passive");
            // 神秘遗物+ 联动描述:仅当安装神秘遗物+ 模组时展示(置于备注区,紫色,无标题)
            if (net.minecraftforge.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.empty());
                addSignNoteLines(tooltip, "tooltip.astral_dice.sign.misaki_enigmatic");
            }
            int stacks = ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(stack, 0);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.misaki_stacks", stacks);
            // 死亡提示:死亡时丢失全部"剑气"层数
            addSignNoteLines(tooltip, "tooltip.astral_dice.sign.misaki_death_note");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.MIMI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "商品补货");
            addSignLines(tooltip, "tooltip.astral_dice.sign.mimi_active");
            addSignPassiveTitle(tooltip, "过期回收");
            addSignLines(tooltip, "tooltip.astral_dice.sign.mimi_passive");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.LULU_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "治愈粘液");
            addSignLines(tooltip, "tooltip.astral_dice.sign.lulu_active",
                    GameplayConstants.LULU_ACTIVE_RANGE, GameplayConstants.LULU_ACTIVE_RANGE);
            addSignPassiveTitle(tooltip, "细胞分裂");
            addSignLines(tooltip, "tooltip.astral_dice.sign.lulu_passive");
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.KOMACHI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "忍术连击");
            addSignLines(tooltip, "tooltip.astral_dice.sign.komachi_active");
            addSignPassiveTitle(tooltip, "复制者");
            addSignLines(tooltip, "tooltip.astral_dice.sign.komachi_passive");
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.komachi_effect_count",
                        ModAttachments.getKomachiUseCount(player));
                // 伤害增益只在**佩戴立牌**时生效(2026-09-15 裁决):死亡保留的值不因"牌不在身上"而显示为加成
                // 并与伤害结算同源做静默上限夹取(2026-09-19,SpellDamageRegistry#SIGN_DAMAGE_BONUS_CAP)
                addSignCounter(tooltip, "tooltip.astral_dice.sign.komachi_damage_bonus",
                        com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.cappedSignDamageBonus(
                                com.merlinkitsune.astral_dice.item.sign.KomachiSignItem.isEquipped(player)
                                        ? ModAttachments.getKomachiDamageBonus(player) : 0));
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.FLASHLIGHT_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.CUTTER_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.cutter")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.CUTTER_BLADE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.cutter_blade")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.SCOPE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.scope")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EAGLE_SCOPE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.eagle_scope")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.medkit_emergency")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.MEDKIT_COMPLETE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.medkit_complete")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.VITAMIN_PILL_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.vitamin_pill")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.TARGET_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.target")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MARKER_SPRAYER_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.marker_sprayer")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MAGIC_TOME_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.magic_tome")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.magic_tome_count",
                        ModAttachments.getMagicTomeUseCount(player));
            }
        }
        if (stack.is(ModItems.BIG_BACKPACK_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.big_backpack")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.NINJA_STAR_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.ninja_star")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.HAND_FAN_SMALL_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.hand_fan_small")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.HAND_FAN_BIG_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.hand_fan_big",
                            GameplayConstants.HAND_FAN_BIG_RANGE)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EIGHT_SIDED_DICE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.eight_sided_dice",
                            GameplayConstants.MAX_STARLIGHT)
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }

        // === 新筹码 tooltip ===
        if (stack.is(ModItems.ATM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.atm")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_LOW.get()) || stack.is(ModItems.BANK_CARD_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.BANK_CARD_LOW.get())
                            ? "tooltip.astral_dice.chip.bank_card_low"
                            : "tooltip.astral_dice.chip.bank_card_high")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_UNLIMITED.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.bank_card_unlimited")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BOXING_GLOVES_LOW.get()) || stack.is(ModItems.BOXING_GLOVES_MEDIUM.get())
                || stack.is(ModItems.BOXING_GLOVES_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.BOXING_GLOVES_LOW.get())
                            ? "tooltip.astral_dice.chip.boxing_gloves_low"
                            : stack.is(ModItems.BOXING_GLOVES_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.boxing_gloves_medium"
                            : "tooltip.astral_dice.chip.boxing_gloves_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.SPEED_SKATES_LOW.get()) || stack.is(ModItems.SPEED_SKATES_MEDIUM.get())
                || stack.is(ModItems.SPEED_SKATES_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.SPEED_SKATES_LOW.get())
                            ? "tooltip.astral_dice.chip.speed_skates_low"
                            : stack.is(ModItems.SPEED_SKATES_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.speed_skates_medium"
                            : "tooltip.astral_dice.chip.speed_skates_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MOTO_HELMET_LOW.get()) || stack.is(ModItems.MOTO_HELMET_MEDIUM.get())
                || stack.is(ModItems.MOTO_HELMET_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.MOTO_HELMET_LOW.get())
                            ? "tooltip.astral_dice.chip.moto_helmet_low"
                            : stack.is(ModItems.MOTO_HELMET_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.moto_helmet_medium"
                            : "tooltip.astral_dice.chip.moto_helmet_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.SANDWICH_LOW.get()) || stack.is(ModItems.SANDWICH_MEDIUM.get())
                || stack.is(ModItems.SANDWICH_HIGH.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, stack.is(ModItems.SANDWICH_LOW.get())
                            ? "tooltip.astral_dice.chip.sandwich_low"
                            : stack.is(ModItems.SANDWICH_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.sandwich_medium"
                            : "tooltip.astral_dice.chip.sandwich_high",
                    ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.ADRENALINE_LOW.get()) || stack.is(ModItems.ADRENALINE_HIGH.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, stack.is(ModItems.ADRENALINE_LOW.get())
                    ? "tooltip.astral_dice.chip.adrenaline_low"
                    : "tooltip.astral_dice.chip.adrenaline_high", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.MAGIC_QUIVER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.magic_quiver")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.BUFFER_SHIELD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.buffer_shield")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.STAR_COIN_HAMMER.get())) {
            tooltip.add(Component.empty());
            // 必须走 tt():值内含 "%%",Component.translatable 会经 TranslatableContents.decomposeTemplate
            // 把 "%%" 拆成独立的无样式片段,落在黄色区间里的 % 会掉成行底色(灰)。
            tooltip.add(tt("tooltip.astral_dice.chip.star_coin_hammer")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(player), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.CURSED_SWORD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.cursed_sword",
                            GameplayConstants.CURSED_SWORD_BONUS_MAX)
                    .withStyle(ChatFormatting.GRAY));
            // 青之诅咒效果描述:上下各空一行,名称使用红色
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.cursed_sword_blue_curse")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.empty());
            if (net.minecraftforge.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                addChipLines(tooltip, "tooltip.astral_dice.chip.cursed_sword_enigmatic",
                        ChatFormatting.LIGHT_PURPLE);
            }
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.cursed_sword_bonus",
                        ModAttachments.getCursedSwordBonus(player));
            }
        }
        if (stack.is(ModItems.REVENGE_HALBERD.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.revenge_halberd", ChatFormatting.GRAY);
            if (event.getEntity() != null) {
                addChipLines(tooltip, "tooltip.astral_dice.chip.revenge_halberd_current", ChatFormatting.GRAY,
                        "§e+" + RevengeHalberdChipItem.currentAttackBonus(player) + "§7",
                        "§e+" + RevengeHalberdChipItem.currentDefenseBonus(player) + "§7");
            }
        }
        if (stack.is(ModItems.PIERCING_GUN.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.piercing_gun")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.CANDY_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.candy")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.FRIENDSHIP_BADGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.friendship_badge")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.BIG_BOWL_STEW_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.big_bowl_stew", ChatFormatting.GRAY);
            if (event.getEntity() != null) {
                addHealingPointsCounter(tooltip, player);
            }
        }
        if (stack.is(ModItems.SATELLITE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.satellite", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.WARP_ENGINE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.warp_engine", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.ENERGY_RECYCLER.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.energy_recycler", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.ELECTRIC_SWORD.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.electric_sword", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.ADVANCED_PERIPHERALS.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.advanced_peripherals", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.PERPETUAL_MOTION.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.perpetual_motion", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.CURRENT_CORE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.current_core", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.MEMBER_RECOMMENDATION_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.member_recommendation", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.BOOKMARK_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.bookmark", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.PIGGY_BANK_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.piggy_bank", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.SMART_WATCH_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.smart_watch", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.ELECTRIC_GLOVE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.electric_glove", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.AIRBAG_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.airbag", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.RAILGUN_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.railgun", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.PRIMORDIAL_CORE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.primordial_core", ChatFormatting.GRAY);
            addChargeCounter(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.WHETSTONE_CHIP.get())) {
            tooltip.add(Component.empty());
            addChipLines(tooltip, "tooltip.astral_dice.chip.whetstone", ChatFormatting.GRAY);
        }
        if (stack.is(ModItems.PADMAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "真的生气了");
            addSignLines(tooltip, "tooltip.astral_dice.sign.padman_active");
            addSignPassiveTitle(tooltip, "毫无主见");
            addSignLines(tooltip, "tooltip.astral_dice.sign.padman_passive",
                    formatSignTime(GameplayConstants.PADMAN_REFRESH_SECONDS));
            int atkBonus = ModDataComponents.PADMAN_ATK_BONUS.getOrDefault(stack, 0);
            int defBonus = ModDataComponents.PADMAN_DEF_BONUS.getOrDefault(stack, 0);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.padman_bonus", atkBonus, defBonus);
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.FANNY_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "麻烦制造者");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fanny_active");
            addSignPassiveTitle(tooltip, "华点发现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fanny_passive");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.RIN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "活体书页");
            addSignLines(tooltip, "tooltip.astral_dice.sign.rin_active");
            addSignPassiveTitle(tooltip, "调查发现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.rin_passive", 32);
            if (event.getEntity() != null) {
                int pages = com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.livingPageBonusPages(player);
                addSignCounter(tooltip, "tooltip.astral_dice.sign.rin_bonus", pages);
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.LIVING_PAGE.get())) {
            tooltip.add(Component.empty());
            if (event.getEntity() != null) {
                tooltip.add(tt("tooltip.astral_dice.card.living_page",
                                com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.livingPageImpactDamage(player))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(tt("tooltip.astral_dice.card.living_page", "?")
                        .withStyle(ChatFormatting.GRAY));
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.exclusive_owner")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (stack.is(ModItems.MONSTER_LASER_CARD.get()) || stack.is(ModItems.MONSTER_BRICK_CARD.get())
                || stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) || stack.is(ModItems.DIRECTIONAL_BLAST_CARD.get())) {
            String tooltipKey = stack.is(ModItems.MONSTER_LASER_CARD.get()) ? "tooltip.astral_dice.card.monster_laser"
                    : stack.is(ModItems.MONSTER_BRICK_CARD.get()) ? "tooltip.astral_dice.card.monster_brick"
                    : stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) ? "tooltip.astral_dice.card.orbital_strike"
                    : "tooltip.astral_dice.card.directional_blast";
            // 伤害数值显示:基础 + 伤害效果牌统一加成(观看者佩戴忍者立牌/书签时显示加成后的数值)
            int baseDamage = stack.is(ModItems.MONSTER_LASER_CARD.get()) ? 4
                    : stack.is(ModItems.MONSTER_BRICK_CARD.get()) ? 6
                    : stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) ? 8 : 5;
            int effectCardBonus = event.getEntity() != null
                    ? com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.effectCardDamageBonus(event.getEntity())
                    : 0;
            tooltip.add(Component.empty());
            // 组件基础色为灰(普通文本);行内颜色码:数值=黄 §e、时间=蓝 §9
            tooltip.add(Component.translatable(tooltipKey, baseDamage + effectCardBonus)
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        // === 新效果牌(治疗/互动) ===
        if (stack.is(ModItems.CHOCOLATE_CAKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.chocolate_cake")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAMBURGER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.hamburger")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.LUXURY_FEAST.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.luxury_feast")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.YOU_HAVE_I_HAVE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.you_have_i_have")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EXPRESS_DELIVERY.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.express_delivery")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAIQING_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "虚弱印记");
            addSignLines(tooltip, "tooltip.astral_dice.sign.haiqing_active");
            addSignPassiveTitle(tooltip, "幸运星");
            addSignLines(tooltip, "tooltip.astral_dice.sign.haiqing_passive");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.FATE_GUIDANCE_CARD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.fate_guidance_desc")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_saturation")
                    .withStyle(ChatFormatting.GRAY));
            // 联动条目:仅安装相关模组时显示(备注区,紫色,无编号)
            if (net.minecraftforge.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                addSignNoteLines(tooltip, "tooltip.astral_dice.card.fate_curse_mitigation");
            }
            if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
                addSignNoteLines(tooltip, "tooltip.astral_dice.card.fate_spell_mana");
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            effectCardCooldownSeconds(player))
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.exclusive_owner")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (stack.is(ModItems.PAPARA_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "嘬你一口");
            addSignLines(tooltip, "tooltip.astral_dice.sign.papara_active");
            addSignPassiveTitle(tooltip, "可爱即正义");
            addSignLines(tooltip, "tooltip.astral_dice.sign.papara_passive");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.BONNIE_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "隐匿行动");
            addSignLines(tooltip, "tooltip.astral_dice.sign.bonnie_active");
            addSignPassiveTitle(tooltip, "关键线索");
            addSignLines(tooltip, "tooltip.astral_dice.sign.bonnie_passive");
            // 调查阶段事件说明:置于备注区(紫色,无标题)
            tooltip.add(Component.empty());
            addSignNoteLines(tooltip, "tooltip.astral_dice.sign.investigation_desc");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.FEN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "运功");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fen_active");
            addSignPassiveTitle(tooltip, "养精蓄锐");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fen_passive");
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.fen_recharge",
                        ModAttachments.getFenRecharge(player),
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.MAX_RECHARGE);
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.NANCY_LU_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "远程侵入");
            addSignLines(tooltip, "tooltip.astral_dice.sign.nancy_lu_active");
            addSignPassiveTitle(tooltip, "网络防火墙");
            addSignLines(tooltip, "tooltip.astral_dice.sign.nancy_lu_passive");
            // 最下方显示本立牌攻击力与防御力加成
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.nancy_lu_bonus",
                        com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getAttackBonus(player)
                                + com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getActiveAttackBonus(player),
                        com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getDefenseBonus(player));
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.MOSES_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "弱点反击");
            addSignLines(tooltip, "tooltip.astral_dice.sign.moses_active");
            addSignPassiveTitle(tooltip, "精密技巧");
            addSignLines(tooltip, "tooltip.astral_dice.sign.moses_passive");
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.moses_weakness_reveal",
                        com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect.getStacks(player),
                        com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect.MAX_STACKS);
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.PANDAMAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "大吃特吃");
            addSignLines(tooltip, "tooltip.astral_dice.sign.pandaman_active");
            addSignPassiveTitle(tooltip, "有好有坏");
            addSignLines(tooltip, "tooltip.astral_dice.sign.pandaman_passive");
            if (event.getEntity() != null) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.pandaman_health_gain",
                        ModAttachments.getPandamanMaxHealthBonus(player));
                tooltip.add(tt("tooltip.astral_dice.healing_points",
                        HealingManager.getPoints(player), HealingManager.getCap(player))
                        .withStyle(ChatFormatting.GRAY));
            }
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
        if (stack.is(ModItems.REN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "熊孩子特权");
            addSignLines(tooltip, "tooltip.astral_dice.sign.ren_active");
            addSignPassiveTitle(tooltip, "鼠鼠救我");
            addSignLines(tooltip, "tooltip.astral_dice.sign.ren_passive");
            addSignCooldownRemaining(tooltip, event.getEntity());
        }
    }

    /** 效果牌冷却显示:按玩家当前实际冷却取值(有充能时基础值封顶为 20 秒),结果向下取整为秒 */
    private static long effectCardCooldownSeconds(Player player) {
        long baseTicks = GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L;
        long ticks = player != null
                ? com.merlinkitsune.astral_dice.item.ChargeManager.effectCardCooldownTicks(player, baseTicks)
                : baseTicks;
        return Math.max(1L, ticks / 20L);
    }

}
