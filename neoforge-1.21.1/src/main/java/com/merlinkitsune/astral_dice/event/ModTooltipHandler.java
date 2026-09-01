package com.merlinkitsune.astral_dice.event;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.astral_dice.item.BossEntityUtil;
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
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
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

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
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

    // 治愈类 tooltip 统一显示当前治愈点/上限
    private static void addHealingPointsCounter(List<Component> tooltip, Player p) {
        if (p == null) return;
        addSignCounter(tooltip, "tooltip.astral_dice.healing_points",
                HealingManager.getPoints(p), HealingManager.getCap(p));
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
    private static void addSignCooldownRemaining(List<Component> tooltip, Player p) {
        if (p == null) return;
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(p);
        int remainingTicks = cdEnd > 0 ? (int) (cdEnd - p.level().getGameTime()) : 0;
        if (remainingTicks > 0) {
            tooltip.add(tt("tooltip.astral_dice.sign.cooldown_remaining", remainingTicks / 20)
                    .withStyle(ChatFormatting.RED));
        }
    }

    // 秒数 → 立牌 tooltip 时间格式(蓝):§9MM:SS§7(如 60 → §91:00§7)
    private static String formatSignTime(int seconds) {
        return String.format("§9%d:%02d§7", seconds / 60, seconds % 60);
    }

    // 立牌主动技能按键显示名(客户端取实际映射,服务端/异常回退 "J")
    private static String signKeyName() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
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
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            try {
                return com.merlinkitsune.astral_dice.client.KeyBindingSetup.OPEN_CARD_INVENTORY_KEY
                        .getTranslatedKeyMessage().getString();
            } catch (Throwable ignored) {
            }
        }
        return "H";
    }

    // 效果牌 tooltip:当前出牌周期出牌数(current/max)
    private static void addEffectCardPlayCountTooltip(List<Component> tooltip, Player p) {
        if (p == null) {
            tooltip.add(tt("tooltip.astral_dice.card.play_count", "?", "?")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(tt("tooltip.astral_dice.card.play_count",
                            EffectCardPeriod.getPlayCount(p), EffectCardPeriod.getMaxAllowed(p))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // 效果牌 tooltip:当前周期已激活伤害效果牌的总伤害加成
    private static void addActiveDamageBonusTooltip(List<Component> tooltip, Player p) {
        if (p == null) {
            tooltip.add(tt("tooltip.astral_dice.card.active_damage_bonus", "?")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int bonus = 0;
        int komachi = ModAttachments.getKomachiDamageBonus(p);
        if (p.hasEffect(ModEffects.MONSTER_LASER)) bonus += 4 + komachi;
        if (p.hasEffect(ModEffects.MONSTER_BRICK)) bonus += 6 + komachi;
        if (p.hasEffect(ModEffects.ORBITAL_STRIKE)) bonus += 8 + komachi;
        if (p.hasEffect(ModEffects.DIRECTIONAL_BLAST)) bonus += 5 + komachi;
        if (p.hasEffect(ModEffects.LIVING_PAGE)) {
            int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_PAGE_BONUS_CAP);
            bonus += 2 + pages + komachi;
        }
        tooltip.add(tt("tooltip.astral_dice.card.active_damage_bonus", bonus)
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var tooltip = event.getToolTip();
        Player player = event.getEntity() instanceof Player p ? p : null;

        if (stack.is(ModItems.DICE.get()) || stack.is(ModItems.GOLDEN_DICE.get()) || stack.is(ModItems.DIAMOND_DICE.get())
                || stack.is(ModItems.NETHERITE_DICE.get())) {
            WeaponEnhancement enhancement = stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
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
            tooltip.add(Component.translatable("tooltip.astral_dice.dice_desc",
                    GameplayConstants.DICE_BLESSING_DURATION_SECONDS, cardInventoryKeyName())
                    .withStyle(ChatFormatting.GOLD));
            if (starLevel > 0) {
                tooltip.add(Component.translatable("tooltip.astral_dice.star_level", starLevel)
                        .withStyle(ChatFormatting.GOLD));
            }
            if (starLevel < 3) {
                int req = switch (starLevel) {
                    case 0 -> 15;
                    case 1 -> 20;
                    case 2 -> 25;
                    default -> -1;
                };
                tooltip.add(Component.translatable("tooltip.astral_dice.card.upgrade_hint", starLevel, starLevel + 1, req)
                        .withStyle(ChatFormatting.YELLOW));
            }
            String cost = usedCost + "/" + maxCost;
            tooltip.add(Component.translatable("tooltip.astral_dice.cost", cost)
                    .withStyle(ChatFormatting.GRAY));
            String defCost = usedDefenseCost + "/" + maxDefenseCost;
            tooltip.add(Component.translatable("tooltip.astral_dice.defense_cost", defCost)
                    .withStyle(ChatFormatting.GRAY));
            if (!stones.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.astral_dice.applied_stones")
                        .withStyle(ChatFormatting.GREEN));
                for (AppliedStone stone : stones) {
                    if ("shadow_strike".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §5暗影突袭 §e+3§7 固定 §7| 黑暗(§93秒§7) §7[剩余:§e" + stone.uses() + "§7]")
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
                        tooltip.add(Component.literal(" §7- §c全力攻击 §e+6§7 攻击力 §e本次攻击的最终攻击力+50%§7 §7[剩余:§e" + stone.uses() + "§7]")
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
        }

        if (stack.is(ModItems.ATTACK_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            // 费用:黄色 "Cost: " + ⨀(每 1 费一个符号),置于 tooltip 最上方
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("medium"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("large"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("epic"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_SHADOW_STRIKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("shadow_strike", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("shadow_strike"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.shadow_strike", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_MEITO.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("meito", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("meito"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.meito", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_CHARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("charge", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("charge"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.charge", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_FULL_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("full_power", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("full_power"));
            tooltip.add(tt("tooltip.astral_dice.card.full_power", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_medium"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_large"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_epic"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EFFECT_CARD_KING_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.king_power").withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_BERSERK.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.berserk.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_UNWAVERING.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.unwavering.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.fight_poison_with_poison.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
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
        if (stack.is(ModItems.PARUNAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "套现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.parunan_active");
            addSignPassiveTitle(tooltip, "传奇商人");
            addSignLines(tooltip, "tooltip.astral_dice.sign.parunan_passive",
                    formatSignTime(GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS),
                    GameplayConstants.MAX_STARLIGHT);
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.parunan_starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.JASMINE_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "能量过载");
            addSignLines(tooltip, "tooltip.astral_dice.sign.jasmine_active");
            addSignPassiveTitle(tooltip, "移动充能");
            addSignLines(tooltip, "tooltip.astral_dice.sign.jasmine_passive",
                    GameplayConstants.JASMINE_MAX_BONUS, GameplayConstants.JASMINE_MAX_BONUS * 2);
            int atkBonus = JasmineSignItem.getAttackBonus(stack);
            int defBonus = JasmineSignItem.getDefenseBonus(stack);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.jasmine_bonus", atkBonus, defBonus * 2);
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.MISAKI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "樱花裂空斩");
            addSignLines(tooltip, "tooltip.astral_dice.sign.misaki_active");
            addSignPassiveTitle(tooltip, "剑气");
            addSignLines(tooltip, "tooltip.astral_dice.sign.misaki_passive");
            // 神秘遗物+ 联动描述:仅当安装神秘遗物+ 模组时展示(置于备注区,紫色,无标题)
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.empty());
                addSignNoteLines(tooltip, "tooltip.astral_dice.sign.misaki_enigmatic");
            }
            int stacks = stack.getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.misaki_stacks", stacks);
            // 死亡提示:死亡时丢失全部"剑气"层数
            addSignNoteLines(tooltip, "tooltip.astral_dice.sign.misaki_death_note");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.MIMI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "商品补货");
            addSignLines(tooltip, "tooltip.astral_dice.sign.mimi_active");
            addSignPassiveTitle(tooltip, "过期回收");
            addSignLines(tooltip, "tooltip.astral_dice.sign.mimi_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.LULU_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "治愈粘液");
            addSignLines(tooltip, "tooltip.astral_dice.sign.lulu_active",
                    GameplayConstants.LULU_ACTIVE_RANGE, GameplayConstants.LULU_ACTIVE_RANGE);
            addSignPassiveTitle(tooltip, "细胞分裂");
            addSignLines(tooltip, "tooltip.astral_dice.sign.lulu_passive");
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.KOMACHI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "忍术连击");
            addSignLines(tooltip, "tooltip.astral_dice.sign.komachi_active");
            addSignPassiveTitle(tooltip, "复制者");
            addSignLines(tooltip, "tooltip.astral_dice.sign.komachi_passive");
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.komachi_damage_bonus",
                        ModAttachments.getKomachiDamageBonus(p),
                        com.merlinkitsune.astral_dice.component.GameplayConstants.KOMACHI_DAMAGE_BONUS_MAX);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FLASHLIGHT_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.CUTTER_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.cutter")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.CUTTER_BLADE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.cutter_blade")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
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
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.MEDKIT_COMPLETE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.medkit_complete")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.VITAMIN_PILL_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.vitamin_pill")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.TARGET_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.target",
                            GameplayConstants.TARGET_CHIP_RANGE)
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
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }

        // === 新筹码 tooltip ===
        if (stack.is(ModItems.ATM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.chip.atm")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_LOW.get()) || stack.is(ModItems.BANK_CARD_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.BANK_CARD_LOW.get())
                            ? "tooltip.astral_dice.chip.bank_card_low"
                            : "tooltip.astral_dice.chip.bank_card_high")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_UNLIMITED.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.bank_card_unlimited")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
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
            tooltip.add(Component.translatable(stack.is(ModItems.SANDWICH_LOW.get())
                            ? "tooltip.astral_dice.chip.sandwich_low"
                            : stack.is(ModItems.SANDWICH_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.sandwich_medium"
                            : "tooltip.astral_dice.chip.sandwich_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ADRENALINE_LOW.get()) || stack.is(ModItems.ADRENALINE_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.ADRENALINE_LOW.get())
                            ? "tooltip.astral_dice.chip.adrenaline_low"
                            : "tooltip.astral_dice.chip.adrenaline_high")
                    .withStyle(ChatFormatting.GRAY));
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
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.STAR_COIN_HAMMER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.star_coin_hammer")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
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
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.translatable("tooltip.astral_dice.chip.cursed_sword_enigmatic")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.cursed_sword_bonus",
                        ModAttachments.getCursedSwordBonus(p));
            }
        }
        if (stack.is(ModItems.REVENGE_HALBERD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.revenge_halberd")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                tooltip.add(Component.translatable("tooltip.astral_dice.chip.revenge_halberd_current",
                        Component.literal("+" + RevengeHalberdChipItem.currentAttackBonus(p)).withStyle(ChatFormatting.YELLOW),
                        Component.literal("+" + RevengeHalberdChipItem.currentDefenseBonus(p) * 2).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.GRAY));
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
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.FRIENDSHIP_BADGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.friendship_badge")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addHealingPointsCounter(tooltip, p);
            }
        }
        if (stack.is(ModItems.SATELLITE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.satellite")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.PADMAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "真的生气了");
            addSignLines(tooltip, "tooltip.astral_dice.sign.padman_active");
            addSignPassiveTitle(tooltip, "毫无主见");
            addSignLines(tooltip, "tooltip.astral_dice.sign.padman_passive",
                    formatSignTime(GameplayConstants.PADMAN_REFRESH_SECONDS));
            int atkBonus = stack.getOrDefault(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
            int defBonus = stack.getOrDefault(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
            addSignCounter(tooltip, "tooltip.astral_dice.sign.padman_bonus", atkBonus, defBonus * 2);
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FANNY_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "麻烦制造者");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fanny_active");
            addSignPassiveTitle(tooltip, "华点发现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fanny_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.RIN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "活体书页");
            addSignLines(tooltip, "tooltip.astral_dice.sign.rin_active");
            addSignPassiveTitle(tooltip, "调查发现");
            addSignLines(tooltip, "tooltip.astral_dice.sign.rin_passive", 32);
            if (event.getEntity() instanceof Player p) {
                int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_PAGE_BONUS_CAP);
                addSignCounter(tooltip, "tooltip.astral_dice.sign.rin_bonus", pages);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.LIVING_PAGE.get())) {
            tooltip.add(Component.empty());
            if (event.getEntity() instanceof Player p) {
                // 活体书页伤害 = 基础 2 + 调查员(rin)已使用数量 + 忍者立牌效果牌伤害增益
                int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_PAGE_BONUS_CAP);
                // 组件基础色为灰(普通文本);行内颜色码:数值=黄 §e、时间=蓝 §9
                tooltip.add(Component.translatable("tooltip.astral_dice.card.living_page",
                                2 + pages + ModAttachments.getKomachiDamageBonus(p))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.astral_dice.card.living_page", "?")
                        .withStyle(ChatFormatting.GRAY));
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
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
            // 伤害数值显示:基础 + 忍者立牌效果牌伤害增益(观看者佩戴忍者立牌时显示加成后的数值)
            int baseDamage = stack.is(ModItems.MONSTER_LASER_CARD.get()) ? 4
                    : stack.is(ModItems.MONSTER_BRICK_CARD.get()) ? 6
                    : stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) ? 8 : 5;
            int ninjaBonus = event.getEntity() instanceof Player p ? ModAttachments.getKomachiDamageBonus(p) : 0;
            tooltip.add(Component.empty());
            // 组件基础色为灰(普通文本);行内颜色码:数值=黄 §e、时间=蓝 §9
            tooltip.add(Component.translatable(tooltipKey, baseDamage + ninjaBonus)
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        // === 新效果牌(治疗/互动) ===
        if (stack.is(ModItems.CHOCOLATE_CAKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.chocolate_cake")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAMBURGER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.hamburger")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.LUXURY_FEAST.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.luxury_feast")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.YOU_HAVE_I_HAVE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.you_have_i_have")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EXPRESS_DELIVERY.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.express_delivery")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAIQING_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "虚弱印记");
            addSignLines(tooltip, "tooltip.astral_dice.sign.haiqing_active");
            addSignPassiveTitle(tooltip, "幸运星");
            addSignLines(tooltip, "tooltip.astral_dice.sign.haiqing_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FATE_GUIDANCE_CARD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(tt("tooltip.astral_dice.card.fate_guidance_desc")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_saturation")
                    .withStyle(ChatFormatting.GRAY));
            // 联动条目:仅安装相关模组时显示(备注区,紫色,无编号)
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                addSignNoteLines(tooltip, "tooltip.astral_dice.card.fate_curse_mitigation");
            }
            if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
                addSignNoteLines(tooltip, "tooltip.astral_dice.card.fate_spell_mana");
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
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
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
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
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FEN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "运功");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fen_active");
            addSignPassiveTitle(tooltip, "养精蓄锐");
            addSignLines(tooltip, "tooltip.astral_dice.sign.fen_passive");
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.fen_recharge",
                        ModAttachments.getFenRecharge(p),
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.MAX_RECHARGE);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.NANCY_LU_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "远程侵入");
            addSignLines(tooltip, "tooltip.astral_dice.sign.nancy_lu_active");
            addSignPassiveTitle(tooltip, "网络防火墙");
            addSignLines(tooltip, "tooltip.astral_dice.sign.nancy_lu_passive");
            // 最下方显示本立牌攻击力与防御力加成
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.sign.nancy_lu_bonus",
                        com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getAttackBonus(p)
                                + com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getActiveAttackBonus(p),
                        com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.getDefenseBonus(p) * 2);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
    }

}
