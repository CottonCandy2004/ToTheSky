package com.fst.tothesky.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/**
 * 日历活动/生日：不可变数据模型。
 * 按现实「月-日」键控，逐年循环（无年份字段）。
 *
 * <p>{@link #letter} 是节日可选的**信件绑定**：填 {@code config/tothesky/letters} 里某个文件的
 * id（文件名去掉 {@code .json}），该节日当天就把这封信发给**全服每位玩家**（见
 * {@code contact.LetterScheduler}）。为空 = 不绑定。生日活动不参与绑定（名字已经是收件人）。
 *
 * <p>{@link #lunar} 是生日可选的**农历标记**：{@code true} 时 {@link #month}/{@link #day} 是农历
 * 月-日，逐年按 {@link LunarCalendar} 换算成公历（每年落在不同的公历日）；{@code false} 为公历。
 * 与 {@code letter} 对称：节日活动不收该字段（构造器会清掉）。
 * <p>日历 GUI 只认公历格子，故展示时用 {@link #occurrenceIn} 取「该公历月里落在哪天」；
 * 邮件排期用 {@link #nextOccurrenceOn} 取「不早于某日的下一次」。两者同源，不会各算各的。
 * <p><b>为什么按月而不是按年问</b>：农历年长 353-385 天，一个农历日子在某个公历**年**里可能
 * 出现 0 次或 2 次（例如农历冬月十五在 2026 年落在 1-03 与 12-23 两天，而它在 1957 年一次都没有）。
 * 相邻两次至少隔 353 天，所以同一个公历**月**里最多一次——按月问永远不重不漏。
 */
public final class CalendarEvent {

    /** 节日 */
    public static final String TYPE_FESTIVAL = "festival";
    /** 生日 */
    public static final String TYPE_BIRTHDAY = "birthday";

    /** 无图标（仅文字） */
    public static final String ICON_NONE = "none";
    /** 物品图标（iconId 为物品 registry id） */
    public static final String ICON_ITEM = "item";
    /** 方块图标（iconId 为方块 registry id，客户端取其物品形态渲染） */
    public static final String ICON_BLOCK = "block";
    /** 玩家头图标（iconId 为玩家名） */
    public static final String ICON_PLAYER = "player";

    private static final String TAG_ID = "id";
    private static final String TAG_NAME = "name";
    private static final String TAG_TYPE = "type";
    private static final String TAG_MONTH = "month";
    private static final String TAG_DAY = "day";
    private static final String TAG_ICON_TYPE = "icon_type";
    private static final String TAG_ICON_ID = "icon_id";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_LETTER = "letter";
    private static final String TAG_LUNAR = "lunar";

    public final UUID id;
    public final String name;
    public final String type;
    public final int month;
    public final int day;
    public final String iconType;
    public final String iconId;
    public final String description;
    /** 绑定的信件 id（{@code config/tothesky/letters} 里的文件名去 {@code .json}）；{@code ""} = 未绑定 */
    public final String letter;
    /** 月-日是否为农历（仅生日有意义）；见类注释 */
    public final boolean lunar;

