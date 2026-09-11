package com.fst.tothesky.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

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
 *
 * <p>崩溃安全：SavedData 依赖 autosave 周期（默认 5 分钟）与正常停服落盘，
 * 服务器被强杀时未落盘的 REST 变工会丢失。故每次变更同步追加写
 * {@code world/calendar_events.json}（人类可读镜像）；启动时 SavedData 为空
 * 且镜像存在则以镜像恢复。事件量级几十条，全量重写开销可忽略。
 */
public final class CalendarData extends SavedData {
    public static final String DATA_NAME = "tothesky_calendar";
    private static final String TAG_EVENTS = "events";
    /** 崩溃安全镜像文件名（位于主世界根目录，与 level.dat 同级） */
    public static final String MIRROR_FILE = "calendar_events.json";

    /** 「月-日」→ 当日事件 */
    private final Map<Integer, List<CalendarEvent>> byMonthDay = new HashMap<>();
    /** 主世界根目录（镜像读写用）；构造时未知，首次 get 时注入 */
    @Nullable
    private java.nio.file.Path worldDir;

    private CalendarData() {
    }

    /** 读取/创建（服务器级：挂在主世界上）；SavedData 为空时从镜像恢复 */
    public static CalendarData get(MinecraftServer server) {
        CalendarData data = server.overworld().getDataStorage().computeIfAbsent(
                CalendarData::read, CalendarData::new, DATA_NAME);
        if (data.worldDir == null) {
            data.worldDir = server.getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT);
            if (data.byMonthDay.isEmpty()) {
                data.restoreFromMirror();
            }
        }
        return data;
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
        writeMirror();
        return event;
    }

    /** 更新（按 id 定位后替换），成功返回新实例，找不到返回 null */
    public CalendarEvent update(UUID id, CalendarEvent updated) {
        if (remove(id)) {
            put(updated);
            setDirty();
            writeMirror();
            return updated;
        }
        return null;
    }


    /** 删除，返回是否删除 */
    public boolean remove(UUID id) {
        for (List<CalendarEvent> day : byMonthDay.values()) {
            if (day.removeIf(e -> e.id.equals(id))) {
                setDirty();
                writeMirror();
                return true;
            }
        }
        return false;
    }

    // ---- 崩溃安全镜像（world/calendar_events.json） ----

    /** 全量重写镜像（每次变更调用；几十条事件开销可忽略） */
    private void writeMirror() {
        if (worldDir == null) {
            return;
        }
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (CalendarEvent event : all()) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("id", event.id.toString());
            obj.addProperty("name", event.name);
            obj.addProperty("type", event.type);
            obj.addProperty("month", event.month);
            obj.addProperty("day", event.day);
            obj.addProperty("iconType", event.iconType);
            obj.addProperty("iconId", event.iconId);
            obj.addProperty("description", event.description);
            array.add(obj);
        }
        try {
            java.nio.file.Files.writeString(worldDir.resolve(MIRROR_FILE),
                    new com.google.gson.Gson().toJson(array),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            com.fst.tothesky.ToTheSky.LOGGER.warn("[日历] 写镜像文件失败: {}", e.getMessage());
        }
    }

    /** 启动恢复：SavedData 为空且镜像存在时从镜像载入 */
    private void restoreFromMirror() {
        if (worldDir == null) {
            return;
        }
        java.nio.file.Path file = worldDir.resolve(MIRROR_FILE);
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonArray();
            for (var element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject obj = element.getAsJsonObject();
                CalendarEvent event = CalendarEvent.create(
                        java.util.UUID.fromString(obj.get("id").getAsString()),
                        obj.get("name").getAsString(),
                        obj.has("type") ? obj.get("type").getAsString() : CalendarEvent.TYPE_FESTIVAL,
                        obj.get("month").getAsInt(),
                        obj.get("day").getAsInt(),
                        obj.has("iconType") ? obj.get("iconType").getAsString() : CalendarEvent.ICON_NONE,
                        obj.has("iconId") ? obj.get("iconId").getAsString() : "",
                        obj.has("description") ? obj.get("description").getAsString() : "");
                put(event);
            }
            if (!byMonthDay.isEmpty()) {
                setDirty();
                com.fst.tothesky.ToTheSky.LOGGER.info("[日历] 从镜像恢复 {} 条事件", all().size());
            }
        } catch (Exception e) {
            com.fst.tothesky.ToTheSky.LOGGER.warn("[日历] 镜像恢复失败（忽略，从空数据开始）: {}", e.getMessage());
        }
    }
}