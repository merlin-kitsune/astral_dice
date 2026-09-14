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
     * ({@code rin_pages} / {@code komachi_damage_bonus}),与 1.21.1 侧
     * {@code AttachmentType.Builder#copyOnDeath()} 的键集合一一对应;
     * 其余键与 1.21 附件默认行为一致——死亡不复制。
     *
     * <p>注意:死亡清理({@code LivingDeathEvent})在**旧实体**上执行且刻意不清除这两个键,
     * 因此此处仍能从旧数据中读到值;若将来在死亡清理里加了清除调用,本保留逻辑会失效。
     */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        AstralData oldData = event.getOriginal().getCapability(ModCapabilities.ASTRAL_DATA).orElse(null);
        if (oldData != null) {
            if (event.isWasDeath()) {
                String[] kept = {
                        ModAttachments.RIN_PAGES.name(),
                        ModAttachments.KOMACHI_DAMAGE_BONUS.name()
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
