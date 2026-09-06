package com.fst.tothesky.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务器级检查站坐标索引（SavedData，存于世界 data/ 下），与
 * config/CheckerData/posList.txt 保持同步（该文件供运营阅读/手工编辑）。
 * 移植自 kubejs checker_server.js 中 server.persistentData.allCheckerPos
 * （重启丢失问题由 SavedData + posList.txt 双写解决）。
 */
public final class CheckerIndexData extends SavedData {
    public static final String DATA_NAME = "tothesky_checker_index";
    private static final String TAG_POSITIONS = "positions";

    /** 插入序迭代（与旧脚本 persistentData 字符串中的顺序一致） */
    private final Set<BlockPos> positions = new LinkedHashSet<>();

    private CheckerIndexData() {
    }

    /** 读取/创建（服务器级：挂在主世界上，含全部维度共用的检查站） */
    public static CheckerIndexData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CheckerIndexData::read, CheckerIndexData::new, DATA_NAME);
    }

    private static CheckerIndexData read(CompoundTag tag) {
        CheckerIndexData data = new CheckerIndexData();
        ListTag list = tag.getList(TAG_POSITIONS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            BlockPos pos = parsePos(list.getString(i));
            if (pos != null) {
                data.positions.add(pos);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(StringTag.valueOf(pos.getX() + "," + pos.getY() + "," + pos.getZ()));
        }
        tag.put(TAG_POSITIONS, list);
        return tag;
    }

    /** 解析 "x,y,z"；格式不对时返回 null（容忍手工编辑与旧文件中的脏行） */
    static BlockPos parsePos(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public Set<BlockPos> positions() {
        return Collections.unmodifiableSet(positions);
    }

    public boolean add(BlockPos pos) {
        if (positions.add(pos)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean remove(BlockPos pos) {
        if (positions.remove(pos)) {
            setDirty();
            return true;
        }
        return false;
    }
}
