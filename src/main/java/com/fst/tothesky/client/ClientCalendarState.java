package com.fst.tothesky.client;

import com.fst.tothesky.network.CalendarDataPacket;

/**
 * 客户端日历状态：最新一包数据 + 打开中的屏幕引用。
 * 仅客户端加载；被网络层经 DistExecutor 引用，专用服不触及本类。
 */
public final class ClientCalendarState {
    /** 最新数据（初始 null，收到首包前 GUI 不存在） */
    public static volatile CalendarDataPacket latest;

    private ClientCalendarState() {
    }

    public static void handle(CalendarDataPacket packet) {
        latest = packet;
        com.fst.tothesky.client.gui.calendar.CalendarScreen.onData(packet);
    }
}