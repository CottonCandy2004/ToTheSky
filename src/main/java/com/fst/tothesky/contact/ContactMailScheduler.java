package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 定时邮件投递：每 20 tick（1 秒）检查一次 {@link ScheduledMailData} 里到期的条目。
 *
 * <p><b>投递时刻</b>：到期条件是「投递日 ≤ 今天」（{@link LocalDate} 的 epochDay 比较），
 * 所以只要服务器在跑，跨过当天 00:00 之后的首次检查（≤1 秒）就是投递时刻——
 * 即「当天的第一分钟」。服务器当晚没开时不清空任务：当天稍后开机，首次检查就会补投，
 * 不会因为停机错过窗口而永久滞留。
 *
 * <p><b>收件人解析</b>：排期里存的是昵称，在这里（投递时刻）才解析成 UUID：
 * <ol>
 *   <li>在线玩家（{@link net.minecraft.server.players.PlayerList#getPlayerByName}，大小写不敏感）；</li>
 *   <li>服务器 usercache（{@link GameProfileCache#get(String)}，只查本地缓存、不发网络请求——
 *       覆盖所有进过服的玩家，含离线模式）。</li>
 * </ol>
 * 都查不到（名字拼错，或该玩家尚未首次进服）则**不消费**该条目，保留到以后重试：
 * 玩家后来首次进服，就会被 usercache 收录，届时自动投递。日志每条只报一次，避免每秒刷屏。
 *
 * <p>投递动作交给 Contact 的挂号队列（{@link ContactMailBridge#deliver}），
 * 与 {@code /contact postcard deliver} 同一条路径：邮箱满则在队列里等，收件人离线则入箱等上线读取。
 * Contact 未安装时同样不消费到期条目，装上后照常投递。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class ContactMailScheduler {
    /** 检查间隔：1 秒，与「投递到当天第一分钟内」的精度要求相称 */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** 上次检查用的服务器 tick；负值 = 下一 tick 立即检查 */
    private static long lastCheckTick = -CHECK_INTERVAL_TICKS;

    private ContactMailScheduler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now - lastCheckTick < CHECK_INTERVAL_TICKS) {
            return;
        }
        lastCheckTick = now;

        ScheduledMailData data = ScheduledMailData.get(server);
        if (data.size() == 0) {
            return;
        }
        if (!ContactMail.loaded()) {
            return;
        }
        List<ScheduledMailData.Entry> due = data.due(LocalDate.now().toEpochDay());
        for (ScheduledMailData.Entry entry : due) {
            UUID target = resolveRecipient(server, entry.recipient());
            if (target == null) {
                if (entry.noteFailure()) {
                    ToTheSky.LOGGER.warn("[往来] 收件人「{}」暂不可解析（未进过服或昵称有误），定时邮件保留待投递",
                            entry.recipient());
                }
                continue;
            }
            try {
                ContactMailBridge.deliver(target, entry.mail());
                data.remove(entry);
                ToTheSky.LOGGER.info("[往来] 定时邮件已投递：{} → {}（物品 {}）",
                        ContactMail.SYSTEM_SENDER, entry.recipient(),
                        entry.mail().getHoverName().getString());
            } catch (Exception e) {
                // 单条失败不阻塞其余邮件，也不移除条目（下轮重试）；
                // 每秒重试一次，故每条只报一次日志，避免刷屏
                if (entry.noteFailure()) {
                    ToTheSky.LOGGER.error("[往来] 定时邮件投递失败，保留待下次重试：{} → {}",
                            ContactMail.SYSTEM_SENDER, entry.recipient(), e);
                }
            }
        }
    }

    /**
     * 昵称 → UUID：先在线玩家，再服务器 usercache。
     * <p>只走本地数据（{@link GameProfileCache#get(String)} 命中 usercache，不做网络查询），
     * 不会在 tick 里阻塞。
     */
    @Nullable
    private static UUID resolveRecipient(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return null;
        }
        return cache.get(name).map(profile -> profile.getId()).orElse(null);
    }

    /**
     * 停服时清掉节流标记。
     * <p>服务器 tick 计数会随重开归零，静态标记若留着旧值（远大于新 tick），
     * {@code now - lastCheckTick} 会长期为负，导致新一场服务器的定时邮件永不检查。
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        lastCheckTick = -CHECK_INTERVAL_TICKS;
    }
}
