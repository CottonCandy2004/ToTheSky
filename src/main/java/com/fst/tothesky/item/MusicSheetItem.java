package com.fst.tothesky.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 乐谱（记录了一首歌的 allNodes 数据）。
 *
 * <p>对应 kubejs 的 {@code music_sheet}（注册时带 .glow(true) 附魔光效）：
 * 主手右键 → 学习歌曲（写入 config/musicSheets/&lt;玩家名&gt;/），逻辑见
 * {@link com.fst.tothesky.event.InstrumentsEvents}。
 */
public class MusicSheetItem extends Item {
    public MusicSheetItem(Properties properties) {
        super(properties);
    }

    /** 原 kubejs 注册 .glow(true) → 物品始终带附魔光效 */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
