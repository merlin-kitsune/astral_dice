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

@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class AnvilUpgradeHandler {
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // 升星白名单覆盖全部 13 种骰子(基础/黄金/玻璃/下界岩/钻石/绿宝石/黑曜石/诡异/紫晶/下界合金/绯红/末影/下界之星)
        if (left.is(ModItems.DICE.get()) || left.is(ModItems.GOLDEN_DICE.get()) || left.is(ModItems.DIAMOND_DICE.get())
                || left.is(ModItems.NETHERITE_DICE.get()) || left.is(ModItems.EMERALD_DICE.get())
                || left.is(ModItems.GLASS_DICE.get()) || left.is(ModItems.NETHERRACK_DICE.get())
                || left.is(ModItems.OBSIDIAN_DICE.get()) || left.is(ModItems.WEIRD_DICE.get())
                || left.is(ModItems.AMETHYST_DICE.get()) || left.is(ModItems.CRIMSON_DICE.get())
                || left.is(ModItems.ENDER_DICE.get()) || left.is(ModItems.NETHER_STAR_DICE.get())) {
            if (!right.is(ModItems.STAR_COIN.get())) return;
            WeaponEnhancement enhancement = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(left, WeaponEnhancement.EMPTY);
            if (enhancement.starLevel() >= 3) return;
            int req = switch (enhancement.starLevel()) {
                case 0 -> 15;
                case 1 -> 20;
                case 2 -> 25;
                default -> -1;
            };
            if (right.getCount() < req) return;
            ItemStack output = left.copy();
            ModDataComponents.WEAPON_ENHANCEMENT.set(output, new WeaponEnhancement(
                            enhancement.usedCost(),
                            GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                            enhancement.usedDefenseCost(),
                            GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                            enhancement.starLevel() + 1,
                            // ⚠️ appliedStones 必须**原样透传**(不重建):临时牌(绿洲女王 nardis)的
                            // temporary 分量存在 AppliedStone 里,重建/拷贝时丢标记 = 该牌到期清不掉。
                            enhancement.appliedStones()
                    ));
            event.setOutput(output);
            event.setMaterialCost(req);
            event.setCost(10);
            return;
        }
    }

    // ============ 立牌 tooltip 统一格式辅助 ============
    // 格式模板:
    //   <按键提示>
    //
    //   主动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //   被动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //
    //   <备注信息(紫色,无符号)>
    //
    //   <立牌计数器>
    // 颜色约定:见 ModTooltipHandler 类头「物品 tooltip 统一染色规则」
    //   (数值=黄 §e、时间=蓝 §9、效果条目「名 (时间)」整段蓝 §9;§c 红色语义保留、§r/§f 例外)

    // 主动技能按键提示:置于 tooltip 最上方独立一行,并在末尾追加一个空行
}
