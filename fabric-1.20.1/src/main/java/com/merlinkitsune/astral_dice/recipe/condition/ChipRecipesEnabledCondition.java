package com.merlinkitsune.astral_dice.recipe.condition;

import com.google.gson.JsonObject;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.ResourceLocation;

/**
 * 筹码配方开关条件(需求 1):挂了本条件的配方**仅当**公共配置 {@code enable_chip_recipes} 为 true
 * 时才会被加载(forge 原样见 {@code forge-1.20.1/.../recipe/condition/ChipRecipesEnabledCondition.java})。
 *
 * <h2>Fabric 1.20.1 的真实机制(2026-11-02 订正)</h2>
 * 本类早期注释曾断言「Fabric 1.20.1 没有任何对应机制 / ResourceCondition 是 1.21.2+ 才有」
 * —— **该断言是错的**,现按实读字节码订正。Fabric 侧走 <b>fabric-resource-conditions-api-v1</b>
 * (随 {@code fabric-api} 聚合包 jar-in-jar 自动加载,运行期无需新增 depends):
 * <ul>
 *   <li>数据 JSON 顶层写
 *       {@code "fabric:load_conditions": [{"condition": "astral_dice:chip_recipes_enabled"}]}
 *       —— 注意字段名是 {@code condition}(fabric),不是 forge/neo 的 {@code type};</li>
 *   <li>求值发生在资源重载的 apply 前置阶段({@code JsonDataLoaderMixin} +
 *       {@code SinglePreparationResourceReloaderMixin} 注入 {@code SimplePreparableReloadListener})
 *       ⇒ <b>作用域不只是配方</b>:所有 {@code SimpleJsonResourceReloadListener} 类型的数据
 *       (配方、advancement、战利品表、标签…)都吃这个条件;</li>
 *   <li>datagen 侧由 {@code FabricRecipeProvider#withConditions} 把条件挂到 {@code FinishedRecipe} 实例上,
 *       {@code FabricRecipeProvider#run} 再把<b>同一份条件</b>同时写进 recipe json 与 advancement json
 *       ⇒ advancement 与配方天然同进同出,不存在"只关一半"的错配。</li>
 * </ul>
 *
 * <h2>与 forge / neoforge 的 JSON 形态差异</h2>
 * forge 线:{@code "conditions":[{"type":"astral_dice:chip_recipes_enabled"}]};
 * fabric 线:{@code "fabric:load_conditions":[{"condition":"astral_dice:chip_recipes_enabled"}]}。
 * 字段名与值键都不同 ⇒ <b>不要追求四线 JSON 逐字节一致</b>,
 * 一致性的判据只能是「同一个 id 集合」。
 *
 * <p><b>保留面(与 forge 同名同包,便于落线时单点替换)</b>:条件 id {@code astral_dice:chip_recipes_enabled}、
 * 单例 {@link #INSTANCE}、{@link #isEnabled()} 判定出口、{@link #register()} 注册出口。
 *
 * <p><b>现状</b>:{@code data/astral_dice/recipes} 与 {@code data/astral_dice/advancements} 下
 * 67 条 {@code *_chip}.json 均带该条件(由 {@code ModRecipeProvider#buildRecipes} 按 id 后缀路由产出);
 * {@code blank_chip_duplicate} 等其余配方的 id 不以 {@code _chip} 结尾 ⇒ 保持无条件,与三条对照线一致。
 */
public final class ChipRecipesEnabledCondition implements ConditionJsonProvider {

    /** 条件 id(路径段 {@code chip_recipes_enabled},与 forge 逐字一致)。 */
    public static final ResourceLocation ID =
            new ResourceLocation(AstralDiceMod.MODID, "chip_recipes_enabled");

    /** 无参条件单例(JSON 里写 {"condition":"astral_dice:chip_recipes_enabled"} 即可)。 */
    public static final ChipRecipesEnabledCondition INSTANCE = new ChipRecipesEnabledCondition();

    private ChipRecipesEnabledCondition() {
    }

    /** 条件 id(对应 forge 的 {@code ICondition#getID()})。 */
    public ResourceLocation getID() {
        return getConditionId();
    }

    /**
     * 条件 id(Fabric 侧出口,{@link ConditionJsonProvider#toJson()} 把本值写进 JSON 的 {@code condition} 字段)。
     */
    @Override
    public ResourceLocation getConditionId() {
        return ID;
    }

    /**
     * 条件参数(本条件是「无参条件」⇒ 故意留空)。
     *
     * <p>留空的直接后果:写出的 JSON 只有
     * {@code {"condition":"astral_dice:chip_recipes_enabled"}},
     * 与 forge 线的 {@code {"type":"astral_dice:chip_recipes_enabled"}} 在「有无额外字段」这一点上等价。
     */
    @Override
    public void writeParameters(JsonObject json) {
        // 无参数条件:不写任何额外字段
    }

    /**
     * 条件求值(对应 forge 的 {@code ICondition#test(IContext)})。
     *
     * <p>调用方 = {@link #register()} 注册进 {@link ResourceConditions} 的 predicate,
     * 由资源重载期逐条目求值。取值见 {@link ModCommonConfig#ENABLE_CHIP_RECIPES}:
     * 配置键 {@code enable_chip_recipes},默认 {@code false},与 forge {@code config/ModCommonConfig.java:55}
     * 同名 / 同默认 / 同键名。
     * <p>⚠️ 语义提醒:{@code ConfigValue#get()} 读的是**启动期加载进内存**的字段(本线配置 shim 不支持热重载)
     * ⇒ 运行期改配置文件必须**重启服务端**;{@code /reload} 会重新求值条件,但取到的仍是旧值。
     */
    public boolean isEnabled() {
        return ModCommonConfig.ENABLE_CHIP_RECIPES.get();
    }

    /**
     * 注册条件(对应 forge 的 {@code CraftingHelper.register(new Serializer())})。
     *
     * <p>必须在**任何数据包加载之前**完成({@code AstralDiceMod#onInitialize} 内调用,客户端与专用服务器两侧都执行)
     * —— 否则 67 + 67 个带条件的 JSON 会因为 {@code Unknown condition} 被 {@code objectMatchesConditions}
     * 的 fail-closed 分支**静默丢弃**(只有一条 WARN)。
     *
     * <p>幂等:{@link ResourceConditions#register} 在重复注册时抛 {@code IllegalArgumentException}
     * (字节码里是 {@code Map.put(...) != null} 即抛),故先查 {@code get(ID) == null} 再注册。
     */
    public static void register() {
        // ⚠️ 幂等守卫:用 ResourceConditions.get(ID) == null 判定(而不是自持 boolean)。
        //    两者对「重复调用」都成立,但前者直接观察到注册表现状。
        if (ResourceConditions.get(ID) != null) {
            return;
        }
        ResourceConditions.register(ID, json -> INSTANCE.isEnabled());
    }

    @Override
    public String toString() {
        return "chip_recipes_enabled";
    }
}
