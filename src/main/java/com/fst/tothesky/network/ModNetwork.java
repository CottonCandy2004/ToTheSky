package com.fst.tothesky.network;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarData;
import com.fst.tothesky.calendar.CalendarEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历网络层：单一 S2C 包 {@link CalendarDataPacket}。
 * open=true 打开屏幕；false 仅刷新已打开屏幕（REST 变更后广播）。
 * 包携带全量事件（量级小），客户端本地翻月无需再请求。
 */
public final class ModNetwork {
    private static final String PROTOCOL = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ToTheSky.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(CalendarDataPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CalendarDataPacket::encode)
                .decoder(CalendarDataPacket::decode)
                .consumerMainThread(CalendarDataPacket::handle)
                .add();
    }

    /** 服务端组包发送：当前现实年月 + 今天 + 全量事件 */
    public static void sendCalendar(ServerPlayer player, boolean open) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        CalendarData data = CalendarData.get(server);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                CalendarDataPacket.create(open, today.getYear(), today.getMonthValue(),
                        today.getDayOfMonth(), data.all()));
    }

    /** 广播到所有在线玩家（REST 变更后刷新打开中的 GUI） */
    public static void broadcastCalendar(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        CalendarData data = CalendarData.get(server);
        CalendarDataPacket packet = CalendarDataPacket.create(false, today.getYear(),
                today.getMonthValue(), today.getDayOfMonth(), data.all());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}