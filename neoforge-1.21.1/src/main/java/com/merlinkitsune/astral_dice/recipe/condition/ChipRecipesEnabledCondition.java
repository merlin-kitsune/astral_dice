package com.merlinkitsune.astral_dice.recipe.condition;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 筹码配方开关条件(需求 1):挂了本条件的配方**仅当**公共配置 {@code enable_chip_recipes} 为 true
 * 时才会被加载(取值见 {@link ModCommonConfig#ENABLE_CHIP_RECIPES})。
 *
 * <p>条件码(condition codec)以 id {@code astral_dice:chip_recipes_enabled} 注册到 NeoForge 的
 * 条件序列化器注册表 —— 实物口径(NeoForge 21.1.235 源码,见自检证据):该注册表是
 * {@code NeoForgeRegistries.CONDITION_SERIALIZERS},元素类型为 {@code MapCodec<? extends ICondition>},
 * 注册表键为 {@code NeoForgeRegistries.Keys.CONDITION_CODECS}
 * (⚠️ 21.1.235 已**没有** {@code IConditionSerializer} 类型 / {@code Keys.CONDITION_SERIALIZERS} 键,
 * 与旧文档口径不同,以实物为准)。
 *
 * <p>注册方式:本类自带 {@code @EventBusSubscriber(bus = MOD)} 注册钩子,经 {@link RegisterEvent} 直接把
 * 条件码塞进该注册表 —— 与 {@code DeferredRegister#register(IEventBus)} 走的是**同一条底层路径**
 * ({@code RegisterEvent#register}),但**无需改动主入口类 {@code AstralDiceMod}**,以免越出本次任务的
 * 「允许修改文件清单」边界。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ChipRecipesEnabledCondition implements ICondition {

    /** 注册 id(路径段 {@code chip_recipes_enabled})。 */
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_recipes_enabled");

    /** 无参条件单例(JSON 里写 {"type":"astral_dice:chip_recipes_enabled"} 即可)。 */
    public static final ChipRecipesEnabledCondition INSTANCE = new ChipRecipesEnabledCondition();

    public static final MapCodec<ChipRecipesEnabledCondition> CODEC = MapCodec.unit(INSTANCE).stable();

    private ChipRecipesEnabledCondition() {
    }

    @Override
    public boolean test(IContext context) {
        return ModCommonConfig.ENABLE_CHIP_RECIPES.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    /** 把本条件的码注册进条件序列化器注册表(与 DeferredRegister 等价的注册路径)。 */
    @SubscribeEvent
    public static void onRegisterConditionCodecs(RegisterEvent event) {
        event.register(NeoForgeRegistries.CONDITION_SERIALIZERS.key(),
                helper -> helper.register(ID, CODEC));
    }

    @Override
    public String toString() {
        return "chip_recipes_enabled";
    }
}
