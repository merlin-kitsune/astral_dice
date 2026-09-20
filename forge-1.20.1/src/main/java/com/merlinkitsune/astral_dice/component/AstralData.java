package com.merlinkitsune.astral_dice.component;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 玩家附件数据承载(1.20.1):单一 Capability,内部为 NBT compound,
 * 由 {@link AttachedDataKey} 按名读写。持久化随玩家 NBT;维度切换复制全部数据,
 * 死亡重生**只复制显式标记为随死亡保留的键**(见 {@link #onPlayerClone}),其余与 1.21 附件默认行为一致。
 */
public class AstralData implements INBTSerializable<CompoundTag> {
    private CompoundTag persistent = new CompoundTag();
    private final CompoundTag transientStore = new CompoundTag();

    CompoundTag persistentStore() {
        return persistent;
    }

    CompoundTag transientStore() {
        return transientStore;
    }

    @Override
    public CompoundTag serializeNBT() {
        return persistent.copy();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        persistent = tag.copy();
    }

    /** Capability 提供者:挂接到玩家实体,负责 NBT 持久化与惰性解析。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final AstralData data = new AstralData();
        private final LazyOptional<AstralData> optional = LazyOptional.of(() -> data);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == ModCapabilities.ASTRAL_DATA ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.deserializeNBT(tag);
        }
    }

    /**
     * 维度切换:复制全部数据。死亡重生:只复制**随死亡保留**的键
     * ({@code rin_pages} / {@code komachi_damage_bonus} / {@code guide_book_given} /
     * {@code teru_huguang_layers} / {@code teru_equip_watermark} / {@code mamushi_awakening}),
     * 与 1.21.1 侧 {@code AttachmentType.Builder#copyOnDeath()} 的键集合一一对应;
     * 其余键与 1.21 附件默认行为一致——死亡不复制。
     *
     * <p>注意:死亡清理({@code LivingDeathEvent})在**旧实体**上执行且刻意不清除这些键,
     * 因此此处仍能从旧数据中读到值;若将来在死亡清理里加了清除调用,本保留逻辑会失效。
     */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        AstralData oldData = event.getOriginal().getCapability(ModCapabilities.ASTRAL_DATA).orElse(null);
        if (oldData != null) {
            if (event.isWasDeath()) {
                String[] kept = {
                        ModAttachments.RIN_PAGES.name(),
                        ModAttachments.KOMACHI_DAMAGE_BONUS.name(),
                        // 2026-09-15 用户裁决:赠书守卫必须随死亡保留,否则重登会再发一本(对应 1.21.1 的 .copyOnDeath())
                        ModAttachments.GUIDE_BOOK_GIVEN.name(),
                        // 2026-09-27 教主立牌(teru):狐光层数需求上跨死亡保留;装备水位是防刷守卫,
                        // 若死亡归 0 就能靠「死一次 → 再装备一次」重新领取同装备张数的一份层数。
                        // 两者在 1.21.1 侧都是 AttachmentType.Builder#copyOnDeath() 键,此处一一对应。
                        ModAttachments.TERU_HUGUANG_LAYERS.name(),
                        ModAttachments.TERU_EQUIP_WATERMARK.name(),
                        // 2026-09-27 蛟龙立牌(mamushi):觉醒层数需求为「死亡不重置、只有卸下立牌才归零」
                        // (规格 §2.5)。与 RIN_PAGES 同构:死亡时立牌从饰品槽掉出,Curios 的 tick 轮询
                        // 会先触发 onUnequip → clearSignData 清零,**早于**克隆复制 ⇒ 除本白名单外
                        // 还需要 DeathPreservedBonuses 的第 3 槽位暂存/回写(两层缺一不可)。
                        ModAttachments.MAMUSHI_AWAKENING.name()
                };
                event.getEntity().getCapability(ModCapabilities.ASTRAL_DATA).ifPresent(newData -> {
                    CompoundTag src = oldData.persistentStore();
                    CompoundTag dst = newData.persistentStore();
                    for (String key : kept) {
                        Tag tag = src.get(key);
                        if (tag != null) {
                            dst.put(key, tag.copy());
                        }
                    }
                });
            } else {
                event.getEntity().getCapability(ModCapabilities.ASTRAL_DATA).ifPresent(newData ->
                        newData.deserializeNBT(oldData.serializeNBT()));
            }
        }
        event.getOriginal().invalidateCaps();
    }
}