    private CalendarEvent(UUID id, String name, String type, int month, int day,
                          String iconType, String iconId, String description, @Nullable String letter,
                          boolean lunar) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.month = month;
        this.day = day;
        this.iconType = iconType;
        this.iconId = iconId;
        this.description = description;
        // 只有节日能被绑定：生日活动的名字就是收件人，不需要再指向一封信
        this.letter = TYPE_FESTIVAL.equals(type) && letter != null ? letter : "";
        // 反过来只有生日谈得上农历：节日都是公历节日（春节/中秋若要用农历，得先有农历节日需求）
        this.lunar = TYPE_BIRTHDAY.equals(type) && lunar;
    }

    /** 创建新事件（id 由调用方生成） */
    public static CalendarEvent create(UUID id, String name, String type, int month, int day,
                                       String iconType, String iconId, String description,
                                       @Nullable String letter, boolean lunar) {
        return new CalendarEvent(id, name, type, month, day,
                iconType == null || iconType.isEmpty() ? ICON_NONE : iconType,
                iconId == null ? "" : iconId,
                description == null ? "" : description,
                letter, lunar);
    }

    /** 部分更新：null 字段保留原值（改类型时绑定/农历标记会自动清掉，见构造器） */
    public CalendarEvent with(String name, String type, Integer month, Integer day,
                              String iconType, String iconId, String description, String letter,
                              Boolean lunar) {
        return new CalendarEvent(id,
                name != null ? name : this.name,
                type != null ? type : this.type,
                month != null ? month : this.month,
                day != null ? day : this.day,
                iconType != null ? iconType : this.iconType,
                iconId != null ? iconId : this.iconId,
                description != null ? description : this.description,
                letter != null ? letter : this.letter,
                lunar != null ? lunar : this.lunar);
    }

    /** 展示标题：生日显示「xxx的生日」，节日显示名称原样 */
    public String displayTitle() {
        return TYPE_BIRTHDAY.equals(type) ? name + "的生日" : name;
    }

    // ---- 日期换算 ----

    /** 月-日是否是可用日期（公历按月长，2 月按闰年允许 29；农历月最多 30 天） */
    public boolean hasValidDate() {
        if (month < 1 || month > 12) {
            return false;
        }
        return lunar
                ? day >= 1 && day <= 30
                : day >= 1 && day <= YearMonth.of(2000, month).lengthOfMonth();
    }

    /**
     * 该活动是否落在公历 {@code month} 里，落在哪天（日历 GUI 按公历格子展示用）。
     * <p>公历：其月-日就在该月（2-29 在平年顺延到 2-28，见 {@link MonthDay#atYear}）。
     * 农历：从该月 1 号起找下一次发生，落在本月内才算（见类注释：按月问不重不漏）。
     *
     * @return 该月内的公历日期；日期非法、该月没有它或超出农历表范围返回 {@code null}
     */
    @Nullable
    public LocalDate occurrenceIn(YearMonth month) {
        if (!hasValidDate()) {
            return null;
        }
        if (!lunar) {
            if (month.getMonthValue() != this.month) {
                return null;
            }
            return MonthDay.of(this.month, day).atYear(month.getYear());
        }
        // 农历月份号与公历月份号无关（农历八月初六落在公历 9 月），所以要逐月去问
        long epoch = LunarCalendar.nextOccurrenceFrom(this.month, day, month.atDay(1));
        if (epoch == Long.MAX_VALUE) {
            return null;
        }
        LocalDate occurrence = LocalDate.ofEpochDay(epoch);
        return occurrence.getMonthValue() == month.getMonthValue()
                && occurrence.getYear() == month.getYear() ? occurrence : null;
    }

    /**
     * 不早于 {@code from} 的下一次发生（邮件排期用）。
     *
     * @return 公历日期的 epochDay；不可能会再有（日期非法 / 超出农历表范围）返回 {@link Long#MAX_VALUE}
     */
    public long nextOccurrenceOn(LocalDate from) {
        if (!hasValidDate()) {
            return Long.MAX_VALUE;
        }
        if (lunar) {
            return LunarCalendar.nextOccurrenceFrom(month, day, from);
        }
        MonthDay monthDay = MonthDay.of(month, day);
        LocalDate candidate = monthDay.atYear(from.getYear());
        if (candidate.isBefore(from)) {
            candidate = monthDay.atYear(from.getYear() + 1);
        }
        return candidate.toEpochDay();
    }

    /**
     * 排期键/规格里的日期文本：公历 {@code MM-DD}、农历 {@code 农历MM-DD}。
     * <p>两者必须可区分——公历 8 月 6 日与农历八月初六是两回事，键相同就会互相顶掉排期。
     */
    public String dayText() {
        return (lunar ? "农历" : "") + String.format("%02d-%02d", month, day);
    }

    /** 展示用日期文本：公历 {@code 8月6日}、农历 {@code 农历八月初六} */
    public String dateText() {
        return lunar ? "农历" + LunarCalendar.describe(month, day) : month + "月" + day + "日";
    }

    // ---- NBT ----

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_NAME, name);
        tag.putString(TAG_TYPE, type);
        tag.putInt(TAG_MONTH, month);
        tag.putInt(TAG_DAY, day);
        tag.putString(TAG_ICON_TYPE, iconType);
        tag.putString(TAG_ICON_ID, iconId);
        tag.putString(TAG_DESCRIPTION, description);
        tag.putString(TAG_LETTER, letter);
        tag.putBoolean(TAG_LUNAR, lunar);
        return tag;
    }

    @Nullable
    public static CalendarEvent fromTag(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID)) {
            return null;
        }
        return new CalendarEvent(tag.getUUID(TAG_ID),
                tag.getString(TAG_NAME),
                tag.getString(TAG_TYPE),
                tag.getInt(TAG_MONTH),
                tag.getInt(TAG_DAY),
                tag.getString(TAG_ICON_TYPE),
                tag.getString(TAG_ICON_ID),
                tag.getString(TAG_DESCRIPTION),
                tag.getString(TAG_LETTER),
                tag.getBoolean(TAG_LUNAR));
    }

    public static ListTag toListTag(List<CalendarEvent> events) {
        ListTag list = new ListTag();
        for (CalendarEvent event : events) {
            list.add(event.toTag());
        }
        return list;
    }

    public static List<CalendarEvent> listFromTag(ListTag list) {
        List<CalendarEvent> events = new ArrayList<>();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag compound) {
                CalendarEvent event = fromTag(compound);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        return events;
    }

}