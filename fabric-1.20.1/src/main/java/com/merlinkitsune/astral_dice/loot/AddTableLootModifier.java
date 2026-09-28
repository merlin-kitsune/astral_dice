package com.merlinkitsune.astral_dice.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * 全局战利品修饰符:**把另一张战利品表(字段 {@code table})的结果追加到目标表**。
 *
 * <p><b>为什么本模组自带一份:</b>NeoForge(1.21.1)内置 {@code neoforge:add_table},而
 * **Forge 1.20.1-47.4.10 的 {@code net.minecraftforge.common.loot} 里没有 {@code GlobalLootModifierSerializers},
 * 也没有 {@code AddTableLootModifier}** —— 全 jar 不含 {@code add_table} 这个注册名(已逐字节核验)。
 * 因此 1.20.1 侧原先照抄 {@code "type": "forge:add_table"} 的 13 个战利品修饰符
 * **永远无法解码**(实机日志:`Could not decode GlobalLootModifier with json id astral_dice:star_plate_*
 * - error: Unknown registry key in ResourceKey[minecraft:root / forge:global_loot_modifier_serializers]: forge:add_table`),
 * 星盘的战利品注入形同不存在。
 *
 * <p>这里按 Forge 提供的 {@link LootModifier} 基类补上等价实现,注册名 {@code astral_dice:add_table}
 * (自带命名空间,不与其它模组冲突),json 结构与 1.21.1 侧逐字一致(只有 {@code type} 的命名空间不同)。
 *
 * <p>子表**刻意使用 Raw 版本**生成:附加表自身不再跑一遍全局战利品修饰符,否则下游修饰符
 * 会把它改两次(与 NeoForge 内置实现的取舍一致)。
 */
public class AddTableLootModifier extends LootModifier {
    /** json: {@code {"type":"astral_dice:add_table","conditions":[...],"table":"命名空间:表 id"}} */
    public static final Codec<AddTableLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ResourceLocation.CODEC.fieldOf("table").forGetter(AddTableLootModifier::table))
            .apply(instance, AddTableLootModifier::new));

    private final ResourceLocation table;

    public AddTableLootModifier(LootItemCondition[] conditions, ResourceLocation table) {
        super(conditions);
        this.table = table;
    }

    public ResourceLocation table() {
        return this.table;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        MinecraftServer server = context.getLevel().getServer();
        if (server == null) return generatedLoot;
        LootTable extra = server.getLootData().getLootTable(this.table);
        if (extra == null || extra == LootTable.EMPTY) return generatedLoot;
        // 附加表用 Raw:不再跑全局战利品修饰符(见类注释)
        extra.getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
        return generatedLoot;
    }

    @NotNull
    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return AstralLootModifiers.ADD_TABLE.get();
    }
}
