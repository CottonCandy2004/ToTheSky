package com.fst.tothesky.item;

import net.minecraft.world.item.Item;

/**
 * 空白乐谱。
 *
 * <p>对应 kubejs 的 {@code empty_music_sheet}（副手成书 + 主手空白乐谱右键 → 生成乐谱物品），
 * 转换逻辑见 {@link com.fst.tothesky.event.InstrumentsEvents}。
 */
public class EmptyMusicSheetItem extends Item {
    public EmptyMusicSheetItem(Properties properties) {
        super(properties);
    }
}
