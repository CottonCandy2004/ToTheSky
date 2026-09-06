package com.fst.tothesky.item;

import net.minecraft.world.item.Item;

/**
 * 乐器（吉他 / 电子琴 / 电子鼓机）。
 *
 * <p>移植自 RiaFST 4 的 kubejs 乐器系统（server_scripts/feature/instruments_server.js）。
 * 原版以物品 tag {@code kubejs:instruments} 判定乐器，这里改为 {@code instanceof InstrumentItem}。
 * 全部交互行为集中在 {@link com.fst.tothesky.event.InstrumentsEvents}（右键切换模式/演奏、
 * 左键加入/开始合奏等），本类本身只是一个「乐器」标记，不覆写任何行为。
 */
public class InstrumentItem extends Item {
    public InstrumentItem(Properties properties) {
        super(properties);
    }
}
