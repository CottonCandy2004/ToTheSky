package com.fst.tothesky.calendar;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.http.CalendarHttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 日历生命周期：ServerStarted 初始化 SavedData + 启动 HTTP；
 * ServerStopping 拒绝新请求；ServerStopped 关闭 HTTP。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class CalendarEvents {

    private static volatile CalendarHttpServer httpServer;

    private CalendarEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 预热 SavedData（缺失则创建空数据）
        MinecraftServer server = event.getServer();
        CalendarData.get(server);
        httpServer = CalendarHttpServer.start(server);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 不立刻关 socket（HTTP 线程可能正在处理），先拒绝新请求
        // 实际停止在 ServerStopped（见下）
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CalendarHttpServer host = httpServer;
        httpServer = null;
        if (host != null) {
            host.stop();
        }
    }
}