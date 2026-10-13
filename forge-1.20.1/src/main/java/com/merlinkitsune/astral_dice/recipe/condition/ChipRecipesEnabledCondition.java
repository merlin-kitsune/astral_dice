package com.merlinkitsune.astral_dice.recipe.condition;

import com.google.gson.JsonObject;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * 筹码配方开关条件(需求 1):挂了本条件的配方**仅当**公共配置 {@code enable_chip_recipes} 为 true
 * 时才会被加载(取值见 {@link ModCommonConfig#ENABLE_CHIP_RECIPES})。
 *
 * <p>条件 id 为 {@code astral_dice:chip_recipes_enabled}({@link #getID()})—— 配方 JSON 里写
 * {@code "conditions":[{"type":"astral_dice:chip_recipes_enabled"}]} 即可。
 *
 * <p><b>平台差异(forge-1.20.1,实物口径见自检证据)</b>:Forge 47.4.10 **没有** NeoForge 1.21.1 的
 * {@code NeoForgeRegistries.CONDITION_SERIALIZERS} 注册表 —— 条件序列化器不是注册表项,而是经
 * {@link CraftingHelper#register(IConditionSerializer)} 存进 {@code CraftingHelper} 内部 map;
 * 且条件 JSON 的键是**顶层** {@code "conditions"}(RecipeManager / ConditionalAdvancement 都按此键判定),
 * **不是** {@code "forge:conditions"}(那是 RegistryDataLoader 对数据驱动注册表项用的键)。
 * 故本类改为把注册收进一个 {@link #register()} 方法,由 {@link AstralDiceMod} 构造器同步调用,与 ForgeMod 官方同款 ——
 * ⚠️ **不能**挂到 {@code FMLCommonSetupEvent} / 类 {@code static} 块 / MOD 总线注册表事件上:forge datagen 的数据生成路径
 * ({@code DatagenModLoader#begin} → {@code GatherDataEvent} → {@code RecipeProvider#run})**不发**那些事件,
 * 类 {@code static} 块的加载时机也不在数据序列化之前;{@code AstralDiceMod} 构造器在 runtime 与 datagen 的
 * {@code gatherAndInitializeMods} 里都会执行,早于配方序列化,曾因此报
 * {@code Unknown condition type: astral_dice:chip_recipes_enabled}。
 */
public final class ChipRecipesEnabledCondition implements ICondition {

    /** 条件 id(路径段 {@code chip_recipes_enabled})。 */
    public static final ResourceLocation ID =
            new ResourceLocation(AstralDiceMod.MODID, "chip_recipes_enabled");

    /** 无参条件单例(JSON 里写 {"type":"astral_dice:chip_recipes_enabled"} 即可)。 */
    public static final ChipRecipesEnabledCondition INSTANCE = new ChipRecipesEnabledCondition();

    private ChipRecipesEnabledCondition() {
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return ModCommonConfig.ENABLE_CHIP_RECIPES.get();
    }

    /**
     * 注册条件序列化器 —— 由 {@link AstralDiceMod} 构造器同步调用(与 {@code ModItems.ITEMS.register} 等同批),
     * 构造器在 runtime 与 datagen 的 {@code gatherAndInitializeMods} 里都会执行,早于配方序列化。
     * ⚠️ 不用类 {@code static} 块 / {@code FMLCommonSetupEvent} / MOD 总线注册表事件:forge datagen **不发**那些事件,
     * 类 {@code static} 块的加载时机也不可靠。
     */
    public static void register() {
        CraftingHelper.register(new Serializer());
    }

    @Override
    public String toString() {
        return "chip_recipes_enabled";
    }

    /** 无参条件的序列化器(write/read 均为空实现 —— 本条件不携带参数)。 */
    public static final class Serializer implements IConditionSerializer<ChipRecipesEnabledCondition> {
        @Override
        public void write(JsonObject json, ChipRecipesEnabledCondition value) {
            // 无参条件:不写任何字段
        }

        @Override
        public ChipRecipesEnabledCondition read(JsonObject json) {
            return INSTANCE;
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
