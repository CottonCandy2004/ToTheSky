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

    /**
     * 读取/创建（服务器级：挂在主世界上）；镜像文件与 SavedData 按 id 并集合并。
     * <p>双写（SavedData + world/calendar_events.json）在多进程/强杀交错下可能一方更新：
     * 合并保证两边任一侧的新增事件都不丢；同 id 以内存（SavedData 读出的）为准。
     */
    public static CalendarData get(MinecraftServer server) {
        CalendarData data = server.overworld().getDataStorage().computeIfAbsent(
                CalendarData::read, CalendarData::new, DATA_NAME);
        if (data.worldDir == null) {
            data.worldDir = server.getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT);
            data.mergeFromMirror();
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

    /**
     * 启动合并：镜像事件按 id 并入（同 id 保留内存中的 SavedData 版本）。
     * 逐条容错——单条脏数据（非法 UUID、缺字段、历史 int 数组 id 格式）跳过，
     * 不影响其余条目；全部解析完如有新增则落盘回写。
     */
    private void mergeFromMirror() {
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
            int merged = 0;
            int skipped = 0;
            for (var element : array) {
                if (!element.isJsonObject()) {
                    skipped++;
                    continue;
                }
                try {
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    java.util.UUID id = readId(obj.get("id"));
                    if (id == null || find(id) != null) {
                        continue; // id 无法解析，或 SavedData 已有该 id
                    }
                    CalendarEvent event = CalendarEvent.create(
                            id,
                            obj.get("name").getAsString(),
                            obj.has("type") ? obj.get("type").getAsString() : CalendarEvent.TYPE_FESTIVAL,
                            obj.get("month").getAsInt(),
                            obj.get("day").getAsInt(),
                            obj.has("iconType") ? obj.get("iconType").getAsString() : CalendarEvent.ICON_NONE,
                            obj.has("iconId") ? obj.get("iconId").getAsString() : "",
                            obj.has("description") ? obj.get("description").getAsString() : "");
                    put(event);
                    merged++;
                } catch (Exception e) {
                    skipped++;
                }
            }
            if (merged > 0) {
                setDirty();
                writeMirror();
                com.fst.tothesky.ToTheSky.LOGGER.info("[日历] 从镜像合并 {} 条 SavedData 缺失的事件", merged);
            }
            if (skipped > 0) {
                com.fst.tothesky.ToTheSky.LOGGER.warn("[日历] 镜像中 {} 条脏数据被跳过", skipped);
            }
        } catch (Exception e) {
            com.fst.tothesky.ToTheSky.LOGGER.warn("[日历] 镜像合并失败（忽略）: {}", e.getMessage());
        }
    }

    /** 解析 id：字符串 UUID 或历史 Gson int[4] 数组格式；失败返回 null */
    @Nullable
    private static java.util.UUID readId(com.google.gson.JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                return java.util.UUID.fromString(element.getAsString());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (element.isJsonArray() && element.getAsJsonArray().size() == 4) {
            try {
                long most = (element.getAsJsonArray().get(0).getAsLong() & 0xFFFFFFFFL) << 32
                        | (element.getAsJsonArray().get(1).getAsLong() & 0xFFFFFFFFL);
                long least = (element.getAsJsonArray().get(2).getAsLong() & 0xFFFFFFFFL) << 32
                        | (element.getAsJsonArray().get(3).getAsLong() & 0xFFFFFFFFL);
                return new java.util.UUID(most, least);
            } catch (NumberFormatException | IllegalStateException e) {
                return null;
            }
        }
        return null;
    }
}