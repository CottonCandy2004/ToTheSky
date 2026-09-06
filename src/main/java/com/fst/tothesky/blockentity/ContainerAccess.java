package com.fst.tothesky.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 容器交互工具：通过 Forge Capability 系统统一访问上方/下方容器，兼容所有实现了 IItemHandler 的方块实体。
 * 替代 kjs 的 {@code event.block.getInventory()} 抽象——后者依赖 KubeJS 的 Inventory 包装，且用 try/catch 兜底。
 *
 * 1.20.1 适配：能力查询用 ForgeCapabilities.ItemHandler.BLOCK
 * （1.21 NeoForge 是 Capabilities.ItemHandler.BLOCK + level.getCapability(pos, side)）。
 * 1.20.1 的 Level.getCapability 无 pos 重载，需经 BlockEntity 获取。
 */
public final class ContainerAccess {

    private ContainerAccess() {}

    /**
     * 获取指定方向上的 IItemHandler。非容器方块返回 null（干净降级，无 try/catch）。
     */
    @Nullable
    public static IItemHandler getItemHandler(Level level, BlockPos pos, Direction side) {
        BlockPos target = pos.relative(side);
        var blockEntity = level.getBlockEntity(target);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite())
                .orElse(null);
    }

    /**
     * 检查 IItemHandler 中是否有至少一个非空槽位。
     */
    public static boolean hasItems(@Nullable IItemHandler handler) {
        if (handler == null) return false;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 收集所有非空槽位中的物品列表副本（不修改原槽位）。
     */
    public static java.util.List<ItemStack> collectNonEmpty(@Nullable IItemHandler handler) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        if (handler == null) return list;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack);
        }
        return list;
    }

    /**
     * 从指定槽位提取 1 个物品（simulate=false 原子操作）。
     * 返回提取出的物品（可能为空），调用方负责处理后续逻辑。
     */
    public static ItemStack extractOne(IItemHandler handler, int slot) {
        return handler.extractItem(slot, 1, false);
    }

    /**
     * 向容器插入物品，返回剩余未插入的部分（非空表示未全部插入）。
     */
    public static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        return remaining;
    }
}