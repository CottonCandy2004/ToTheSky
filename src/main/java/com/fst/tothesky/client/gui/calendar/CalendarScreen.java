package com.fst.tothesky.client.gui.calendar;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.calendar.CalendarEvent;
import com.fst.tothesky.network.CalendarDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 只读（编辑走 REST API）；「今天」高亮；事件数据来自 S2C 包全量，本地翻月。
 */
public final class CalendarScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar.png");
    private static final ResourceLocation DAY_FRAME =
            new ResourceLocation(ToTheSky.MODID, "textures/gui/calendar_day.png");

    /** 翻月按钮（占位：底图画好后换成贴图箭头） */
    private static final int BUTTON_W = 52;
    private static final int BUTTON_H = 14;

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
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.tothesky.calendar.prev_month"), b -> shiftMonth(-1))
                .bounds(left + 16, top + 16, BUTTON_W, BUTTON_H)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.tothesky.calendar.next_month"), b -> shiftMonth(1))
                .bounds(left + CalendarConstants.IMAGE_WIDTH - 16 - BUTTON_W, top + 16, BUTTON_W, BUTTON_H)
                .build());
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

        YearMonth yearMonth = YearMonth.of(shownYear, shownMonth);
        LocalDate first = LocalDate.of(shownYear, shownMonth, 1);
        // 周一起始：value() 1..7 → 列 0..6
        int firstCol = first.getDayOfWeek().getValue() - CalendarConstants.FIRST_DAY_OF_WEEK;
        if (firstCol < 0) {
            firstCol += 7;
        }
        int days = yearMonth.lengthOfMonth();
        boolean isCurrentMonth = shownYear == packet.year && shownMonth == packet.month;

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

            // 日期框 24×24（1px 透明边 → 坐标 -1 对齐 22px 内容区）
            graphics.blit(DAY_FRAME, cellX - 1, cellY - 1, 0, 0,
                    CalendarConstants.DAY_FRAME_SIZE, CalendarConstants.DAY_FRAME_SIZE,
                    CalendarConstants.DAY_FRAME_SIZE, CalendarConstants.DAY_FRAME_SIZE);

            // 今天高亮
            if (isCurrentMonth && day == packet.todayDay) {
                graphics.fill(cellX, cellY,
                        cellX + CalendarConstants.CELL, cellY + CalendarConstants.CELL, 0x40FFFFFF);
            }

            // 当日事件（图标）
            List<CalendarEvent> events = eventsOn(day);
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
            graphics.drawString(this.font, String.valueOf(day),
                    dayX[day] + 2, dayY[day] + 2, 0x404040, false);
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

    private List<CalendarEvent> eventsOn(int day) {
        List<CalendarEvent> result = new ArrayList<>();
        for (CalendarEvent event : packet.events) {
            if (event.month == shownMonth && event.day == day) {
                result.add(event);
            }
        }
        return result;
    }

    private List<Component> tooltipOf(List<CalendarEvent> events) {
        List<Component> lines = new ArrayList<>();
        for (CalendarEvent event : events) {
            String mark = CalendarEvent.TYPE_BIRTHDAY.equals(event.type) ? "🎂 " : "🎉 ";
            lines.add(Component.literal(mark + event.displayTitle()));
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
                // 16px 图标贴格右下再整体左上移 1px（22px 格留 7px 边距给数字）
                graphics.renderItem(new ItemStack(item),
                        cellX + CalendarConstants.CELL - 17, cellY + CalendarConstants.CELL - 17);
            }
            case CalendarEvent.ICON_PLAYER ->
                    // 玩家头像比物品小一圈：14px（右下锚点同样左上移 1px）
                    CalendarHeadRenderer.render(event.iconId, graphics,
                            cellX + CalendarConstants.CELL - 15, cellY + CalendarConstants.CELL - 15, 14);
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