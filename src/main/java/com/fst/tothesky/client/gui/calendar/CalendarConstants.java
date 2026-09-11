package com.fst.tothesky.client.gui.calendar;

/**
 * 日历 GUI 布局常量（与新底图逐像素测量对齐）。
 *
 * <p>底图 256×256（自带标题栏、星期标签、完整日期格边框与填充）。
 * 日期格区：第一完整格左上 (41, 87)，7 列 × 6 行，步进 25px
 * （格主体 22px + 2px 间隙 + 1px 右下阴影），填充内容区 18×18（从格左上 +3 起）。
 * 底缘 87 + 5×25 + 22 = 234，在 256 底图内。
 * 日期框贴图 24×24（贴图主体在 x/y=1..22，四周留白）→ blit 坐标 = 格左上 -1。
 */
public final class CalendarConstants {
    /** 底图尺寸 */
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 256;

    /** 第一个完整日期格左上角（含边框） */
    public static final int GRID_X = 41;
    public static final int GRID_Y = 87;

    /** 相邻格左上角间距（22px 主体 + 2px 间隙 + 1px 阴影） */
    public static final int STEP = 25;
    /** 行列数：六行七列（最多 42 格，覆盖所有月份） */
    public static final int ROWS = 6;
    public static final int COLUMNS = 7;

    /** 格主体尺寸（含边框） */
    public static final int CELL = 22;
    /** 格内内容区尺寸（浅黄填充） */
    public static final int CONTENT = 18;
    /** 内容区相对格左上的偏移（三重边框 2px + 1px） */
    public static final int CONTENT_OFFSET = 3;

    /** 日期框贴图尺寸（主体 22px 在贴图 1..22，四周各留 1px） */
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