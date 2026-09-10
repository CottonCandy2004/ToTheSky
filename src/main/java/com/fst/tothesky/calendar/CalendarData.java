package com.fst.tothesky.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 日历活动数据（服务器级 SavedData，存于主世界 data/ 下）。
 * 与 {@code CheckerIndexData} 相同模式；事件量级小，全量读写。
 * 仅在服务器线程调用（HTTP 层经 server.execute() 提交写操作）。
 */
public final class CalendarData extends SavedData {
    public static final String DATA_NAME = "tothesky_calendar";
    private static final String TAG_EVENTS = "events";

    /** 「月-日」→ 当日事件（稳定排序：生日在前？否——按 name 排序，展示顺序可预期） */
    private final Map<Integer, List<CalendarEvent>> byMonthDay = new HashMap<>();

    private CalendarData() {
    }

    /** 读取/创建（服务器级：挂在主世界上） */
    public static CalendarData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CalendarData::read, CalendarData::new, DATA_NAME);
    }

    private static CalendarData read(CompoundTag tag) {
        CalendarData data = new CalendarData();
        ListTag list = tag.getList(TAG_EVENTS, Tag.TAG_COMPOUND);
        for (CalendarEvent event : CalendarEvent.listFromTag(list)) {
            data.put(event);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put(TAG_EVENTS, CalendarEvent.toListTag(all()));
        return tag;
    }

    private static int key(int month, int day) {
        return month * 100 + day;
    }

    private void put(CalendarEvent event) {
        byMonthDay.computeIfAbsent(key(event.month, event.day), k -> new ArrayList<>())
                .add(event);
    }

    /** 全部事件（按 月→日→name 排序，输出稳定） */
    public List<CalendarEvent> all() {
        List<CalendarEvent> all = new ArrayList<>();
        for (List<CalendarEvent> day : byMonthDay.values()) {
            all.addAll(day);
        }
        all.sort(Comparator.comparingInt((CalendarEvent e) -> e.month)
                .thenComparingInt(e -> e.day)
                .thenComparing(e -> e.name));
        return all;
    }

    /** 某日事件（可为空列表） */
    public List<CalendarEvent> byDay(int month, int day) {
        List<CalendarEvent> events = byMonthDay.get(key(month, day));
        return events == null ? Collections.emptyList() : Collections.unmodifiableList(events);
    }

    /** 按 id 查找，找不到返回 null */
    public CalendarEvent find(UUID id) {
        for (List<CalendarEvent> day : byMonthDay.values()) {
            for (CalendarEvent event : day) {
                if (event.id.equals(id)) {
                    return event;
                }
            }
        }
        return null;
    }

    /** 新增（替换同 id 旧值），返回存入实例 */
    public CalendarEvent add(CalendarEvent event) {
        remove(event.id);
        put(event);
        setDirty();
        return event;
    }

    /** 更新（按 id 定位后替换），成功返回新实例，找不到返回 null */
    public CalendarEvent update(UUID id, CalendarEvent updated) {
        if (remove(id)) {
            put(updated);
            setDirty();
            return updated;
        }
        return null;
    }

    /** 删除，返回是否删除 */
    public boolean remove(UUID id) {
        for (List<CalendarEvent> day : byMonthDay.values()) {
            if (day.removeIf(e -> e.id.equals(id))) {
                setDirty();
                return true;
            }
        }
        return false;
    }
}