package com.fst.tothesky.calendar;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

/**
 * 农历（阴阳历）↔ 公历换算：查表法，覆盖 1900–2100 农历年。
 *
 * <p>农历的月长（29/30 天）、闰月位置都不是简单周期，靠公式推不出——真正便携的做法是两个：
 * 查表（本类）或算朔望月与中气的天文历（需一套太阳黄经数据）。这里取前者：一张
 * {@value #YEAR_COUNT} 项的 {@code int} 表，每项编码一个农历年，是中文日历库的通用做法，
 * 结果与官方历书一致（已用 2000–2030 年全部春节日期与 2020/2023/2025/2028 闰月核对）。
 *
 * <p>表项位布局：{@code 0x8000..0x10} 十二个月的长度（置位 = 30 天，清零 = 29 天）、
 * {@code 0x10000} 闰月长度（置位 = 30 天）、低 4 位 = 闰月的月份号（0 = 该年无闰月）。
 *
 * <p><b>闰月归属</b>：本类只认「非闰月」的那个月份——{@code month=6} 永远是六月，
 * 该年闰六月不影响六月的换算。所以闰月出生的人按普通月份过生日。
 *
 * <p><b>小月顺延</b>：月长只有 29 天时，{@code day=30} 按该月最后一天（廿九）算，
 * 与 {@code MonthDay.atYear} 把 2-29 顺延到 2-28 是同一套约定。
 *
 * <p>所有方法都是纯函数、线程安全；越界（1900 年之前、2100 农历年之后）不抛异常，
 * 一律以 {@code null} / {@link Long#MAX_VALUE} 表示「算不出来」，免得一个手改的存档把服炸掉。
 */
public final class LunarCalendar {

    /** 表覆盖的最早农历年 */
    public static final int MIN_YEAR = 1900;
    /** 表覆盖的最晚农历年 */
    public static final int MAX_YEAR = 2100;

    private static final int YEAR_COUNT = MAX_YEAR - MIN_YEAR + 1;

    /** 农历 1900 年正月初一 = 公历 1900-01-31（表的基准日，各项累加由此起算） */
    private static final LocalDate BASE = LocalDate.of(1900, 1, 31);

    /** 农历月份名（下标 1..12；十一月称冬月、十二月称腊月） */
    private static final String[] MONTH_NAMES = {
            "", "正月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "冬月", "腊月"};

    /** 日期数字（下标 0..10，用于「初X」「十X」「廿X」「三十」的拼接） */
    private static final String[] DIGITS = {"〇", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private static final int[] INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
            0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
            0x0d520};

    private LunarCalendar() {
    }

    // ---- 表查询 ----

    /** 该农历年的闰月月份号（0 = 无闰月） */
    private static int leapMonth(int lunarYear) {
        return INFO[lunarYear - MIN_YEAR] & 0xf;
    }

    /** 该农历年闰月的天数（无闰月为 0） */
    private static int leapDays(int lunarYear) {
        int leap = leapMonth(lunarYear);
        if (leap == 0) {
            return 0;
        }
        return (INFO[lunarYear - MIN_YEAR] & 0x10000) == 0 ? 29 : 30;
    }

    /** 该农历年第 month 个月的天数（不含闰月） */
    private static int monthDays(int lunarYear, int month) {
        return (INFO[lunarYear - MIN_YEAR] & (0x10000 >> month)) == 0 ? 29 : 30;
    }

    /** 该农历年的总天数（12 或 13 个月） */
    private static int yearDays(int lunarYear) {
        int days = 348; // 12 × 29，再按置位补成 30 天的月
        for (int bit = 0x8000; bit > 0x8; bit >>= 1) {
            if ((INFO[lunarYear - MIN_YEAR] & bit) != 0) {
                days++;
            }
        }
        return days + leapDays(lunarYear);
    }

    // ---- 换算 ----

    /**
     * 农历 → 公历：农历 {@code lunarYear} 年（{@code month} 月 {@code day} 日）对应的公历日。
     * <p>闰月不参与（见类注释）；{@code day} 超过该月天数时取该月最后一天。
     *
     * @return 公历日期；农历年不在表内返回 {@code null}
     */
    @Nullable
    public static LocalDate solarOf(int lunarYear, int month, int day) {
        if (lunarYear < MIN_YEAR || lunarYear > MAX_YEAR || month < 1 || month > 12 || day < 1) {
            return null;
        }
        long days = 0;
        for (int y = MIN_YEAR; y < lunarYear; y++) {
            days += yearDays(y);
        }
        int leap = leapMonth(lunarYear);
        for (int m = 1; m < month; m++) {
            days += monthDays(lunarYear, m);
            if (m == leap) {
                days += leapDays(lunarYear);
            }
        }
        days += Math.min(day, monthDays(lunarYear, month)) - 1L;
        return BASE.plusDays(days);
    }

    /**
     * 公历 → 农历年（该日期所在的农历年，春节前属上一年）。
     *
     * @return 农历年；日期在表范围外返回 {@code null}
     */
    @Nullable
    public static Integer lunarYearOf(LocalDate date) {
        if (date.isBefore(BASE)) {
            return null;
        }
        long offset = date.toEpochDay() - BASE.toEpochDay();
        for (int y = MIN_YEAR; y <= MAX_YEAR; y++) {
            int length = yearDays(y);
            if (offset < length) {
                return y;
            }
            offset -= length;
        }
        return null;
    }

    /**
     * 农历「{@code month} 月 {@code day} 日」在不早于 {@code from} 的第一次发生（公历日）。
     * <p>实现：从 {@code from} 所在农历年的前一年起扫 4 个农历年取最早的一个——
     * 相邻两次相隔一个农历年（29x/38x 天），4 年窗口必然覆盖。
     *
     * @return 公历日期的 epochDay；{@code from} 或结果超出表范围时返回 {@link Long#MAX_VALUE}
     */
    public static long nextOccurrenceFrom(int month, int day, LocalDate from) {
        if (month < 1 || month > 12 || day < 1) {
            return Long.MAX_VALUE;
        }
        Integer current = lunarYearOf(from);
        if (current == null) {
            return Long.MAX_VALUE;
        }
        long fromEpoch = from.toEpochDay();
        long best = Long.MAX_VALUE;
        for (int y = current - 1; y <= current + 2; y++) {
            LocalDate candidate = solarOf(y, month, day);
            if (candidate == null) {
                continue;
            }
            long epoch = candidate.toEpochDay();
            if (epoch >= fromEpoch && epoch < best) {
                best = epoch;
            }
        }
        return best;
    }

    // ---- 展示 ----

    /** 农历日期的中文写法，如 {@code 八月初六}、{@code 腊月三十} */
    public static String describe(int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 30) {
            return month + "月" + day + "日";
        }
        return MONTH_NAMES[month] + dayName(day);
    }

    /** 农历日名：初一…初十、十一…十九、二十、廿一…廿九、三十 */
    private static String dayName(int day) {
        if (day <= 10) {
            return "初" + DIGITS[day];
        }
        if (day < 20) {
            return "十" + DIGITS[day - 10];
        }
        if (day == 20) {
            return "二十";
        }
        if (day < 30) {
            return "廿" + DIGITS[day - 20];
        }
        return "三十";
    }
}
