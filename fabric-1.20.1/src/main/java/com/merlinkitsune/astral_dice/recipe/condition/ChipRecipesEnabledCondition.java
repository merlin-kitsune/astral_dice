package com.merlinkitsune.astral_dice.recipe.condition;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * 筹码配方开关条件(需求 1):挂了本条件的配方**仅当**公共配置 {@code enable_chip_recipes} 为 true
 * 时才会被加载(forge 原样见 {@code forge-1.20.1/.../recipe/condition/ChipRecipesEnabledCondition.java})。
 *
 * <p><b>⚠️ 平台差异(fabric-1.20.1):本类目前是「占位重写」,不是 1:1 移植。</b>
 * Forge / NeoForge 有「配方条件」基础设施({@code ICondition} / {@code IConditionSerializer} +
 * forge 47 的 {@code CraftingHelper.register(...)} 或 neo 的 {@code NeoForgeRegistries.CONDITION_SERIALIZERS}),
 * 配方 JSON 可以写
 * {@code "conditions":[{"type":"astral_dice:chip_recipes_enabled"}]} 由加载器在解析期求值。
 * <b>Fabric 1.20.1 没有任何对应机制</b>(原版 RecipeManager 不支持外部条件;唯一近似物
 * {@code ResourceCondition} 是 <b>1.21.2+</b> 才有的,本线不可用)⇒ 本类无法实现任何加载期判定。
 *
 * <p><b>保留面(与 forge 同名同包,便于落线时单点替换)</b>:条件 id {@code astral_dice:chip_recipes_enabled}、
 * 单例 {@link #INSTANCE}、{@link #isEnabled()} 判定出口、{@link #register()} 注册出口。
 *
 * <p><b>现状</b>:两线 {@code data/astral_dice/recipes/} 下**没有任何** JSON 使用该条件
 * (全量 grep {@code "conditions"} = 0 命中),故本占位不影响任何配方的加载行为。
 */
public final class ChipRecipesEnabledCondition {

    /** 条件 id(路径段 {@code chip_recipes_enabled},与 forge 逐字一致)。 */
    public static final ResourceLocation ID =
            new ResourceLocation(AstralDiceMod.MODID, "chip_recipes_enabled");

    /** 无参条件单例(JSON 里写 {"type":"astral_dice:chip_recipes_enabled"} 即可)。 */
    public static final ChipRecipesEnabledCondition INSTANCE = new ChipRecipesEnabledCondition();

    private ChipRecipesEnabledCondition() {
    }

    /** 条件 id(对应 forge 的 {@code ICondition#getID()})。 */
    public ResourceLocation getID() {
        return ID;
    }

    /**
     * 条件求值(对应 forge 的 {@code ICondition#test(IContext)})。
     *
     * <p>B1-CHIPS-WIRING 已接线(取值见 {@link ModCommonConfig#ENABLE_CHIP_RECIPES}):
     * fabric {@code config/ModCommonConfig.java} 的 {@code enable_chip_recipes},默认 {@code false},
     * 与 forge {@code config/ModCommonConfig.java:55} 同名 / 同默认 / 同键名。
     * <p>⚠️ 接线只解决「取值来源」,**不构成加载期求值**:本方法目前无调用方,
     * 配方 JSON 的 {@code "conditions"} 在 fabric 1.20.1 仍不会被求值(见类注释与 {@link #register()})。
     */
    public boolean isEnabled() {
        return ModCommonConfig.ENABLE_CHIP_RECIPES.get();
    }

    /**
     * 注册条件序列化器(对应 forge 的 {@code CraftingHelper.register(new Serializer())})。
     *
     * <p>TODO(CHIP-RECIPES-MECHANISM): fabric 1.20.1 无配方条件注册面(见类注释)⇒ 本方法保持**空实现**,
     * 只作为接线 seam 保留。真要在 fabric 上实现「配置开关整批筹码配方」,可选的落法(择一,均需改配方数据):
     * ① 启动期按配置把 {@code data/astral_dice/recipes/*.json} 从资源包内**过滤掉**(fabric 侧需自建
     *    {@code ResourceManager} 装饰器或改用数据包覆盖);② 改成「运行时标签门控」——配方恒加载,
     *    但在 {@code ChipRecipesEnabledCondition#isEnabled() == false} 时让产出物不可用。
     * 现状下两线都没有配方使用本条件,故本方法为空不影响行为。
     */
    public static void register() {
        // TODO(CHIP-RECIPES-MECHANISM): fabric 无配方条件注册面,占位空实现(见方法注释)
    }

    @Override
    public String toString() {
        return "chip_recipes_enabled";
    }
}
