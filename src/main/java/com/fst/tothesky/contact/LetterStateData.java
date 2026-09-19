package com.fst.tothesky.contact;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节日信排期状态（服务器级 SavedData，存于主世界 {@code data/tothesky_letters.dat}）。
 *
 * <p>每条记录 = 一个排期键（「信件 + 收件人 + 日期」，见 {@code LetterScheduler#recipientKey}）
 * 对应的**下次投递日**（{@link java.time.LocalDate#toEpochDay()}）。定义本身不落盘——
 * {@code config/tothesky/letters} 里的文件才是事实来源，改文件下次扫描即生效；
 * 日历里的日期改了 = 换键 = 从头排期，所以这里不需要再记日期字符串。
 *
 * <p>持久化 {@code nextDueDay} 是为了补投：某封信该在昨天投递而服务器没开，
 * 今天开机时 {@code today >= nextDueDay} 直接补上，不会因为错过时刻而永久作废。
 *
 * <p>只在服务器线程读写；信件量级极小，全量序列化。
 */
public final class LetterStateData extends SavedData {
    public static final String DATA_NAME = "tothesky_letters";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_ID = "id";
    private static final String TAG_NEXT_DUE = "next_due";

    /** 排期键（{@code 信件id|收件人|日期}）→ 下次投递日 */
    private static final class Entry {
        private long nextDue;

        private Entry(long nextDue) {
            this.nextDue = nextDue;
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    private LetterStateData() {
    }

    /** 读取/创建（服务器级：挂在主世界上） */
    public static LetterStateData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                LetterStateData::read, LetterStateData::new, DATA_NAME);
    }

    private static LetterStateData read(CompoundTag tag) {
        LetterStateData data = new LetterStateData();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString(TAG_ID);
            if (id.isEmpty()) {
                continue;
            }
            // 旧版本还存了 spec（日期字符串）：键里已经含日期，多出来的标签读时直接忽略
            data.entries.put(id, new Entry(entry.getLong(TAG_NEXT_DUE)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Entry> each : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_ID, each.getKey());
            entryTag.putLong(TAG_NEXT_DUE, each.getValue().nextDue);
            list.add(entryTag);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    /** 该排期的下次投递日（epochDay）；没有记录返回 null */
    @Nullable
    public Long nextDue(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.nextDue;
    }

    /** 记录/更新一条排期 */
    public void put(String id, long nextDue) {
        Entry entry = entries.get(id);
        if (entry == null) {
            entries.put(id, new Entry(nextDue));
        } else {
            entry.nextDue = nextDue;
        }
        setDirty();
    }

    /** 丢掉本轮没再见到的排期（信件被删、日历里的日期改了都算）；日后放回时从头算 */
    public void retain(Set<String> ids) {
        if (entries.keySet().retainAll(ids)) {
            setDirty();
        }
    }

    /** 已记录的排期条数（诊断用） */
    public int size() {
        return entries.size();
    }

    /** 全部 id（诊断用） */
    public List<String> ids() {
        return new ArrayList<>(entries.keySet());
    }
}
