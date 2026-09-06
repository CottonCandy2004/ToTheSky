package com.fst.tothesky.dumpling;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一盘饺子的内容物：8 份馅料与对应的厨师名，按下标一一对应。
 * 对应旧脚本里 item/blockEntity NBT 上的 filling/author 列表。
 *
 * 1.20.1 无数据组件：save/load 直接走 NBT
 * （filling: ListTag[CompoundTag]，author: ListTag[StringTag]）。
 */
public record DumplingPlateContents(List<ItemStack> fillings, List<String> authors) {
    public static final int SIZE = 8;

    public DumplingPlateContents {
        fillings = List.copyOf(fillings);
        authors = List.copyOf(authors);
        if (fillings.size() != authors.size()) {
            throw new IllegalArgumentException("fillings 与 authors 数量不一致");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag fillingList = new ListTag();
        for (ItemStack filling : fillings) {
            fillingList.add(filling.save(new CompoundTag()));
        }
        ListTag authorList = new ListTag();
        for (String author : authors) {
            authorList.add(StringTag.valueOf(author));
        }
        tag.put("filling", fillingList);
        tag.put("author", authorList);
        return tag;
    }

    public static DumplingPlateContents load(CompoundTag tag) {
        List<ItemStack> fillings = new ArrayList<>();
        List<String> authors = new ArrayList<>();
        ListTag fillingList = tag.getList("filling", Tag.TAG_COMPOUND);
        for (int i = 0; i < fillingList.size(); i++) {
            fillings.add(ItemStack.of(fillingList.getCompound(i)));
        }
        ListTag authorList = tag.getList("author", Tag.TAG_STRING);
        for (int i = 0; i < authorList.size(); i++) {
            authors.add(authorList.getString(i));
        }
        return new DumplingPlateContents(fillings, authors);
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

    // ItemStack 比较依赖 NBT 相等性；record 默认 equals 走 List.equals →
    // ItemStack 引用比较，两个副本永不相等（曾导致厨锅会话即刻作废）

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DumplingPlateContents other)) {
            return false;
        }
        if (!authors.equals(other.authors) || fillings.size() != other.fillings.size()) {
            return false;
        }
        for (int i = 0; i < fillings.size(); i++) {
            if (!ItemStack.isSameItemSameTags(fillings.get(i), other.fillings.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = authors.hashCode();
        for (ItemStack filling : fillings) {
            hash = 31 * hash + (filling.isEmpty() ? 0 : filling.getItem().hashCode());
        }
        return hash;
    }
}