package com.merlinkitsune.astral_dice.component;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
 * 由 {@link AttachedDataKey} 按名读写。持久化随玩家 NBT;死亡不复制(与 1.21 附件默认行为一致)。
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

    /** 维度切换时保留数据(死亡不保留,对应 1.21 附件无 copyOnDeath 的行为)。 */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        event.getOriginal().reviveCaps();
        AstralData oldData = event.getOriginal().getCapability(ModCapabilities.ASTRAL_DATA).orElse(null);
        if (oldData != null) {
            event.getEntity().getCapability(ModCapabilities.ASTRAL_DATA).ifPresent(newData ->
                    newData.deserializeNBT(oldData.serializeNBT()));
        }
        event.getOriginal().invalidateCaps();
    }
}
