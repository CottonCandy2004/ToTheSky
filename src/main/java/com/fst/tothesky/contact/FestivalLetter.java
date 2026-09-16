package com.fst.tothesky.contact;

import com.fst.tothesky.ToTheSky;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条节日信的定义——{@code config/tothesky/letters/*.json} 解析后的不可变结果
 * （解析见 {@link LetterLibrary}）。字段全部经过校验，拿到实例即可直接投递。
 *
 * <p><b>JSON 格式</b>（一个文件 = 一封信；未知字段忽略）：
 * <pre>{@code
 * {
 *   "enabled": true,                                        // 可选，默认 true；false = 不投递
 *   "trigger": "date",                                      // 可选，默认 date；birthday = 生日信（见下）
 *   "type": "postcard",                                     // 必填：postcard / parcel / red_packet
 *   "player": "Steve",                                      // 独立信必填：收件人昵称（与 date 成对）
 *   "date": "10-24",                                        // 独立信必填：MM-DD = 每年该日；YYYY-MM-DD = 只投一次
 *   "style": "contact:new_year_2023",                       // type=postcard 必填：明信片款式
 *   "items": [{"item": "minecraft:cake", "count": 3}],      // type=parcel/red_packet 必填，count 默认 1
 *   "text": "祝${player}生日快乐！"                           // postcard/red_packet 可选：正文 / 红包祝福语
 * }
 * }</pre>
 *
 * <p><b>两种形态</b>——由「有没有 {@code player} + {@code date}」决定：
 * <ul>
 *   <li><b>独立信</b>（{@code trigger=date} 且两个字段齐全）——按自己写的日期、发给写好的收件人；</li>
 *   <li><b>模板信</b>（两个字段都省略）——自己不排期，**只由绑定了它的日历节日投递**
 *       （节日的 {@code letter} 字段指向本文件；当天发给全服每位玩家，见 {@code calendar.CalendarEvent}）。
 *       被绑定的信一律不按自身 trigger 投递，故没有人收双份。</li>
 * </ul>
 * 只写其中一个（{@code player} 或 {@code date}）视为配置错误，整封跳过。
 *
 * <p><b>{@code trigger}</b>决定「谁、什么时候收」：
 * <ul>
 *   <li>{@link Trigger#DATE}（默认）——收件人与日期写在文件里（{@code player} + {@code date}），
 *       适合节日信、活动信；</li>
 *   <li>{@link Trigger#BIRTHDAY}——收件人与日期都取自日历里 {@code type=birthday} 的活动
 *       （活动名 = 玩家昵称，按「月-日」逐年循环，见 {@code calendar.CalendarData}）：
 *       文件里只写「送什么」，{@code player} / {@code date} 写了也会被忽略。
 *       同一个文件对**每个**当天过生日的玩家各投一份（一封生日信服务全服，见 {@code birthday.json}）。</li>
 * </ul>
 *
 * <p>字段名与三类邮件一一对应：明信片 = 样式 + 正文，包裹 = 内容物（Contact 的包裹没有正文），
 * 红包 = 内容物 + 祝福语。
 *
 * <p><b>占位符</b>（只在 {@code text} 里生效，投递当天替换）：
 * <ul>
 *   <li>{@value #PLACEHOLDER_PLAYER} → 收件人昵称（生日信就是当天过生日的那个人），
 *       如 {@code 祝${player}生日快乐！} → {@code 祝Steve生日快乐！}；</li>
 *   <li>{@value #PLACEHOLDER_DATE} → 投递当天的日期，格式 {@code yyyy-MM-dd}；</li>
 *   <li>{@value #PLACEHOLDER_ITEM} → 内容物清单（{@code 物品显示名×数量}，多项用 {@code 、} 连接），
 *       <b>只在红包里有效</b>——包裹没有正文可写，明信片没有内容物，这两类里该占位符原样保留。
 *       名字就是物品的显示名 {@link ItemStack#getHoverName()}（自定义名也算），
 *       所以专用服务器上会是服务端语言的叫法（没有语言包时为英文）。</li>
 * </ul>
 *
 * <p><b>日期语义</b>（{@link Trigger#DATE} 的信）：{@code MM-DD} 每年循环，{@code YYYY-MM-DD} 只投一次
 * （投递后不再触发）；两者都在「投递日 00:00 后的首次检查」投递。
 * 这里只做日期计算，何时检查、如何投递见 {@link LetterScheduler}。
 */
public final class FestivalLetter {
    /** 收件人占位符 → 被送的玩家昵称 */
    public static final String PLACEHOLDER_PLAYER = "${player}";
    /** 日期占位符 → 投递当天的日期（{@code yyyy-MM-dd}） */
    public static final String PLACEHOLDER_DATE = "${date}";
    /** 内容物占位符 → 包含的物品（仅红包有效） */
    public static final String PLACEHOLDER_ITEM = "${item}";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 邮件类型（JSON 的 {@code type}） */
    public enum Type {
        POSTCARD("postcard"),
        PARCEL("parcel"),
        RED_PACKET("red_packet");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 未知类型返回 null（由调用方报错） */
        @Nullable
        static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 投递时机（JSON 的 {@code trigger}）：决定「谁、什么时候收」。
     */
    public enum Trigger {
        /** 收件人与日期写在文件里（{@code player} + {@code date}） */
        DATE("date"),
        /** 收件人与日期取自日历里 {@code type=birthday} 的活动（活动名 = 玩家昵称） */
        BIRTHDAY("birthday");

        private final String id;

        Trigger(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 未知取值返回 null（由调用方报错） */
        @Nullable
        static Trigger byId(String id) {
            for (Trigger trigger : values()) {
                if (trigger.id.equals(id)) {
                    return trigger;
                }
            }
            return null;
        }
    }

    /** 文件名去掉 {@code .json}；改文件名 = 换了一封信（排期状态从头算） */
    private final String id;
    private final Type type;
    private final Trigger trigger;
    /** {@link Trigger#DATE} 的收件人；生日信为 null（收件人来自日历） */
    @Nullable
    private final String player;
    /** JSON 里的原始日期字符串，用于判断「日期被改过」；生日信为 null */
    @Nullable
    private final String dateSpec;
    /** 生日信为 null（日期来自日历） */
    @Nullable
    private final DateSpec date;
    /** 仅 {@link Type#POSTCARD} */
    @Nullable
    private final ResourceLocation style;
    /** 仅 {@link Type#PARCEL} / {@link Type#RED_PACKET}，其它类型为空列表 */
    private final List<ItemStack> items;
    /** 仅 {@link Type#POSTCARD} / {@link Type#RED_PACKET}；红包里即祝福语 */
    @Nullable
    private final String text;

    private FestivalLetter(String id, Type type, Trigger trigger, @Nullable String player,
                           @Nullable String dateSpec, @Nullable DateSpec date,
                           @Nullable ResourceLocation style, List<ItemStack> items, @Nullable String text) {
        this.id = id;
        this.type = type;
        this.trigger = trigger;
        this.player = player;
        this.dateSpec = dateSpec;
        this.date = date;
        this.style = style;
        this.items = items;
        this.text = text;
    }

    public String id() {
        return id;
    }

    public Type type() {
        return type;
    }

    /** 投递时机：日期写在文件里，还是取自日历生日 */
    public Trigger trigger() {
        return trigger;
    }

    /** 仅 {@link Trigger#DATE} 非 null */
    @Nullable
    public String player() {
        return player;
    }

    /** 文件里的日期字符串；仅 {@link Trigger#DATE} 非 null */
    @Nullable
    public String dateSpec() {
        return dateSpec;
    }

    /** 排期是否每年循环（{@code MM-DD}）；生日信恒为 false（循环由日历驱动） */
    public boolean recurring() {
        return date != null && date.recurring();
    }

    /**
     * 是否**模板信**：既没有 {@code player} 也没有 {@code date}，自己不排期，
     * 只能由绑定了本文件的日历节日投递（见类注释的「两种形态」）。
     * <p>{@code trigger=birthday} 的信不算模板（它由生日活动驱动）。
     */
    public boolean template() {
        return trigger == Trigger.DATE && player == null && dateSpec == null;
    }

    /** 仅明信片非 null */
    @Nullable
    public ResourceLocation style() {
        return style;
    }

    /** 内容物（包裹/红包；其它类型为空列表） */
    public List<ItemStack> items() {
        return items;
    }

    /** 正文 / 红包祝福语（可为 null） */
    @Nullable
    public String text() {
        return text;
    }

    /**
     * 不早于 {@code from} 的首次投递日（epochDay）；生日信没有日期，返回 {@link Long#MAX_VALUE}（不由日期驱动）。
     */
    public long nextOccurrenceOn(LocalDate from) {
        return date == null ? Long.MAX_VALUE : date.nextOn(from);
    }

    /**
     * 下一个不早于 {@code from} 的「月-日」（epochDay）——生日信用：日历里的生日同样是逐年循环的月-日。
     * <p>闰日（{@code 02-29}）在平年顺延到 2 月 28 日，所以这种生日每年都投，不会隔三年才发一次。
     */
    public static long nextRecurringOn(MonthDay day, LocalDate from) {
        LocalDate candidate = day.atYear(from.getYear());
        if (candidate.isBefore(from)) {
            candidate = day.atYear(from.getYear() + 1);
        }
        return candidate.toEpochDay();
    }

    /**
     * 渲染正文：替换 {@value #PLACEHOLDER_PLAYER}、{@value #PLACEHOLDER_DATE}；
     * {@code forItems} 非 null 时再替换 {@value #PLACEHOLDER_ITEM}（即只有红包传内容物）。
     * <p>物品清单最后替换，物品名里万一有别的占位符也不会被二次替换。
     *
     * @param recipient 收件人昵称（日期触发的信就是 {@link #player()}，生日信是当天过生日的那位）
     */
    public String renderText(LocalDate today, String recipient, @Nullable List<ItemStack> forItems) {
        String rendered = (text == null ? "" : text)
                .replace(PLACEHOLDER_PLAYER, recipient)
                .replace(PLACEHOLDER_DATE, DATE_FORMAT.format(today));
        if (forItems != null) {
            rendered = rendered.replace(PLACEHOLDER_ITEM, describeItems(forItems));
        }
        return rendered;
    }

    /**
     * 内容物的可读清单：{@code 钻石×8、蛋糕×1}，名字取物品的显示名
     * （{@link ItemStack#getHoverName()}，含自定义名）。信里那句话由此在投递当天定死。
     */
    public static String describeItems(List<ItemStack> items) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : items) {
            if (builder.length() > 0) {
                builder.append('、');
            }
            builder.append(stack.getHoverName().getString()).append('×').append(stack.getCount());
        }
        return builder.toString();
    }

    // ==================== 解析 ====================

    /**
     * 解析一个 JSON 文件。任何一处不合法都返回 null 并打一条 WARN——
     * 宁可不投递，也不要把半成品信件发出去。
     */
    @Nullable
    static FestivalLetter parse(String id, JsonObject json) {
        if (Boolean.FALSE.equals(bool(json, "enabled"))) {
            ToTheSky.LOGGER.info("[节日信] {} 已禁用（enabled=false），跳过", id);
            return null;
        }
        String typeId = string(json, "type");
        Type type = typeId == null ? null : Type.byId(typeId);
        if (type == null) {
            warn(id, "type 缺失或未知（可用 postcard / parcel / red_packet），实为 " + typeId);
            return null;
        }
        String rawTrigger = string(json, "trigger");
        Trigger trigger = rawTrigger == null ? Trigger.DATE : Trigger.byId(rawTrigger);
        if (trigger == null) {
            warn(id, "trigger 未知（可用 date / birthday），实为 " + rawTrigger);
            return null;
        }
        String player = null;
        String dateSpec = null;
        DateSpec date = null;
        if (trigger == Trigger.DATE) {
            player = string(json, "player");
            dateSpec = string(json, "date");
            if (player != null || dateSpec != null) {
                // 独立信：player + date 成对出现
                if (!ScheduledMailData.isValidPlayerName(player)) {
                    warn(id, "player 缺失或不是合法玩家昵称（模板信请把 player 和 date 都去掉），实为 " + player);
                    return null;
                }
                date = dateSpec == null ? null : DateSpec.parse(dateSpec);
                if (date == null) {
                    warn(id, "date 缺失或格式不对（MM-DD 每年循环 / YYYY-MM-DD 只投一次），实为 " + dateSpec);
                    return null;
                }
            }
            // 两者都没有 = 模板信：不排期，只等日历节日绑定（见 LetterScheduler）
        } else {
            // 收件人与日期都来自日历，文件里写了也没用；报出来免得对着不生效的字段猜
            if (json.has("player")) {
                note(id, "生日信的收件人来自日历（type=birthday 的活动名），player 已忽略");
            }
            if (json.has("date")) {
                note(id, "生日信的日期来自日历，date 已忽略");
            }
        }
        String text = string(json, "text");

        ResourceLocation style = null;
        List<ItemStack> items = List.of();
        switch (type) {
            case POSTCARD -> {
                String styleId = string(json, "style");
                style = styleId == null ? null : ResourceLocation.tryParse(styleId);
                if (style == null) {
                    warn(id, "明信片缺少 style（款式 id，如 contact:new_year_2023），实为 " + styleId);
                    return null;
                }
            }
            case PARCEL, RED_PACKET -> {
                int capacity = type == Type.PARCEL ? ContactMail.PARCEL_CAPACITY : ContactMail.RED_PACKET_CAPACITY;
                items = parseItems(id, json, capacity);
                if (items == null) {
                    return null;
                }
                if (type == Type.PARCEL && text != null) {
                    note(id, "包裹没有正文，text 已忽略");
                }
            }
        }
        return new FestivalLetter(id, type, trigger, player, dateSpec, date, style, items, text);
    }

    /** 解析 items；不合法返回 null */
    @Nullable
    private static List<ItemStack> parseItems(String id, JsonObject json, int capacity) {
        JsonElement element = json.get("items");
        if (element == null) {
            warn(id, "缺少 items（内容物数组，元素形如 {\"item\": \"minecraft:cake\", \"count\": 3}）");
            return null;
        }
        if (!element.isJsonArray()) {
            warn(id, "items 必须是数组");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > capacity) {
            warn(id, "items 有 " + array.size() + " 项，超过上限 " + capacity + " 件（Contact 的容量限制）");
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement child = array.get(i);
            if (!child.isJsonObject()) {
                warn(id, "items[" + i + "] 必须是 {\"item\": ..., \"count\": ...} 对象");
                return null;
            }
            JsonObject entry = child.getAsJsonObject();
            String itemId = string(entry, "item");
            ResourceLocation key = itemId == null ? null : ResourceLocation.tryParse(itemId);
            Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            if (item == null) {
                warn(id, "items[" + i + "] 未知物品 " + itemId);
                return null;
            }
            int count = 1;
            if (entry.has("count")) {
                Integer parsed = integer(entry, "count");
                if (parsed == null || parsed < 1 || parsed > 64) {
                    warn(id, "items[" + i + "] 的 count 必须是 1..64 的整数");
                    return null;
                }
                count = parsed;
            }
            ItemStack stack = new ItemStack(item, count);
            if (stack.isEmpty()) {
                warn(id, "items[" + i + "] 不能是空气");
                return null;
            }
            items.add(stack);
        }
        return items;
    }

    @Nullable
    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    @Nullable
    private static Boolean bool(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : null;
    }

    @Nullable
    private static Integer integer(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt() : null;
    }

    /** 解析失败：整封信跳过 */
    private static void warn(String id, String message) {
        ToTheSky.LOGGER.warn("[节日信] {} 已跳过：{}", id, message);
    }

    /** 可继续投递的配置问题：某个字段不生效（信照发，只是那个字段被忽略） */
    private static void note(String id, String message) {
        ToTheSky.LOGGER.warn("[节日信] {}：{}", id, message);
    }

    /**
     * 投递日：{@code MM-DD}（{@link MonthDay}，每年循环）或 {@code YYYY-MM-DD}（{@link LocalDate}，一次性）。
     */
    private static final class DateSpec {
        /** 每年循环时为非 null */
        @Nullable
        private final MonthDay recurring;
        /** 一次性日期，epochDay */
        private final long oneOff;

        private DateSpec(@Nullable MonthDay recurring, long oneOff) {
            this.recurring = recurring;
            this.oneOff = oneOff;
        }

        boolean recurring() {
            return recurring != null;
        }

        /**
         * 不早于 {@code from} 的首次投递日（epochDay）；一次性日期已过则返回该过去日期（会被视为立即到期）。
         */
        long nextOn(LocalDate from) {
            return recurring == null ? oneOff : nextRecurringOn(recurring, from);
        }

        /** 解析 {@code MM-DD} 或 {@code YYYY-MM-DD}，失败返回 null */
        @Nullable
        static DateSpec parse(String raw) {
            String trimmed = raw.trim();
            String[] parts = trimmed.split("-");
            try {
                if (parts.length == 2) {
                    return new DateSpec(MonthDay.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])), 0L);
                }
                if (parts.length == 3) {
                    LocalDate date = LocalDate.of(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    return new DateSpec(null, date.toEpochDay());
                }
            } catch (NumberFormatException | DateTimeException e) {
                return null;
            }
            return null;
        }
    }
}
