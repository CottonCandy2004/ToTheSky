package com.fst.tothesky.client.gui.calendar;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarEvent;
import com.fst.tothesky.network.CalendarDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 日历主屏幕：底图 + 日期格 + 图标轮播 + tooltip + 翻月按钮。
 * 只读（编辑走 REST API 或 {@code /tothesky setbirthday}）；「今天」高亮；事件数据来自 S2C 包全量，本地翻月。
 * <p>格子是公历的，农历生日要按**当前显示的那一年**换算成公历日才摆得上（见 {@link #monthIndex}）——
 * 所以同一个农历生日翻到不同年份会落在不同的格子，这正是它该有的样子。
 */
public final class CalendarScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar.png");
    private static final ResourceLocation DAY_FRAME =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar_day.png");
    /** 翻月按钮贴图（32×32，内容偏上：黄色面板 + 棕色斜阴影） */
    private static final ResourceLocation PREV_BUTTON =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar_prev.png");
    private static final ResourceLocation NEXT_BUTTON =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar_next.png");

    /** 当前包数据 */
    private CalendarDataPacket packet;
    /** 当前显示年月（可与包不同：本地翻月） */
    private int shownYear;
    private int shownMonth;

    /** 轮播计数器 */
    private int carouselCounter;

    /** 打开中的屏幕引用（非 open 包刷新用） */
    private static CalendarScreen openScreen;

    public CalendarScreen(CalendarDataPacket packet) {
        super(Component.translatable("block.tothesky.calendar"));
        this.packet = packet;
        this.shownYear = packet.year;
        this.shownMonth = packet.month;
    }

    /** 网络层入口：open 打开屏幕，否则刷新已打开屏幕 */
    public static void onData(CalendarDataPacket packet) {
        if (packet.open) {
            CalendarScreen screen = new CalendarScreen(packet);
            openScreen = screen;
            Minecraft.getInstance().setScreen(screen);
        } else if (openScreen != null) {
            openScreen.packet = packet;
        }
    }

    @Override
    protected void init() {
        int left = (this.width - CalendarConstants.IMAGE_WIDTH) / 2;
        int top = (this.height - CalendarConstants.IMAGE_HEIGHT) / 2;
        // 翻页按钮改用贴图箭头（32×32，不绘制文本）
        addRenderableWidget(new CalendarIconButton(
                left + CalendarConstants.BUTTON_MARGIN, top + CalendarConstants.BUTTON_Y,
                CalendarConstants.BUTTON_SIZE, PREV_BUTTON,
                Component.translatable("gui.tothesky.calendar.prev_month"), b -> shiftMonth(-1)));
        addRenderableWidget(new CalendarIconButton(
                left + CalendarConstants.IMAGE_WIDTH - CalendarConstants.BUTTON_MARGIN
                        - CalendarConstants.BUTTON_SIZE,
                top + CalendarConstants.BUTTON_Y,
                CalendarConstants.BUTTON_SIZE, NEXT_BUTTON,
                Component.translatable("gui.tothesky.calendar.next_month"), b -> shiftMonth(1)));
    }

    private void shiftMonth(int delta) {
        int total = shownYear * 12 + (shownMonth - 1) + delta;
        shownYear = Math.floorDiv(total, 12);
        shownMonth = total - shownYear * 12 + 1;
    }

    @Override
    public void tick() {
        carouselCounter++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int x = (this.width - CalendarConstants.IMAGE_WIDTH) / 2;
        int y = (this.height - CalendarConstants.IMAGE_HEIGHT) / 2;

        // 底图 256×256（7 参 blit 按 256×256 硬编码）
        graphics.blit(BACKGROUND, x, y, 0, 0,
                CalendarConstants.IMAGE_WIDTH, CalendarConstants.IMAGE_HEIGHT);

        // 月份标题 xxxx·x：水平居中；文字下缘距日期格第一行上缘 TITLE_GAP(13px)
        String monthTitle = shownYear + "·" + shownMonth;
        int titleX = (CalendarConstants.IMAGE_WIDTH - this.font.width(monthTitle)) / 2;
        int titleY = CalendarConstants.GRID_Y - CalendarConstants.TITLE_GAP
                - CalendarConstants.TITLE_HEIGHT;
        graphics.drawString(this.font, monthTitle, x + titleX, y + titleY, 0x404040, false);

        YearMonth yearMonth = YearMonth.of(shownYear, shownMonth);
        LocalDate first = LocalDate.of(shownYear, shownMonth, 1);
        // 周一起始：value() 1..7 → 列 0..6
        int firstCol = first.getDayOfWeek().getValue() - CalendarConstants.FIRST_DAY_OF_WEEK;
        if (firstCol < 0) {
            firstCol += 7;
        }
        int days = yearMonth.lengthOfMonth();
        boolean isCurrentMonth = shownYear == packet.year && shownMonth == packet.month;
        // 农历生日要换算成公历日：每帧只算一次，别在 31 个格子上重复换算
        List<List<CalendarEvent>> byDay = monthIndex(yearMonth);

        List<Component> hoveredTooltip = null;
        int hoveredX = 0;
        int hoveredY = 0;
        // 各格左上角（数字统一后画，避免 item flush 的 depth 残留裁字）
        int[] dayX = new int[days + 1];
        int[] dayY = new int[days + 1];

        for (int day = 1; day <= days; day++) {
            int index = firstCol + day - 1;
            int col = index % CalendarConstants.COLUMNS;
            int row = index / CalendarConstants.COLUMNS;
            int cellX = x + CalendarConstants.cellX(col);
            int cellY = y + CalendarConstants.cellY(row);
            dayX[day] = cellX;
            dayY[day] = cellY;

            // 日期框贴图 24×24（主体在贴图 1..22）→ 坐标 -1 对齐 22px 格主体
            graphics.blit(DAY_FRAME, cellX - 1, cellY - 1, 0, 0,
                    CalendarConstants.DAY_FRAME_SIZE, CalendarConstants.DAY_FRAME_SIZE,
                    CalendarConstants.DAY_FRAME_SIZE, CalendarConstants.DAY_FRAME_SIZE);

            // 今天高亮（覆盖内容区）
            if (isCurrentMonth && day == packet.todayDay) {
                graphics.fill(cellX + CalendarConstants.CONTENT_OFFSET,
                        cellY + CalendarConstants.CONTENT_OFFSET,
                        cellX + CalendarConstants.CONTENT_OFFSET + CalendarConstants.CONTENT,
                        cellY + CalendarConstants.CONTENT_OFFSET + CalendarConstants.CONTENT,
                        0x40FFFFFF);
            }

            // 当日事件（图标）
            List<CalendarEvent> events = byDay.get(day);
            if (!events.isEmpty()) {
                CalendarEvent event = events.get(carouselIndex(events.size()));
                renderIcon(graphics, event, cellX, cellY);

                if (isHovering(cellX, cellY, mouseX, mouseY)) {
                    hoveredTooltip = tooltipOf(events);
                    hoveredX = mouseX;
                    hoveredY = mouseY;
                }
            }
        }

        // 日期数字统一最后画：先推 pose z=200 压过 item 渲染写入的深度（z=150），
        // 否则 depth test 会把文字裁掉——表现为图标盖住数字
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        for (int day = 1; day <= days; day++) {
            // 数字在内容区左上角（格左上 +3，避开三重边框）
            graphics.drawString(this.font, String.valueOf(day),
                    dayX[day] + CalendarConstants.CONTENT_OFFSET,
                    dayY[day] + CalendarConstants.CONTENT_OFFSET, 0x404040, false);
        }
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);

        // tooltip 最后画（浮在按钮之上）
        if (hoveredTooltip != null) {
            graphics.renderComponentTooltip(this.font, hoveredTooltip, hoveredX, hoveredY);
        }
    }

    private int carouselIndex(int size) {
        return size <= 1 ? 0 : (carouselCounter / CalendarConstants.CAROUSEL_PERIOD) % size;
    }

    /**
     * 当月每天的事件（下标 = day-of-month，0 号位空着）。
     * <p>逐月问 {@link CalendarEvent#occurrenceIn}：公历事件非本月的直接落空，农历事件按当年换算
     * （农历月份号与公历无关，同一个农历日子可能落在相邻公历月，也可能某年只有一次——
     * 按月问才不重不漏，见 {@code CalendarEvent} 类注释）。
     */
    private List<List<CalendarEvent>> monthIndex(YearMonth yearMonth) {
        int days = yearMonth.lengthOfMonth();
        List<List<CalendarEvent>> byDay = new ArrayList<>(days + 1);
        for (int day = 0; day <= days; day++) {
            byDay.add(new ArrayList<>());
        }
        for (CalendarEvent event : packet.events) {
            LocalDate occurrence = event.occurrenceIn(yearMonth);
            if (occurrence != null) {
                byDay.get(occurrence.getDayOfMonth()).add(event);
            }
        }
        return byDay;
    }

    private List<Component> tooltipOf(List<CalendarEvent> events) {
        List<Component> lines = new ArrayList<>();
        for (CalendarEvent event : events) {
            String mark = CalendarEvent.TYPE_BIRTHDAY.equals(event.type) ? "🎂 " : "🎉 ";
            // 农历生日摆在公历格子上，顺手把农历日期报出来，免得玩家以为是公历的那天
            String suffix = event.lunar ? "（" + event.dateText() + "）" : "";
            lines.add(Component.literal(mark + event.displayTitle() + suffix));
            if (!event.description.isEmpty()) {
                lines.add(Component.literal("  " + event.description));
            }
        }
        return lines;
    }

    private boolean isHovering(int cellX, int cellY, int mouseX, int mouseY) {
        return mouseX >= cellX && mouseX < cellX + CalendarConstants.CELL
                && mouseY >= cellY && mouseY < cellY + CalendarConstants.CELL;
    }

    /** 单图标轮播：物品/方块（取物品形态）/ 玩家头 */
    private void renderIcon(GuiGraphics graphics, CalendarEvent event, int cellX, int cellY) {
        switch (event.iconType) {
            case CalendarEvent.ICON_ITEM, CalendarEvent.ICON_BLOCK -> {
                ResourceLocation id = ResourceLocation.tryParse(event.iconId);
                if (id == null) {
                    return;
                }
                var item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) {
                    return;
                }
                // 16px 图标贴内容区右下再整体左上移 1px（18px 内容区留 3px 上边距给数字）
                int contentX = cellX + CalendarConstants.CONTENT_OFFSET;
                int contentY = cellY + CalendarConstants.CONTENT_OFFSET;
                graphics.renderItem(new ItemStack(item),
                        contentX + CalendarConstants.CONTENT - 17,
                        contentY + CalendarConstants.CONTENT - 17);
            }
            case CalendarEvent.ICON_PLAYER -> {
                // 玩家头像比物品小一圈：14px（内容区右下锚点同样左上移 1px）
                int contentX = cellX + CalendarConstants.CONTENT_OFFSET;
                int contentY = cellY + CalendarConstants.CONTENT_OFFSET;
                CalendarHeadRenderer.render(event.iconId, graphics,
                        contentX + CalendarConstants.CONTENT - 15,
                        contentY + CalendarConstants.CONTENT - 15, 14);
            }
        }
    }

    @Override
    public void onClose() {
        openScreen = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}