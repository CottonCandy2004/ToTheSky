package com.fst.tothesky.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

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

    private CalendarEvent(UUID id, String name, String type, int month, int day,
                          String iconType, String iconId, String description, @Nullable String letter) {
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
    }

    /** 创建新事件（id 由调用方生成） */
    public static CalendarEvent create(UUID id, String name, String type, int month, int day,
                                       String iconType, String iconId, String description,
                                       @Nullable String letter) {
        return new CalendarEvent(id, name, type, month, day,
                iconType == null || iconType.isEmpty() ? ICON_NONE : iconType,
                iconId == null ? "" : iconId,
                description == null ? "" : description,
                letter);
    }

    /** 部分更新：null 字段保留原值，返回新实例（改类型时绑定会自动清掉，见构造器） */
    public CalendarEvent with(String name, String type, Integer month, Integer day,
                              String iconType, String iconId, String description, String letter) {
        return new CalendarEvent(id,
                name != null ? name : this.name,
                type != null ? type : this.type,
                month != null ? month : this.month,
                day != null ? day : this.day,
                iconType != null ? iconType : this.iconType,
                iconId != null ? iconId : this.iconId,
                description != null ? description : this.description,
                letter != null ? letter : this.letter);
    }

    /** 展示标题：生日显示「xxx的生日」，节日显示名称原样 */
    public String displayTitle() {
        return TYPE_BIRTHDAY.equals(type) ? name + "的生日" : name;
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
                tag.getString(TAG_LETTER));
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