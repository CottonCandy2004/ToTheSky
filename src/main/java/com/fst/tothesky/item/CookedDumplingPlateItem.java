package com.fst.tothesky.item;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 一盘熟饺子的物品形态：放置成功时把物品 NBT 里的内容物原样搬进方块实体的 {@code data} 标签。
 *
 * <p>没有这一步，放下的一盘饺子会丢失整盘馅料与厨师名（物品被消耗、方块状态无 NBT）。
 * 脚本侧原本靠 rightClicked 里补邻位方块实体的做法对不可堆叠物品失效（放置时主手已空），
 * 故在 Java 侧可靠地完成转移。
 *
 * <p>兼容两种历史物品 NBT：
 * <ul>
 *   <li>脚本产出的 {@code filling} + {@code author}（挂在根标签）；</li>
 *   <li>过渡版 1.20.1 移植产出的 {@code dumpling_plate}（内含 filling/author 的复合标签）。</li>
 * </ul>
 * 只搬运这两个键，不把 {@code display} 等展示信息写进方块实体。
 */
public class CookedDumplingPlateItem extends BlockItem {
    private static final String TAG_FILLING = "filling";
    private static final String TAG_AUTHOR = "author";
    /** 过渡版 1.20.1 移植（79efe9b 之前）的物品键 */
    private static final String TAG_PLATE_LEGACY = "dumpling_plate";

    public CookedDumplingPlateItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        // super.place 会消耗物品栈，先取快照
        CompoundTag itemTag = context.getItemInHand().getTag();
        CompoundTag contents = contentsOf(itemTag == null ? null : itemTag.copy());

        InteractionResult result = super.place(context);
        if (result.consumesAction() && contents != null && !context.getLevel().isClientSide) {
            BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            if (blockEntity instanceof DumplingPlateBlockEntity plate) {
                CompoundTag target = plate.data();
                for (String key : new String[]{TAG_FILLING, TAG_AUTHOR}) {
                    if (contents.contains(key)) {
                        target.put(key, contents.get(key).copy());
                    }
                }
                plate.setChanged();
            }
        }
        return result;
    }

    /** 取出承载内容物的复合标签；无内容物返回 null */
    private static CompoundTag contentsOf(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (tag.contains(TAG_PLATE_LEGACY, Tag.TAG_COMPOUND)) {
            return tag.getCompound(TAG_PLATE_LEGACY);
        }
        return tag.contains(TAG_FILLING) || tag.contains(TAG_AUTHOR) ? tag : null;
    }
}
