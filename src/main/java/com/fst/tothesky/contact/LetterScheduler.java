package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarData;
import com.fst.tothesky.calendar.CalendarEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节日信/生日信投递：每 60 秒检查一次到期的信，交给 {@link ContactMail}。
 *
 * <p><b>三种驱动方式</b>：
 * <ul>
 *   <li>{@code trigger=date}（默认）——收件人与日期写在 json 里，排期键 = 文件名；</li>
 *   <li>{@code trigger=birthday}——收件人与日期都取自日历里 {@code type=birthday} 的活动
 *       （活动名 = 玩家昵称、月-日逐年循环，农历生日按 {@code LunarCalendar} 逐年换算成公历日）。
 *       <b>一个生日信文件服务全服</b>：
 *       当天过生日的每位玩家各投一份，排期按「信件 + 收件人 + 生日」逐人独立记账，
 *       所以改日历里某个生日的日期只影响那一个人；</li>
 *   <li><b>模板信</b>（既无 {@code player} 又无 {@code date}）——自己不排期，只能由**日历节日**的
 *       {@code letter} 字段绑定：该节日当天把信发给**全服每位玩家**（{@link #recipientNames}）。
 *       多个节日可绑同一封信，各按自己的日子发。</li>
 * </ul>
 * <b>被任何节日绑定的信，一律不按自身 trigger 投递</b>（避免同一封信发两遍）：
 * 若它同时还写了 {@code player}/{@code date}/{@code trigger=birthday}，那些设置会被忽略并告警一次。
 *
 * <p><b>配置何时生效</b>：{@code config/tothesky/letters} 只在**服务器启动后的首轮检查**与
 * {@code /tothesky reloadletters}（{@link #reload}）时被读取；平时的 60 秒检查只用已读入的定义，
 * 不碰磁盘（读 {@code usercache.json} 最坏一天一次，见 {@link #recipientNames}）。
 * 所以改完 json 要跑一次命令——顺带也能立刻投出当天该发的信，方便调试。
 *
 * <p><b>命令 vs 日常检查的差别</b>：命令会把「本应在今天投递」的信**重新武装再发一次**
 * （哪怕今天已经投过），日常 60 秒检查则严格按已记录的 {@code nextDue} 判断、绝不重发。
 * 实现见 {@link #deliverDue} 的 {@code rearmToday}。
 *
 * <p><b>为什么投递检查是 60 秒</b>：投递粒度是「天」，检查间隔只需保证跨过午夜后能在
 * **第一分钟内**命中——最坏情况是午夜后 59.9 秒扫到，仍在当天第一分钟内。
 *
 * <p><b>排期算法</b>（状态见 {@link LetterStateData}，键见 {@link #recipientKey}）：
 * <ol>
 *   <li>{@code date} 信的日期字符串变了（或首次见到）→ 按今天重算 {@code nextDueDay}——所以当天写下的
 *       「日期 = 今天」的信会立刻投递，而今天补写的「元旦 01-01」会等到明年元旦；</li>
 *   <li>{@code today >= nextDueDay} → 投递（服务器停机错过日期时这里就补投）；</li>
 *   <li>投递成功 → 循环日期（{@code MM-DD}、生日与节日绑定都是逐年循环）推进到下一次，
 *       一次性日期（{@code YYYY-MM-DD}）置为永不再投；</li>
 *   <li>投递失败（例如款式写错、Contact 缺席、收件人还没进过服）→ <b>保留</b> {@code nextDueDay}，
 *       下一轮重试；失败日志每个排期键每进程只报一次，避免每分钟刷屏。</li>
 * </ol>
 *
 * <p>信件解析失败的告警由 {@link LetterLibrary} 负责（按文件内容缓存，只在文件改动后的首次重载时报一次）。
 * Contact 未安装时整轮跳过，排期一律不动，装上后照常投递。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class LetterScheduler {
    /** 投递检查间隔：60 秒（见类注释：恰好卡在「当天第一分钟」的精度要求内） */
    private static final int CHECK_INTERVAL_TICKS = 20 * 60;

    /** 上次检查用的服务器 tick；负值 = 下一 tick 立即检查 */
    private static long lastCheckTick = -CHECK_INTERVAL_TICKS;
    /** 已报过的问题（投递失败 / 生日活动配错）的排期键（仅本次进程有效，不落盘） */
    private static final Set<String> REPORTED = new HashSet<>();

    /**
     * 当前生效的信件定义；{@code null} = 本场服务器还没读过配置。
     * <p>停服时置回 {@code null}，所以换存档/重开世界会重新读盘（客户端整合服里 mod 实例是活的）。
     */
    @Nullable
    private static List<FestivalLetter> letters;

    /** {@code usercache.json} 的名字缓存（节日绑定信的收件人名单） */
    @Nullable
    private static Set<String> cachedUsercache;
    /** 上面这份缓存对应的日期（epochDay）；跨天重读一次 */
    private static long cachedUsercacheDay = Long.MIN_VALUE;

    private LetterScheduler() {
    }

    /** {@code /tothesky reloadletters} 的结果 */
    public record ReloadResult(int loaded, int delivered, boolean contactAvailable) {
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
        if (letters == null) {
            // 本场服务器首轮：读一次配置。目录与默认文件的生成不依赖 Contact，缺 Contact 时也照样备好
            reloadFromDisk();
        }
        // 日常检查：已投过的不重发（nextDue 已推进）
        deliverDue(server, false);
    }

    /**
     * 重读配置并立刻投递一轮（{@code /tothesky reloadletters}）。
     * <p><b>今天的信会重新投一遍</b>：凡是「本应在今天投递」的信，无论此前是否已经投过，
     * 这次都重新武装并再发一次（见 {@code rearmToday}）——所以命令跑两次就会收到两份，
     * 这是给「改完 json 想立刻看到效果 / 手工补发」用的。
     *
     * <p>必须由服务端线程调用（命令执行天然满足；HTTP 之类的异线程入口需先 {@code server.execute(...)}）。
     */
    public static ReloadResult reload(MinecraftServer server) {
        reloadFromDisk();
        return new ReloadResult(letters.size(), deliverDue(server, true), ContactMail.loaded());
    }

    /** 备好目录与默认文件，然后把目录里的信读进来 */
    private static void reloadFromDisk() {
        LetterLibrary.ensureDefaultFiles();
        letters = LetterLibrary.reload();
    }

    /**
     * 按当前生效的定义投递所有到期的信；返回投递成功数。
     *
     * @param rearmToday 命令重载时为 {@code true}：「本轮该投的日期正好是今天」的信一律重新武装并重发
     *                   （哪怕它的 {@code nextDue} 已经被推进到明年）；日常检查传 {@code false}，
     *                   只按已记录的 {@code nextDue} 判断，绝不重发。
     */
    private static int deliverDue(MinecraftServer server, boolean rearmToday) {
        List<FestivalLetter> current = letters;
        if (current == null) {
            return 0;
        }
        List<CalendarEvent> events = CalendarData.get(server).all();
        Map<String, List<CalendarEvent>> bindings = festivalBindings(events);
        // 配置诊断与 Contact 无关：没装 Contact 也照样把「绑定写错 / 模板没人绑」报出来
        warnConfigIssues(current, bindings);
        if (!ContactMail.loaded()) {
            return 0;
        }
        LetterStateData state = LetterStateData.get(server);
        Set<String> live = new HashSet<>();
        LocalDate today = LocalDate.now();
        int delivered = 0;
        List<String> recipients = null; // 全服名单：只有真有绑定才去枚举
        for (FestivalLetter letter : current) {
            List<CalendarEvent> bound = bindings.get(letter.id());
            if (bound != null) {
                if (recipients == null) {
                    recipients = recipientNames(server);
                }
                for (CalendarEvent festival : bound) {
                    delivered += deliverFestivalLetter(letter, festival, recipients, state, live, today,
                            rearmToday) ? 1 : 0;
                }
            } else if (!letter.template()) {
                if (letter.trigger() == FestivalLetter.Trigger.BIRTHDAY) {
                    for (CalendarEvent event : events) {
                        if (CalendarEvent.TYPE_BIRTHDAY.equals(event.type)) {
                            delivered += deliverBirthdayLetter(letter, event, state, live, today,
                                    rearmToday) ? 1 : 0;
                        }
                    }
                } else {
                    delivered += deliverDateLetter(letter, state, live, today, rearmToday) ? 1 : 0;
                }
            }
        }
        // 目录里已删掉的信、日历里已删掉的生日/绑定，排期状态一并丢掉；日后放回时按新信处理
        state.retain(live);
        return delivered;
    }

    /** 配置级诊断：绑定指向不存在的信、模板信无人绑定、被绑定的信自身排期不生效 */
    private static void warnConfigIssues(List<FestivalLetter> current,
                                         Map<String, List<CalendarEvent>> bindings) {
        warnMissingBoundLetters(bindings, current);
        for (FestivalLetter letter : current) {
            List<CalendarEvent> bound = bindings.get(letter.id());
            if (bound != null) {
                noteOwnScheduleIgnored(letter, bound);
            } else if (letter.template()) {
                warnUnboundTemplate(letter);
            }
        }
    }

    /** {@code trigger=date}：收件人与日期来自 json；返回是否投出 */
    private static boolean deliverDateLetter(FestivalLetter letter, LetterStateData state,
                                             Set<String> live, LocalDate today, boolean rearmToday) {
        String id = letter.id();
        live.add(id);
        long todayEpoch = today.toEpochDay();
        long fresh = letter.nextOccurrenceOn(today);
        String knownSpec = state.spec(id);
        Long due = knownSpec != null && knownSpec.equals(letter.dateSpec())
                ? state.nextDue(id)
                : null;
        if (rearmToday && fresh == todayEpoch) {
            // 命令重载：本应今天投的这封信重新武装（原本可能已投并推进到明年），于是重发一次
            due = todayEpoch;
            state.put(id, letter.dateSpec(), due);
        } else if (due == null) {
            // 首次见到，或 JSON 里的日期被改过：以今天为基准重新排期
            due = fresh;
            state.put(id, letter.dateSpec(), due);
        }
        if (todayEpoch < due) {
            return false;
        }
        String player = letter.player();
        if (!deliver(letter, today, player)) {
            if (REPORTED.add(id)) {
                ToTheSky.LOGGER.warn("[节日信] {} 投递失败（收件人 {}），保留排期待下轮重试", id, player);
            }
            return false;
        }
        REPORTED.remove(id);
        state.put(id, letter.dateSpec(), nextDueAfter(letter, today));
        ToTheSky.LOGGER.info("[节日信] {} 已投递：{} → {}（{}）",
                id, ContactMail.SYSTEM_SENDER, player, letter.type().id());
        return true;
    }

    /**
     * {@code trigger=birthday}：收件人与生日都来自日历里的一条 {@code type=birthday} 活动。
     * <p>排期键含「收件人 + 生日」（见 {@link #recipientKey}），所以同一天过生日的多人各收一份，
     * 同一人的生日日期改了也不会串味。返回是否投出。
     */
    private static boolean deliverBirthdayLetter(FestivalLetter letter, CalendarEvent birthday,
                                                 LetterStateData state, Set<String> live, LocalDate today,
                                                 boolean rearmToday) {
        String name = birthday.name;
        Recurrence recurrence = recurrenceOf(birthday, today);
        if (recurrence == null) {
            warnBadDate("生日信", letter.id(), birthday);
            return false;
        }
        if (!ScheduledMailData.isValidPlayerName(name)) {
            warnBadRecipient("生日信 " + letter.id(), name, recurrence.dayText());
            return false;
        }
        return deliverRecurring(letter, name, recurrence, "生日信", state, live, today, rearmToday);
    }

    /**
     * 日历节日绑定的信（模板信或「被绑定」的独立信）：节日当天发给全服每位玩家。
     * <p>多个节日可绑同一封信，各自按自己的月-日发；同一天被多个节日绑定只算一次
     * （排期键是「信件 + 收件人 + 日期」，天然去重）。
     */
    private static boolean deliverFestivalLetter(FestivalLetter letter, CalendarEvent festival,
                                                 List<String> recipients, LetterStateData state,
                                                 Set<String> live, LocalDate today, boolean rearmToday) {
        Recurrence recurrence = recurrenceOf(festival, today);
        if (recurrence == null) {
            warnBadDate("节日信", letter.id(), festival);
            return false;
        }
        String dayText = recurrence.dayText();
        // 保留「同一天同一封信」的既有记录：名单里暂时没有的人（未进过服/缓存过期）
        // 不该因为这一轮没被枚举到就被当成没发过——否则他回来会再收一份。
        String prefix = letter.id() + '|';
        String suffix = '|' + dayText;
        for (String key : state.ids()) {
            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                live.add(key);
            }
        }
        boolean any = false;
        for (String name : recipients) {
            if (deliverRecurring(letter, name, recurrence, "节日信", state, live, today, rearmToday)) {
                any = true;
            }
        }
        return any;
    }

    /**
     * 给单个收件人投递一封「按日历月-日逐年循环」的信（生日信与节日绑定信共用）：
     * 首次见到按今天排期（已过的今年日期顺延到明年），到期即投，投出后推进到明年。
     * 返回是否投出。
     *
     * @param rearmToday 见 {@link #deliverDue}——命令重载时把「正好是今天」的那一次重新武装
     */
    private static boolean deliverRecurring(FestivalLetter letter, String recipient,
                                            Recurrence recurrence, String kind, LetterStateData state,
                                            Set<String> live, LocalDate today, boolean rearmToday) {
        String dayText = recurrence.dayText();
        String key = recipientKey(letter.id(), recipient, dayText);
        live.add(key);
        long todayEpoch = today.toEpochDay();
        long fresh = recurrence.nextOn();
        Long due = state.nextDue(key);
        if (rearmToday && fresh == todayEpoch) {
            // 命令重载：今天这一份重新武装（可能上周目已投并推进到明年），于是再发一次
            due = todayEpoch;
            state.put(key, dayText, due);
        } else if (due == null) {
            due = fresh;
            state.put(key, dayText, due);
        }
        if (todayEpoch < due) {
            return false;
        }
        if (!deliver(letter, today, recipient)) {
            if (REPORTED.add(key)) {
                ToTheSky.LOGGER.warn("[节日信] {} {} 投递失败（收件人 {}），保留排期待下轮重试",
                        kind, letter.id(), recipient);
            }
            return false;
        }
        REPORTED.remove(key);
        // 逐年循环：今年这份已投出，下一次是明年的这天（从明天起算，避开「今天仍 >= due」）
        state.put(key, dayText, recurrence.nextAfterToday());
        ToTheSky.LOGGER.info("[节日信] {} {} 已投递：{} → {}（{}，{}）",
                kind, letter.id(), ContactMail.SYSTEM_SENDER, recipient, letter.type().id(), dayText);
        return true;
    }

    /** 节日绑定的表：信件 id → 绑定它的节日活动（顺序按日历自身排序，稳定） */
    private static Map<String, List<CalendarEvent>> festivalBindings(List<CalendarEvent> events) {
        Map<String, List<CalendarEvent>> bindings = new LinkedHashMap<>();
        for (CalendarEvent event : events) {
            if (!CalendarEvent.TYPE_FESTIVAL.equals(event.type) || event.letter.isEmpty()) {
                continue;
            }
            bindings.computeIfAbsent(event.letter, key -> new ArrayList<>()).add(event);
        }
        return bindings;
    }

    /** 全服名单：当前在线 ∪ {@code usercache.json} 里的名字 */
    private static List<String> recipientNames(MinecraftServer server) {
        Set<String> online = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getGameProfile().getName());
        }
        return mergeRecipients(online, usercacheNames(server));
    }

    /**
     * 合并在线名单与缓存名单：去重（保持顺序：在线在前）、过滤掉不可能是玩家昵称的名字。
     * <p>非法名字要在这里挡掉，否则会给「某个建号时输入了怪名字的离线玩家」建一堆永远投不出去的排期。
     */
    static List<String> mergeRecipients(Collection<String> online, Collection<String> cached) {
        Set<String> names = new LinkedHashSet<>(online);
        names.addAll(cached);
        names.removeIf(name -> !ScheduledMailData.isValidPlayerName(name));
        return List.copyOf(names);
    }

    /**
     * {@code usercache.json} 里的名字（服务器根目录，MC 自己维护的「进过服的玩家」名单）。
     *
     * <p>不用 {@link net.minecraft.server.players.GameProfileCache#load()}（它正是读这个文件）是因为
     * 它返回的是包私有类型 {@code GameProfileCache.GameProfileInfo}，包外拿不到名字，
     * 只能反射——而生产环境方法名是 SRG 名，反射会在正式服炸。所以这里直接读文件取 {@code name}。
     */
    static Set<String> readUsercache(Path file) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonElement name = element.getAsJsonObject().get("name");
                if (name != null && name.isJsonPrimitive()) {
                    names.add(name.getAsString());
                }
            }
        }
        return names;
    }

    /**
     * 带按天缓存的 {@link #readUsercache}：一天只读一次盘。
     * <p>在线名单每轮都重新取（见 {@link #recipientNames}），所以中途进服的玩家当天就能收到；
     * 兜底名单的滞后只影响「当天首次进服、当天就退服、且退出前未触发自动保存」这种极端情形。
     */
    private static Set<String> usercacheNames(MinecraftServer server) {
        long day = LocalDate.now().toEpochDay();
        Set<String> cached = cachedUsercache;
        if (cached != null && cachedUsercacheDay == day) {
            return cached;
        }
        Set<String> names;
        Path file = new File(server.getServerDirectory(), "usercache.json").toPath();
        try {
            names = readUsercache(file);
        } catch (IOException | RuntimeException e) {
            // 还没有玩家进过服时文件不存在，属常态：每进程只提一次
            if (REPORTED.add("usercache")) {
                ToTheSky.LOGGER.warn("[节日信] 读不到 {}（{}）：节日绑定信的收件人只含当前在线玩家",
                        file, e.getMessage() == null ? e.toString() : e.getMessage());
            }
            names = Set.of();
        }
        cachedUsercache = names;
        cachedUsercacheDay = day;
        return names;
    }

    /** 被节日绑定、但目录里没有这封信：告警一次（多半是文件名写错） */
    private static void warnMissingBoundLetters(Map<String, List<CalendarEvent>> bindings,
                                                List<FestivalLetter> current) {
        if (bindings.isEmpty()) {
            return;
        }
        Set<String> present = new HashSet<>();
        for (FestivalLetter letter : current) {
            present.add(letter.id());
        }
        for (Map.Entry<String, List<CalendarEvent>> entry : bindings.entrySet()) {
            if (present.contains(entry.getKey())) {
                continue;
            }
            for (CalendarEvent festival : entry.getValue()) {
                if (REPORTED.add("bind:" + festival.id + '|' + entry.getKey())) {
                    ToTheSky.LOGGER.warn("[节日信] 节日「{}」绑定的信件 {} 不存在"
                                    + "（应在 {} 里，是文件名去掉 .json），该绑定不会投递",
                            festival.displayTitle(), entry.getKey(), LetterLibrary.dir());
                }
            }
        }
    }

    /** 被绑定的信还有自己的排期设置：那些设置不生效，告警一次免得对着不生效的字段猜 */
    private static void noteOwnScheduleIgnored(FestivalLetter letter, List<CalendarEvent> bound) {
        if (letter.template() || !REPORTED.add("bound:" + letter.id())) {
            return;
        }
        ToTheSky.LOGGER.warn("[节日信] {} 已被节日「{}」绑定，其自身 {} 不生效：收件人与日期改由该节日决定",
                letter.id(), bound.get(0).displayTitle(),
                letter.trigger() == FestivalLetter.Trigger.BIRTHDAY ? "trigger=birthday"
                        : letter.player() != null && letter.dateSpec() != null ? "player/date" : "排期设置");
    }

    /** 模板信没有任何节日绑定：告警一次（多半是日历里忘了填 letter） */
    private static void warnUnboundTemplate(FestivalLetter letter) {
        if (REPORTED.add("template:" + letter.id())) {
            ToTheSky.LOGGER.warn("[节日信] {} 是模板信（没写 player/date），但没有节日绑定它，不会投递"
                    + "（在日历里给某个节日填 letter = {}）", letter.id(), letter.id());
        }
    }

    private static void warnBadDate(String kind, String letterId, CalendarEvent event) {
        if (REPORTED.add("日历:" + event.id)) {
            ToTheSky.LOGGER.warn("[节日信] {} {} 跳过日历里的「{}」：{} 不是合法日期",
                    kind, letterId, event.displayTitle(), event.dateText());
        }
    }

    private static void warnBadRecipient(String what, String name, String dayText) {
        String key = what + '|' + name + '|' + dayText;
        if (REPORTED.add(key)) {
            ToTheSky.LOGGER.warn("[节日信] {} 跳过收件人「{}」（{}）：名字不是合法玩家昵称，无法投递",
                    what, name, dayText);
        }
    }

    /** 按类型投递；返回是否成功交给 ContactMail 的挂号队列 */
    private static boolean deliver(FestivalLetter letter, LocalDate today, String recipient) {
        return switch (letter.type()) {
            case POSTCARD -> ContactMail.sendPostcard(recipient, today, letter.style(),
                    letter.renderText(today, recipient, null));
            case PARCEL -> ContactMail.sendParcel(recipient, today, letter.items());
            // ${item} 只在红包里有效：传内容物进去才会被替换
            case RED_PACKET -> ContactMail.sendRedPacket(recipient, today, letter.items(),
                    letter.renderText(today, recipient, letter.items()));
        };
    }

    /** 投递成功后的下次投递日：循环日期推进到下一年，一次性日期永不再投 */
    private static long nextDueAfter(FestivalLetter letter, LocalDate today) {
        return letter.recurring() ? letter.nextOccurrenceOn(today.plusDays(1)) : Long.MAX_VALUE;
    }

    /**
     * 逐年投递的排期键：{@code 信件名|收件人|日期}（如 {@code birthday|Steve|10-24}、{@code chunjie|Alex|01-01}，
     * 农历生日为 {@code birthday|Steve|农历08-06}）——
     * 一眼能看出这封信是给谁的、哪天发，直接看 {@code tothesky_letters.dat} 也能懂。
     * <p>生日信与节日绑定信共用这套键（都是「信件 + 收件人 + 某个月-日」）：
     * 同一天被多个节日绑定的同一封信只会发一次。
     * <p>分隔符 {@code |} 不会出现在昵称里（昵称白名单见 {@link ScheduledMailData#isValidPlayerName}），
     * 也不会出现在文件名里（Windows 不允许，REST 也挡掉了），故键无歧义。
     * <p>{@link LetterStateData} 的 {@code spec} 记的就是其中的日期（{@code MM-DD} 或 {@code 农历MM-DD}）——它已含在键里，
     * 仅用于让存档自解释；日期改了 = 换键 = 从头排期。
     */
    static String recipientKey(String letterId, String name, String dayText) {
        return letterId + '|' + name + '|' + dayText;
    }

    /**
     * 一封「逐年循环」的信的日期来源：不早于今天的下一次发生 + 从明天起算的下一次 + 排期键里的日期文本。
     * <p>{@code nextOn} 用来判断今天该不该投，{@code nextAfterToday} 用来在投出后推进排期
     * （从明天起算，这样推进后的 {@code nextDue} 一定大于今天）。
     *
     * @return 日期非法（手改存档等）或超出农历表范围时返回 null
     */
    @Nullable
    private static Recurrence recurrenceOf(CalendarEvent event, LocalDate today) {
        long nextOn = event.nextOccurrenceOn(today);
        if (nextOn == Long.MAX_VALUE) {
            return null;
        }
        long nextAfterToday = event.nextOccurrenceOn(today.plusDays(1));
        if (nextAfterToday == Long.MAX_VALUE) {
            return null;
        }
        return new Recurrence(nextOn, nextAfterToday, event.dayText());
    }

    private record Recurrence(long nextOn, long nextAfterToday, String dayText) {
    }

    /**
     * 停服时清掉节流标记、失败记录、已读入的定义与玩家名单缓存。
     * <p>服务器 tick 计数会随重开归零，静态标记若留着旧值（远大于新 tick），
     * {@code now - lastCheckTick} 会长期为负，导致新一场服务器永不检查。
     * <p>{@code letters} 一并置空：换存档/重开世界时必须重新读盘
     * （客户端整合服里本 mod 的类不会被卸载）。
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        lastCheckTick = -CHECK_INTERVAL_TICKS;
        REPORTED.clear();
        letters = null;
        cachedUsercache = null;
        cachedUsercacheDay = Long.MIN_VALUE;
    }
}
