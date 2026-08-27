package com.fst.tothesky.util;

import com.fst.tothesky.ToTheSky;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 极简服务端延迟任务调度器（用于幻翼虾仁的礼花连发等场景）。
 * 替代 KubeJS 的 server.scheduleInTicks。
 */
@EventBusSubscriber(modid = ToTheSky.MODID)
public final class DelayedTasks {
    private record Task(long runAt, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private DelayedTasks() {
    }

    /** 必须在服务端线程调用（食物/交互回调天然满足） */
    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + delayTicks, action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (task.runAt() <= now) {
                it.remove();
                try {
                    task.action().run();
                } catch (Exception e) {
                    ToTheSky.LOGGER.error("Delayed task failed", e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TASKS.clear();
    }
}
