package com.fst.tothesky.client.gui.calendar;

/**
 * 日历 GUI 布局常量（与用户提供的底图/日期框规格对应）。
 *
 * <p>底图 256×256；日期格区起点 (42, 92)，7 列 × 5 行，
 * 格间距 25px（22px 可见内容 + 3px 间隔）。
 * 日期框贴图 24×24（四周 1px 透明边），blit 时坐标 -1 对齐。
 * 月份需要 6 周时第 6 行落在 y=217（仍在底图内）。
 */
public final class CalendarConstants {
    /** 底图尺寸 */
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 256;

    /** 格区起点（第一个日期格左上角） */
    public static final int GRID_X = 42;
    public static final int GRID_Y = 92;

    /** 单格可见内容尺寸 */
    public static final int CELL = 22;
    /** 相邻格左上角间距（22px 内容 + 3px 间隔） */
    public static final int STEP = 25;
    /** 固定 5 行，6 周月份在渲染时追加第 6 行 */
    public static final int ROWS = 5;
    public static final int COLUMNS = 7;

    /** 日期框贴图尺寸（含 1px 透明边） */
    public static final int DAY_FRAME_SIZE = 24;

    /** 图标轮播周期（tick） */
    public static final int CAROUSEL_PERIOD = 40;

    /** 星期标签起始（周一为第一列） */
    public static final int FIRST_DAY_OF_WEEK = java.time.DayOfWeek.MONDAY.getValue();

    private CalendarConstants() {
    }

    /** 第 (row, col) 个格子的左上角 X（0-based） */
    public static int cellX(int col) {
        return GRID_X + col * STEP;
    }

    /** 第 (row, col) 个格子的左上角 Y（0-based） */
    public static int cellY(int row) {
        return GRID_Y + row * STEP;
    }
}