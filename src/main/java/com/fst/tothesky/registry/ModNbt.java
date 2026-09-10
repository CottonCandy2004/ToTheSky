package com.fst.tothesky.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * 1.20.1 的 NBT 数据键与读写工具（替代 1.21 的数据组件注册）。
 */
public final class ModNbt {
    /** 玩家死亡回溯记录点（存于 player.getPersistentData()） */
    public static final String REWIND_POS = "rewind_pos";

    private ModNbt() {
    }

    // ---------------- 死亡回溯点（玩家 persistentData） ----------------

    public static void setRewindPos(net.minecraft.world.entity.player.Player player, GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", pos.dimension().location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(pos.pos()));
        player.getPersistentData().put(REWIND_POS, tag);
    }

    public static GlobalPos getRewindPos(net.minecraft.world.entity.player.Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(REWIND_POS);
        if (!tag.contains("dim")) {
            return GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO);
        }
        // 1.20.1 按 ResourceKey<Level> 反查维度
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(tag.getString("dim")));
        return GlobalPos.of(key, NbtUtils.readBlockPos(tag.getCompound("pos")));
    }
}
