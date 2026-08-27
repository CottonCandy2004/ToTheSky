package com.fst.tothesky.dumpling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 一盘饺子的内容物：8 份馅料与对应的厨师名，按下标一一对应。
 * 对应旧脚本里 item/blockEntity NBT 上的 filling/author 列表。
 */
public record DumplingPlateContents(List<ItemStack> fillings, List<String> authors) {
    public static final int SIZE = 8;

    public static final Codec<DumplingPlateContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.listOf().fieldOf("filling").forGetter(DumplingPlateContents::fillings),
            Codec.STRING.listOf().fieldOf("author").forGetter(DumplingPlateContents::authors)
    ).apply(instance, DumplingPlateContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DumplingPlateContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), DumplingPlateContents::fillings,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DumplingPlateContents::authors,
            DumplingPlateContents::new);

    public DumplingPlateContents {
        fillings = List.copyOf(fillings);
        authors = List.copyOf(authors);
        if (fillings.size() != authors.size()) {
            throw new IllegalArgumentException("fillings 与 authors 数量不一致");
        }
    }

    /** 是否至少有一份有效馅料（全空则不允许下锅） */
    public boolean hasAnyFilling() {
        return fillings.stream().anyMatch(s -> !s.isEmpty());
    }

    public ItemStack fillingAt(int index) {
        return index >= 0 && index < fillings.size() ? fillings.get(index) : ItemStack.EMPTY;
    }

    public String authorAt(int index) {
        return index >= 0 && index < authors.size() ? authors.get(index) : "Unknown";
    }

    // NeoForge 组件校验与 ItemStack.matches 依赖内容相等性；
    // record 默认 equals 走 List.equals → ItemStack 引用比较，两个副本永不相等（曾导致厨锅会话即刻作废）

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DumplingPlateContents other)) {
            return false;
        }
        if (!authors.equals(other.authors) || fillings.size() != other.fillings.size()) {
            return false;
        }
        for (int i = 0; i < fillings.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(fillings.get(i), other.fillings.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = authors.hashCode();
        for (ItemStack filling : fillings) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(filling);
        }
        return hash;
    }
}
