package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.astral_dice.api.combat.AttackPowerModifier;
import com.merlinkitsune.astral_dice.api.combat.DefensePowerModifier;
import com.merlinkitsune.astral_dice.api.combat.DiceCombatFactor;
import com.merlinkitsune.astral_dice.api.combat.ExtraDamageModifier;
import com.merlinkitsune.astral_dice.api.combat.SpellDamageMatcher;
import com.merlinkitsune.astral_dice.api.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.api.combat.VictimDamageModifier;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeHandler;
import com.merlinkitsune.astral_dice.combat.CombatApiAdapters;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeTargetsAdapter;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.economy.StarCoinCurrency;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import net.minecraftforge.registries.RegistryObject;
import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventRegistry;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/** forge 平台实现（2026-11-18 门面桥接：类玩家注册 / 立牌激活 / 骰子祝福）。 */
public final class ForgeAstralDicePlatform implements AstralDicePlatform {

    @Override
    public String id() {
        return "forge";
    }

    @Override
    public void registerPlayerLike(String namespace, String path, PlayerLikeHandler handler) {
        PlayerLikeTargets.register(new ResourceLocation(namespace, path), new PlayerLikeTargetsAdapter(handler));
    }

    @Override
    public boolean activateSign(ItemStack stack, LivingEntity holder) {
        if (stack == null || holder == null) {
            return false;
        }
        if (!(stack.getItem() instanceof BaseSignItem)) {
            return false;
        }
        return BaseSignItem.activateFor(holder);
    }

    @Override
    public boolean hasDiceBlessing(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.DICE_BLESSING.get());
    }

    // ------------------------------------------------------ B2 战斗注册口

    @Override
    public void registerAttackModifier(AttackPowerModifier modifier) {
        CombatApiAdapters.registerAttackModifier(modifier);
    }

    @Override
    public void registerDefenseModifier(DefensePowerModifier modifier) {
        CombatApiAdapters.registerDefenseModifier(modifier);
    }

    @Override
    public void registerExtraDamageModifier(ExtraDamageModifier modifier) {
        CombatApiAdapters.registerExtraDamageModifier(modifier);
    }

    @Override
    public void registerVictimDamageModifier(VictimDamageModifier modifier) {
        CombatApiAdapters.registerVictimDamageModifier(modifier);
    }

    @Override
    public void registerDiceCombatFactor(DiceCombatFactor modifier) {
        CombatApiAdapters.registerDiceCombatFactor(modifier);
    }

    @Override
    public void registerSpellDamageMatcher(SpellDamageMatcher modifier) {
        CombatApiAdapters.registerSpellDamageMatcher(modifier);
    }

    // ------------------------------------------------------------- B3 效果 / 星界事件

    @Override
    public int astralEffectLevel(LivingEntity entity, String effectId) {
        MobEffect effect = astralEffect(effectId);
        MobEffectInstance instance = entity == null || effect == null ? null : entity.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    @Override
    public int astralEffectRemainingTicks(LivingEntity entity, String effectId) {
        MobEffect effect = astralEffect(effectId);
        MobEffectInstance instance = entity == null || effect == null ? null : entity.getEffect(effect);
        return instance == null ? 0 : instance.getDuration();
    }

    @Override
    public boolean applyAstralEffect(LivingEntity entity, String effectId, int durationTicks, int amplifier) {
        MobEffect effect = astralEffect(effectId);
        if (entity == null || effect == null || durationTicks <= 0) {
            return false;
        }
        entity.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
        return true;
    }

    @Override
    public Set<String> astralEffectIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (RegistryObject<MobEffect> holder : ModEffects.ALL) {
            ids.add(holder.getId().toString());
        }
        return ids;
    }

    @Override
    public AstralEvent findAstralEvent(String eventId) {
        ResourceLocation id = ResourceLocation.tryParse(eventId);
        return id == null ? null : AstralEventRegistry.get(id).orElse(null);
    }

    @Override
    public boolean triggerAstralEvent(Player triggerer, Player tierOwner, String eventId, AstralEventTrigger source) {
        ResourceLocation id = ResourceLocation.tryParse(eventId);
        if (triggerer == null || id == null) {
            return false;
        }
        AstralEventTrigger effective = source == null ? AstralEventTrigger.CUSTOM : source;
        return tierOwner == null
                ? AstralEventDispatcher.trigger(triggerer, id, effective)
                : AstralEventDispatcher.trigger(triggerer, tierOwner, id, effective);
    }

    private static MobEffect astralEffect(String effectId) {
        ResourceLocation id = ResourceLocation.tryParse(effectId);
        if (id == null) {
            return null;
        }
        for (RegistryObject<MobEffect> candidate : ModEffects.ALL) {
            if (candidate.getId().equals(id)) {
                return candidate.get();
            }
        }
        return null;
    }

    // ------------------------------------------------------------- B4 经济识别 / 发放 / 配置只读

    @Override
    public long starCoinValue(ItemStack stack) {
        return StarCoinCurrency.valueOf(stack);
    }

    @Override
    public long depositCarriedStarCoins(Player player) {
        return StarCoinCurrency.depositAllFromInventory(player);
    }

    @Override
    public boolean giveItemToPlayer(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.level().isClientSide()) {
            return false;
        }
        ResourceConversion.giveItem(player, stack);
        return true;
    }

    @Override
    public boolean walletFeatureEnabled() {
        return StarCoinCurrency.isWalletEnabled();
    }

    @Override
    public boolean autoDepositOnObtain() {
        return StarCoinCurrency.isAutoDepositEnabled();
    }

    @Override
    public boolean firearmDamageAllowed() {
        return ModCommonConfig.ALLOW_FIREARM_DAMAGE.get();
    }

    @Override
    public int actionBarDurationTicks() {
        return ModCommonConfig.ACTIONBAR_DURATION_TICKS;
    }

    @Override
    public int actionBarFadeTicks() {
        return ModCommonConfig.ACTIONBAR_FADE_TICKS;
    }

    // ------------------------------------------------------------- B5 客户端面 / 界面入口

    @Override
    public boolean openCardInventory(ServerPlayer player) {
        return ModMenuTypes.openCardInventory(player);
    }
}

