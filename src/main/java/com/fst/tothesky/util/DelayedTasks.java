package com.fst.tothesky.util;

import com.fst.tothesky.ToTheSky;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简服务端延迟任务调度器（用于幻翼虾仁的礼花连发等场景）。
 * 替代 KubeJS 的 server.scheduleInTicks。
 *
 * 注意：不能在迭代任务列表的过程中执行会调用 {@link #schedule} 的动作
 * （如危险派对的每 tick 击鼓传花），否则 ArrayList 迭代器会抛
 * ConcurrentModificationException。这里把「取出到期任务」与「执行」分成两步，
 * 执行阶段新增的任务只会追加到 TASKS（其 runAt 在未来），不影响本轮处理。
 *
 * 1.20.1 适配：TickEvent.ServerTickEvent 无 server 引用，
 * 用 ServerLifecycleHooks.getCurrentServer() 获取。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        // 先把本轮到期任务搬到 due，避免在执行阶段迭代 TASKS 时被 schedule 并发修改
        List<Task> due = new ArrayList<>();
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            Task task = TASKS.get(i);
            if (task.runAt() <= now) {
                due.add(task);
                TASKS.remove(i);
            }
        }
        // 逆序执行，保持原调度先后顺序
        for (int i = due.size() - 1; i >= 0; i--) {
            try {
                due.get(i).action().run();
            } catch (Exception e) {
                ToTheSky.LOGGER.error("Delayed task failed", e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TASKS.clear();
    }
}