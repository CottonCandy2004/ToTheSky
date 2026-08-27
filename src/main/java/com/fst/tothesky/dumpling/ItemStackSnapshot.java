package com.fst.tothesky.dumpling;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 数据组件用的单物品栈快照。ItemStack 自身没有实现 equals/hashCode，
 * 直接作为组件值会被 NeoForge 拒绝（CommonHooks.validateComponent），
 * 因此像 Create 的 SandPaperItemComponent 一样包一层 record。
 * 相等性按物品+组件判定，忽略数量（约定数量恒为 1）。
 */
public record ItemStackSnapshot(ItemStack stack) {
    public static final Codec<ItemStackSnapshot> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(ItemStackSnapshot::new, ItemStackSnapshot::stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStackSnapshot> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(ItemStackSnapshot::new, ItemStackSnapshot::stack);

    public ItemStackSnapshot {
        stack = stack.copy(); // 组件值约定不可变，防御外部继续修改原栈
    }

    /** 取出快照内的物品栈；快照为 null 时返回 EMPTY（替代 getOrDefault 写法） */
    public static ItemStack unwrap(@Nullable ItemStackSnapshot snapshot) {
        return snapshot == null ? ItemStack.EMPTY : snapshot.stack();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ItemStackSnapshot other
                && ItemStack.isSameItemSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }
}
