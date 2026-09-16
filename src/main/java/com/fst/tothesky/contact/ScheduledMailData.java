package com.fst.tothesky.contact;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时邮件排期（服务器级 SavedData，存于主世界 {@code data/tothesky_contact_mail.dat}）。
 *
 * <p>每条 = 「收件人昵称 + 投递日（{@link java.time.LocalDate#toEpochDay()}）+ 邮件物品 NBT」。
 * 用 epochDay 而不是时间戳：投递粒度本来就是「天」，日期比较也就没有时区/夏令时算术。
 *
 * <p>收件人存昵称而非 UUID：排期时收件人可能还没进过服、UUID 无从可查，
 * 解析推迟到投递时刻（见 {@link ContactMailScheduler}）。名字区分大小写地原样保存。
 *
 * <p>只在服务器线程读写；邮件量级小，全量序列化。
 */
public final class ScheduledMailData extends SavedData {
    public static final String DATA_NAME = "tothesky_contact_mail";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_RECIPIENT = "recipient";
    private static final String TAG_DAY = "day";
    private static final String TAG_MAIL = "mail";

    /**
     * 一条待投递邮件。
     * <p>刻意不覆写 {@code equals}：调度器按**引用**移除已投递的条目，
     * 避免 {@link ItemStack#equals} 的 NBT 深比较带来「误删同内容邮件」的风险。
     */
    public static final class Entry {
        private final String recipient;
        private final long day;
        private final ItemStack mail;
        /** 本条目已因「解析不到收件人/投递异常」告过警（仅本次进程有效，不落盘） */
        private boolean warned;

        private Entry(String recipient, long day, ItemStack mail) {
            this.recipient = recipient;
            this.day = day;
            this.mail = mail;
        }

        /** 收件人昵称（投递时解析成 UUID） */
        public String recipient() {
            return recipient;
        }

        /** 投递日（{@link java.time.LocalDate#toEpochDay()}） */
        public long day() {
            return day;
        }

        public ItemStack mail() {
            return mail;
        }

        /**
         * 记一次投递未成。
         *
         * @return 是否应当打日志（每条只报一次：调度每秒重试，否则会刷屏）
         */
        public boolean noteFailure() {
            if (warned) {
                return false;
            }
            warned = true;
            return true;
        }
    }

    /** 昵称最长 16 字符（Minecraft 账号名上限）；最短放宽到 1，兼容离线模式的短名 */
    private static final int NAME_MAX_LENGTH = 16;

    private final List<Entry> entries = new ArrayList<>();

    private ScheduledMailData() {
    }

    /**
     * 昵称格式校验：非空白、≤16 字符、只含 {@code [A-Za-z0-9_]}（Minecraft 账号名字符集）。
     * <p>不校验「是否真的存在」——那要等投递时解析（见 {@link ContactMailScheduler}）。
     * <p>放在存储层：既是写入前的把关，也是读档时丢弃脏条目的依据；
     * 且不牵扯 {@link ContactMail} 的静态初始化（那里会摸 {@code ModList}）。
     */
    static boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 读取/创建（服务器级：挂在主世界上） */
    public static ScheduledMailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ScheduledMailData::read, ScheduledMailData::new, DATA_NAME);
    }

    private static ScheduledMailData read(CompoundTag tag) {
        ScheduledMailData data = new ScheduledMailData();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String recipient = entry.getString(TAG_RECIPIENT);
            if (!isValidPlayerName(recipient) || !entry.contains(TAG_MAIL, Tag.TAG_COMPOUND)) {
                continue;
            }
            ItemStack mail = ItemStack.of(entry.getCompound(TAG_MAIL));
            if (mail.isEmpty()) {
                continue;
            }
            data.entries.add(new Entry(recipient, entry.getLong(TAG_DAY), mail));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_RECIPIENT, entry.recipient);
            entryTag.putLong(TAG_DAY, entry.day);
            entryTag.put(TAG_MAIL, entry.mail.save(new CompoundTag()));
            list.add(entryTag);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    /** 新增一条排期（邮件物品在此脱离调用方的栈，防止后续被改动） */
    public void add(String recipient, long day, ItemStack mail) {
        entries.add(new Entry(recipient, day, mail.copy()));
        setDirty();
    }

    /** 到期条目快照（投递日 ≤ {@code today}，即该日 00:00 起算已到点） */
    public List<Entry> due(long today) {
        List<Entry> due = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.day <= today) {
                due.add(entry);
            }
        }
        return due;
    }

    /** 按引用移除（投递成功后调用） */
    public void remove(Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == entry) {
                entries.remove(i);
                setDirty();
                return;
            }
        }
    }

    /** 待投递条目数（诊断用） */
    public int size() {
        return entries.size();
    }
}
