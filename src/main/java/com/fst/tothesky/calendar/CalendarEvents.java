package com.fst.tothesky.calendar;

import com.fst.tothesky.ToTheSky;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 日历生命周期：ServerStarted 预热 CalendarData（缺失则建空数据、并与崩溃镜像合并）。
 * <p>HTTP（管理页与 REST）不在这里起——它归 {@code web.WebApiServer}，日历只是它挂的一个域。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class CalendarEvents {

    private CalendarEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 预热 SavedData（缺失则创建空数据，顺带把 world/calendar_events.json 镜像并进来）
        MinecraftServer server = event.getServer();
        CalendarData.get(server);
    }
}