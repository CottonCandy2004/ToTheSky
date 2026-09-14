package com.fst.tothesky.item;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.registry.ModNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 一盘熟饺子的物品形态：放置成功时把物品 NBT 里的内容物原样搬进方块实体的 {@code data} 标签。
 *
 * <p>没有这一步，放下的一盘饺子会丢失整盘馅料与厨师名（物品被消耗、方块状态无 NBT）。
 * 脚本侧原本靠 rightClicked 里补邻位方块实体的做法对不可堆叠物品失效（放置时主手已空），
 * 故在 Java 侧可靠地完成转移。
 *
 * <p>挂 {@link #updateCustomBlockEntityTag} 而不是自己包一层 {@code place()}：
 * 该钩子由 {@code BlockItem.place} 在方块真正放置成功之后、以**放置位置**调用，
 * 省掉自己推算落点（右键面、可替换方块）的麻烦，也不会在放置失败时误写。
 * 仍然先调 {@code super} 保留原版 {@code BlockEntityTag} 的处理。
 *
 * <p>键的兼容分支见 {@link ModNbt#plateContents}：新格式是挂在物品根标签上的
 * {@code filling} + {@code author}，过渡版 1.20.1 移植则包在 {@code dumpling_plate} 里。
 * 只搬运这两个键，不把 {@code display} 等展示信息写进方块实体。
 */
public class CookedDumplingPlateItem extends BlockItem {
    public CookedDumplingPlateItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        boolean handled = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        CompoundTag contents = ModNbt.plateContents(stack.getTag());
        if (contents == null) {
            return handled;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DumplingPlateBlockEntity plate)) {
            return handled;
        }
        CompoundTag target = plate.data();
        for (String key : new String[]{ModNbt.DUMPLING_FILLING, ModNbt.DUMPLING_AUTHOR}) {
            if (contents.contains(key)) {
                target.put(key, contents.get(key).copy());
            }
        }
        plate.setChanged();
        return true;
    }
}
