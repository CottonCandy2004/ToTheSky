package com.fst.tothesky.network;

import com.fst.tothesky.calendar.CalendarEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C：日历数据（open 标志 + 年 + 月 + 今天的 day-of-month + 全量事件）。
 * 客户端处理经 DistExecutor 隔离，专用服安全。
 */
public final class CalendarDataPacket {
    public final boolean open;
    public final int year;
    public final int month;
    public final int todayDay;
    public final List<CalendarEvent> events;

    private CalendarDataPacket(boolean open, int year, int month, int todayDay,
                               List<CalendarEvent> events) {
        this.open = open;
        this.year = year;
        this.month = month;
        this.todayDay = todayDay;
        this.events = events;
    }

    public static CalendarDataPacket create(boolean open, int year, int month, int todayDay,
                                            List<CalendarEvent> events) {
        return new CalendarDataPacket(open, year, month, todayDay, List.copyOf(events));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeInt(year);
        buf.writeInt(month);
        buf.writeInt(todayDay);
        buf.writeVarInt(events.size());
        for (CalendarEvent event : events) {
            buf.writeUUID(event.id);
            buf.writeUtf(event.name, 256);
            buf.writeUtf(event.type, 32);
            buf.writeVarInt(event.month);
            buf.writeVarInt(event.day);
            buf.writeUtf(event.iconType, 32);
            buf.writeUtf(event.iconId, 256);
            buf.writeUtf(event.description, 1024);
        }
    }

    public static CalendarDataPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int year = buf.readInt();
        int month = buf.readInt();
        int todayDay = buf.readInt();
        int size = buf.readVarInt();
        List<CalendarEvent> events = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(256);
            String type = buf.readUtf(32);
            int m = buf.readVarInt();
            int d = buf.readVarInt();
            String iconType = buf.readUtf(32);
            String iconId = buf.readUtf(256);
            String description = buf.readUtf(1024);
            events.add(CalendarEvent.create(id, name, type, m, d, iconType, iconId, description));
        }
        return new CalendarDataPacket(open, year, month, todayDay, events);
    }

    public static void handle(CalendarDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.fst.tothesky.client.ClientCalendarState.handle(packet)));
        ctx.get().setPacketHandled(true);
    }
}