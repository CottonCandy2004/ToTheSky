package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.item.EmptyMusicSheetItem;
import com.fst.tothesky.item.InstrumentItem;
import com.fst.tothesky.item.MusicSheetItem;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModSounds;
import com.fst.tothesky.util.DelayedTasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 乐器系统（吉他 / 电子琴 / 电子鼓机 / 空白乐谱 / 乐谱）完整移植。
 *
 * <p>对应 RiaFST 4 的 kubejs 脚本 {@code kubejs/server_scripts/feature/instruments_server.js}
 * （561 行，RiaFST 4 版），包含五个模式（选择 / 独奏 / 加入合奏 / 引导合奏 / 退出演奏）、
 * 乐谱写入与学习、成书 → 乐谱转换、音符按 tick 递归播放。所有 actionbar/tell 文本与脚本逐字一致。
 *
 * <p>移植对照：
 * <ul>
 *   <li>乐器判定：kjs tag {@code kubejs:instruments} → {@code instanceof InstrumentItem}</li>
 *   <li>玩家 persistentData → {@code ServerPlayer#getPersistentData()}（键名不变）</li>
 *   <li>音符数据存物品 NBT：{@code allNodes}（空格分隔串）、{@code songName}、{@code length}、
 *       {@code allPlayers}（键名与旧脚本一致）</li>
 *   <li>文件读写 {@code config/musicSheets/<玩家名>/}（java.nio.file，相对游戏 config 目录）</li>
 *   <li>递归延时：{@link DelayedTasks#schedule}（等价 server.scheduleInTicks，SECOND = 20tick）</li>
 *   <li>登录时清除 isPlaying（PlayerEvents.loggedIn）</li>
 * </ul>
 *
 * <p>音效映射说明（参考脚本 instTypes/drumTypes）：脚本里吉他音效为本模组唯一注册的
 * ModSounds.GUITAR_SOUND；钢琴（ywzj_midi:cfx_fs4）与鼓 3-7 号（ywzj_midi/chinjufumod）
 * 音效依赖的外部模组不存在，按“若脚本只给吉他注册了就全部用吉他音”回退为吉他音；
 * 鼓 0-2 号（minecraft:block.note_block.snare/hat/basedrum）与脚本一致保留原版音效。
 * 新模组未注册班卓琴物品，instTypes 中其条目不参与。
 *
 * <p>有意修正的脚本缺陷（均已避免 1:1 复制 bug）：
 * <ul>
 *   <li>quitChoir 重建 leader 名单时引用了循环后的过期下标 i（会写出 "undefined" 名单）→ 按意图重建</li>
 *   <li>startSolo/readNote/findChoir/joinChoir 里“若在引导则 leadChoir(event)”少传乐器参数会直接抛错、
 *       导致无法停止引导 → 补上当前主手乐器正常执行停止引导</li>
 *   <li>readNote/leadChoir 依据 displayName 的 getString().slice(1,-1) 复原名字：服务端对无自定义名物品
 *       会得到乱码 → 有自定义名时保留原名，无自定义名时不再覆写（保持默认物品名）</li>
 *   <li>成书 → 乐谱页 JSON 解析按单页分别解析并宽容提取 text/extra，避免脚本对多页/纯文本页必然崩溃</li>
 *   <li>learnSong 文件名做了非法字符净化（原脚本对含路径分隔符的书名会写出任意路径）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID)
public final class InstrumentsEvents {
    private InstrumentsEvents() {
    }

    // 玩家 persistentData 键（与脚本一致）
    private static final String TAG_IS_PLAYING = "isPlaying";
    private static final String TAG_IS_IN_CHOIR = "isInChoir";
    private static final String TAG_IS_LEADING_CHOIR = "isLeadingChoir";
    private static final String TAG_INST_MOD_TYPE = "instModType";
    private static final String TAG_SHEET_INDEX = "sheetIndex";
    private static final String TAG_NOTE_INDEX = "noteIndex";
    private static final String TAG_NODE_INTERVAL = "nodeInterval";
    private static final String TAG_CHOIR_LEAD = "choirLead";
    // 物品 NBT 键（与脚本一致）
    private static final String TAG_ALL_NODES = "allNodes";
    private static final String TAG_SONG_NAME = "songName";
    private static final String TAG_LENGTH = "length";
    private static final String TAG_ALL_PLAYERS = "allPlayers";

    private static final String[] INST_MOD_TYPES = {"1.乐谱选择模式", "2.独奏", "3.加入合奏", "4.引导合奏", "5.结束演奏"};

    /** 旋律类音量（脚本 allVolum：kubejs:guitar 0.3 / kubejs:piano 0.8） */
    private static final float VOLUME_GUITAR = 0.3F;
    private static final float VOLUME_PIANO = 0.8F;

    /** 鼓音量（脚本 drumVolum，按音符值取 0-7 号） */
    private static final float[] DRUM_VOLUMES = {1.2F, 1.2F, 1.2F, 0.8F, 0.8F, 0.8F, 1.0F, 1.0F};

    /** 音符值 → 音高倍率表（脚本 allPitch，0-24 号） */
    private static final float[] ALL_PITCH = {
            0.5F, 0.529732F, 0.561231F, 0.594604F, 0.629961F, 0.667420F, 0.707107F, 0.749154F,
            0.793701F, 0.840896F, 0.890899F, 0.943874F, 1.0F, 1.059463F, 1.122462F, 1.189207F,
            1.259921F, 1.334840F, 1.414214F, 1.498307F, 1.587401F, 1.681793F, 1.781797F,
            1.887749F, 2.0F};

    /** 附近合奏搜索范围（boundingBox.inflate(40)） */
    private static final double CHOIR_SEARCH_INFLATE = 40.0D;
    /** 播放音效接收范围（execute … playsound … player @a[distance=..20]） */
    private static final double SOUND_TARGET_RANGE = 20.0D;

    // ==================== 事件入口 ====================

    /** PlayerEvents.loggedIn → 清除 isPlaying */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove(TAG_IS_PLAYING);
        }
    }

    /** ItemEvents.rightClicked（乐器 + 空白乐谱 + 乐谱） */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof InstrumentItem) {
            CompoundTag data = player.getPersistentData();
            if (player.isCrouching()) {
                switchInstMod(true, player);
            } else {
                if (!data.contains(TAG_INST_MOD_TYPE)) {
                    data.putInt(TAG_INST_MOD_TYPE, 0);
                }
                switch (data.getInt(TAG_INST_MOD_TYPE)) {
                    case 0 -> readFiles(false, player);
                    case 1 -> startSolo(player);
                    case 2 -> findChoir(player);
                    case 3 -> leadChoir(player, player.getMainHandItem());
                    case 4 -> quitPerform(player);
                    default -> {
                    }
                }
            }
        } else if (stack.getItem() instanceof EmptyMusicSheetItem) {
            convertWrittenBookToSheet(player);
        } else if (stack.getItem() instanceof MusicSheetItem) {
            learnSong(player);
        }
    }

    /** ItemEvents.firstLeftClicked（LeftClickEmpty）——只有乐器参与 */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof InstrumentItem)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (player.isCrouching()) {
            switchInstMod(false, player);
        } else {
            // 脚本里 switch(undefined) 不会有任何 case 命中 → 从未设置模式时左键无操作
            if (!data.contains(TAG_INST_MOD_TYPE)) {
                return;
            }
            switch (data.getInt(TAG_INST_MOD_TYPE)) {
                case 0 -> readFiles(false, player);
                case 2 -> joinChoir(player);
                case 3 -> startMultiple(player);
                default -> {
                }
            }
        }
    }

    // ==================== 模式切换 ====================

    /** switchInstMod：下蹲 + 右键(+1)/左键(-1)，循环 0-4 */
    private static void switchInstMod(boolean isRight, ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_INST_MOD_TYPE)) {
            data.putInt(TAG_INST_MOD_TYPE, 1);
        }
        int mode = data.getInt(TAG_INST_MOD_TYPE) + (isRight ? 1 : -1);
        if (mode < 0) {
            mode = 4;
        }
        if (mode > 4) {
            mode = 0;
        }
        data.putInt(TAG_INST_MOD_TYPE, mode);
        sendActionbar(player, Component.literal("[鼠标切换] 当前模式：").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(INST_MOD_TYPES[mode]).withStyle(ChatFormatting.WHITE)));
    }

    // ==================== 选择模式（0） ====================

    /** readFiles：读目录并切换曲目 */
    private static void readFiles(boolean isRight, ServerPlayer player) {
        Path dir = sheetDir(player);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("创建乐谱目录失败: {}", dir, e);
        }
        List<String> files = listSheetFiles(dir);
        if (files.isEmpty()) {
            // 未发现乐谱！(0文件位于config/musicSheets/xxx)
            sendActionbar(player, red("未发现乐谱！(0文件位于config/musicSheets/" + player.getName().getString() + ")"));
            return;
        }
        switchSheet(isRight, player, files);
        readNote(player, files);
    }

    /** switchSheet：左右切换选中的乐谱（只更新索引并播报） */
    private static void switchSheet(boolean isRight, ServerPlayer player, List<String> files) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_SHEET_INDEX)) {
            data.putInt(TAG_SHEET_INDEX, 0);
        }
        int index = data.getInt(TAG_SHEET_INDEX) + (isRight ? 1 : -1);
        if (index < 0) {
            index = files.size() - 1;
        }
        if (index > files.size() - 1) {
            index = 0;
        }
        data.putInt(TAG_SHEET_INDEX, index);
        String fileName = files.get(index);
        sendActionbar(player, Component.literal("[鼠标切换] 当前曲目：").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal((index + 1) + "." + fileName).withStyle(ChatFormatting.WHITE)));
    }

    /** readNote：读取选中乐谱写入乐器 NBT（乐谱格式：第 1 行 bpm、第 2 行拍值、第 3 行 1=鼓谱，之后每行一个音符） */
    private static void readNote(ServerPlayer player, List<String> files) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            // 如果在引导：停止引导（原脚本此处漏传乐器参数会抛错，见类注释）
            leadChoir(player, player.getMainHandItem());
        }
        if (data.getBoolean(TAG_IS_IN_CHOIR)) {
            quitChoir(player, player);
        }
        data.putInt(TAG_NOTE_INDEX, 3);
        int sheetIndex = data.getInt(TAG_SHEET_INDEX);
        if (sheetIndex < 0 || sheetIndex >= files.size()) {
            return;
        }
        Path file = sheetDir(player).resolve(files.get(sheetIndex));
        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            return;
        }
        String content = String.join(" ", lines);

        // 乐谱与乐器匹配检查（与脚本一致：drum_808 只能读第 3 行为 1 的谱，其他乐器不能读鼓谱；
        // 解析失败/越界按 JS parseInt = NaN 处理：鼓谱报不匹配、普通谱可继续）
        boolean isDrumItem = player.getMainHandItem().is(ModItems.DRUM_808.get());
        boolean markerIsOne = jsParseInt(lineAt(lines, 2)).orElse(0) == 1;
        if (isDrumItem != markerIsOne) {
            chat(player, red("乐谱与乐器不匹配！"));
            return;
        }

        // nodeInterval = 60 / bpm / 拍值 * 4（秒）
        int bpm = jsParseInt(lines.get(0)).orElse(0);
        int divisor = jsParseInt(lines.size() > 1 ? lines.get(1) : "").orElse(0);
        data.putFloat(TAG_NODE_INTERVAL, bpm > 0 && divisor > 0 ? 60F / bpm / divisor * 4F : Float.NaN);

        String songName = stripExtension(files.get(sheetIndex));
        ItemStack instrument = player.getMainHandItem();
        ItemStack renamed = buildPreparedInstrument(instrument, displayTextOrNull(instrument), songName, content,
                lines.size());
        player.getInventory().setItem(player.getInventory().selected, renamed);
    }

    // ==================== 独奏模式（1） ====================

    /** startSolo */
    private static void startSolo(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            leadChoir(player, player.getMainHandItem());
        }
        if (data.getBoolean(TAG_IS_IN_CHOIR)) {
            quitChoir(player, player);
        }
        data.putBoolean(TAG_IS_PLAYING, true);
        data.putInt(TAG_NOTE_INDEX, 3);
        playNote(player, player.getMainHandItem(), player);
    }

    // ==================== 加入合奏模式（2） ====================

    /** findChoir：寻找附近（40 格）正在引导的合奏 */
    private static void findChoir(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            leadChoir(player, player.getMainHandItem());
        }
        boolean found = false;
        for (ServerPlayer near : nearbyPlayers(player)) {
            if (!found && dataOf(near).getBoolean(TAG_IS_LEADING_CHOIR)
                    && near.getMainHandItem().getItem() instanceof InstrumentItem) {
                found = true;
                sendActionbar(player, yellow("在附近发现" + near.getName().getString() + "的合奏！[左键]加入"));
            }
        }
        if (!found) {
            sendActionbar(player, red("未在附近发现合奏！[右键]再次查找"));
        }
    }

    /** joinChoir：加入附近（40 格）正在引导的合奏 */
    private static void joinChoir(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            leadChoir(player, player.getMainHandItem());
        }
        boolean found = false;
        for (ServerPlayer near : nearbyPlayers(player)) {
            if (!found && dataOf(near).getBoolean(TAG_IS_LEADING_CHOIR)
                    && near.getMainHandItem().getItem() instanceof InstrumentItem) {
                ItemStack leaderItem = near.getMainHandItem();
                CompoundTag itemTag = leaderItem.getOrCreateTag();
                String nameList = itemTag.getString(TAG_ALL_PLAYERS);
                itemTag.putString(TAG_ALL_PLAYERS, nameList + " " + player.getName().getString());
                data.putString(TAG_CHOIR_LEAD, near.getName().getString());
                found = true;
                data.putBoolean(TAG_IS_IN_CHOIR, true);
                sendActionbar(player, yellow("你已加入" + near.getName().getString() + "的合奏！[右键]退出"));
                sendActionbar(near, green(player.getName().getString() + "加入了你的合奏！[左键]开始演奏 | [右键]取消引导"));
            }
        }
        if (!found) {
            sendActionbar(player, red("未在附近发现合奏！[右键]再次查找"));
        }
    }

    // ==================== 引导合奏模式（3） ====================

    /** leadChoir：开始引导 / 取消引导（复原乐器） */
    private static void leadChoir(ServerPlayer player, ItemStack aimItem) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_IS_LEADING_CHOIR)) {
            data.putBoolean(TAG_IS_LEADING_CHOIR, true);
        }
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        String songName = aimItem.getTag() != null && aimItem.getTag().contains(TAG_SONG_NAME, Tag.TAG_STRING)
                ? aimItem.getTag().getString(TAG_SONG_NAME) : null;
        List<String> files = Collections.emptyList();
        if (songName == null) {
            // 无记录时回退到当前选中的谱面文件名
            files = listSheetFiles(sheetDir(player));
            int sheetIndex = data.getInt(TAG_SHEET_INDEX);
            if (files.isEmpty() || sheetIndex < 0 || sheetIndex >= files.size()) {
                return; // 原脚本此处 files[sheetIndex] 为 undefined 会抛错中止，等价于什么都不做
            }
            songName = stripExtension(files.get(sheetIndex));
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            // 停止引导，复原物品
            data.putBoolean(TAG_IS_LEADING_CHOIR, false);
            sendActionbar(player, yellow("你已取消引导合奏"));
            net.minecraft.world.Container inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack slotItem = inventory.getItem(i);
                if (slotItem != null && !slotItem.isEmpty() && slotItem.is(aimItem.getItem())
                        && slotItem.hasTag() && sameAllNodes(slotItem, aimItem)) {
                    ItemStack restored = new ItemStack(slotItem.getItem(), 1);
                    String itemName = displayTextOrNull(slotItem);
                    if (itemName != null) {
                        setCustomName(restored, itemName);
                    }
                    addLore(restored, Component.literal("准备演奏的曲目：" + songName)
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false)));
                    String nodes = slotItem.getTag() != null && slotItem.getTag().contains(TAG_ALL_NODES, Tag.TAG_STRING)
                            ? slotItem.getTag().getString(TAG_ALL_NODES) : null;
                    if (nodes != null) {
                        restored.getOrCreateTag().putString(TAG_ALL_NODES, nodes);
                    }
                    restored.getOrCreateTag().putString(TAG_SONG_NAME, songName);
                    inventory.setItem(i, restored);
                    break;
                }
            }
            return;
        }
        if (!data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            // 开始引导，生成名单
            data.putBoolean(TAG_IS_LEADING_CHOIR, true);
            sendActionbar(player, yellow("你已开始引导合奏！[左键]开始演奏 | [右键]取消引导"));
            ItemStack instrument = player.getMainHandItem();
            ItemStack leading = buildPreparedInstrument(instrument, displayTextOrNull(instrument), songName,
                    tagStringOrNull(instrument, TAG_ALL_NODES), tagIntOrZero(instrument, TAG_LENGTH));
            addLore(leading, Component.literal("正在引导合奏")
                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
            leading.getOrCreateTag().putString(TAG_ALL_PLAYERS, player.getName().getString());
            player.getInventory().setItem(player.getInventory().selected, leading);
        }
    }

    /** startMultiple：引导者左键开始全员合奏 */
    private static void startMultiple(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            sendActionbar(player, red("请先引导合奏"));
            return;
        }
        if (data.getBoolean(TAG_IS_PLAYING)) {
            sendActionbar(player, red("你还在演奏中"));
            return;
        }
        ItemStack leaderItem = player.getMainHandItem();
        List<String> rosterNames = splitNames(tagStringOrNull(leaderItem, TAG_ALL_PLAYERS));
        List<ServerPlayer> choirPlayers = new ArrayList<>();
        choirPlayers.add(player);
        for (ServerPlayer e : serverLevelOf(player).players()) {
            if (e != player && rosterNames.contains(e.getName().getString())) {
                choirPlayers.add(e);
            }
        }
        for (ServerPlayer element : choirPlayers) {
            if (!(element.getMainHandItem().getItem() instanceof InstrumentItem)) {
                // 成员没有手持乐器 → 踢出合奏（消息按脚本发给 event.player，即引导者）
                quitChoir(player, element);
            } else {
                CompoundTag elementData = dataOf(element);
                elementData.putBoolean(TAG_IS_PLAYING, true);
                elementData.putInt(TAG_NOTE_INDEX, 3);
                playNote(player, element.getMainHandItem(), element);
            }
        }
    }

    // ==================== 退出合奏 / 结束演奏 ====================

    /** quitChoir：把 member 移出合奏。trigger 用于定位“事件玩家”（消息接收者，原脚本 event.player） */
    private static void quitChoir(ServerPlayer trigger, ServerPlayer member) {
        CompoundTag memberData = member.getPersistentData();
        memberData.putBoolean(TAG_IS_IN_CHOIR, false);
        String leadName = memberData.getString(TAG_CHOIR_LEAD);
        for (ServerPlayer element : serverLevelOf(member).players()) {
            if (element.getName().getString().equals(leadName) && dataOf(element).getBoolean(TAG_IS_LEADING_CHOIR)) {
                if (!(element.getMainHandItem().getItem() instanceof InstrumentItem)) {
                    // 引导玩家已不手持乐器：直接结束引导
                    dataOf(element).putBoolean(TAG_IS_LEADING_CHOIR, false);
                } else {
                    // 更新引导者乐器上的成员名单
                    ItemStack leaderItem = element.getMainHandItem();
                    List<String> nameList = splitNames(tagStringOrNull(leaderItem, TAG_ALL_PLAYERS));
                    nameList.remove(member.getName().getString());
                    StringBuilder newList = new StringBuilder();
                    for (String name : nameList) {
                        newList.append(' ').append(name);
                    }
                    leaderItem.getOrCreateTag().putString(TAG_ALL_PLAYERS, newList.toString());
                }
            }
        }
        sendActionbar(trigger, yellow("你已退出合奏"));
    }

    /** quitPerform：结束演奏（模式 5） */
    private static void quitPerform(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(TAG_IS_PLAYING, false);
        if (data.getBoolean(TAG_IS_IN_CHOIR)) {
            quitChoir(player, player);
        }
        if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            leadChoir(player, player.getMainHandItem());
        }
        sendActionbar(player, yellow("你已结束演奏"));
    }

    // ==================== 音符递归播放 ====================

    /**
     * playNote（递归）：立即校验，然后按 nodeInterval 秒（*20 tick）调度下一个音符；
     * 音符 token 为空格分隔的 allNodes 中的一项，形如 "3,7"（逗号 = 同 tick 多音）或 "-1"（休止）。
     */
    private static void playNote(ServerPlayer trigger, ItemStack item, ServerPlayer player) {
        if (player.getMainHandItem() != item || !(trigger.getMainHandItem().getItem() instanceof InstrumentItem)) {
            sendActionbar(player, yellow("你已结束演奏"));
            CompoundTag data = player.getPersistentData();
            data.putBoolean(TAG_IS_PLAYING, false);
            if (data.getBoolean(TAG_IS_IN_CHOIR)) {
                quitChoir(trigger, player);
            }
            if (data.getBoolean(TAG_IS_LEADING_CHOIR)) {
                leadChoir(trigger, item);
            }
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_IS_PLAYING) && !data.getBoolean(TAG_IS_IN_CHOIR) && !data.getBoolean(TAG_IS_LEADING_CHOIR)) {
            return;
        }
        double intervalSeconds = data.getFloat(TAG_NODE_INTERVAL);
        int delayTicks = Double.isNaN(intervalSeconds) ? 0 : (int) Math.round(intervalSeconds * 20F);
        MinecraftServer server = serverLevelOf(player).getServer();
        DelayedTasks.schedule(server, Math.max(0, delayTicks), () -> {
            CompoundTag d = player.getPersistentData();
            CompoundTag itemTag = item.getTag();
            int length = itemTag != null && itemTag.contains(TAG_LENGTH, Tag.TAG_INT) ? itemTag.getInt(TAG_LENGTH) : 0;
            int noteIndex = d.getInt(TAG_NOTE_INDEX);
            if (length > 0 && noteIndex >= length) {
                noteIndex = 3;
            }
            String allNodes = itemTag != null && itemTag.contains(TAG_ALL_NODES, Tag.TAG_STRING)
                    ? itemTag.getString(TAG_ALL_NODES) : "";
            String[] tokens = allNodes.split(" ");
            if (noteIndex < 0 || noteIndex >= tokens.length) {
                // 谱面 token 已耗尽（无 length 回绕时原脚本会在 undefined.split 处抛错、递归中断）
                return;
            }
            String multiNodes = tokens[noteIndex];
            if (multiNodes != null && !multiNodes.isEmpty() && !"-1".equals(multiNodes)) {
                // 第 3 个 token 为 1 → 鼓谱（与 readNote 的匹配检查一致；解析失败按 false 处理）
                boolean isDrum = jsParseInt(tokens.length > 2 ? tokens[2] : null).orElse(0) == 1;
                int[] noteValues = parseNoteValues(multiNodes);
                if (isDrum) {
                    for (int element : noteValues) {
                        if (element >= 0 && element < DRUM_VOLUMES.length) {
                            playSoundNear(player, drumSound(element), DRUM_VOLUMES[element], 1.0F);
                        }
                    }
                } else {
                    float volume = melodicVolume(item);
                    for (int element : noteValues) {
                        if (element >= 0 && element < ALL_PITCH.length) {
                            playSoundNear(player, ModSounds.GUITAR_SOUND.get(), volume, ALL_PITCH[element]);
                        }
                    }
                }
                spawnNoteParticle(player);
            }
            d.putInt(TAG_NOTE_INDEX, noteIndex + 1);
            playNote(trigger, item, player);
        });
    }

    /** 解析 "1,3,7" 之类的音符值串（原脚本 split(",").map(Number)），无法解析的值被丢弃（原脚本会静默无声） */
    private static int[] parseNoteValues(String multiNodes) {
        String[] parts = multiNodes.split(",");
        List<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            OptionalInt v = jsParseInt(part);
            if (v.isPresent()) {
                values.add(v.getAsInt());
            }
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 在演奏者附近 ≤20 格播放音效（对应 playsound … player @a[distance=..20] ~ ~ ~） */
    private static void playSoundNear(ServerPlayer performer, SoundEvent sound, float volume, float pitch) {
        if (!isOnline(performer)) {
            return;
        }
        ServerLevel level = serverLevelOf(performer);
        double x = performer.getX();
        double y = performer.getY();
        double z = performer.getZ();
        try {
            Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
            ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.PLAYERS, x, y, z,
                    volume, pitch, level.random.nextLong());
            for (ServerPlayer listener : level.players()) {
                if (listener.distanceToSqr(x, y, z) <= SOUND_TARGET_RANGE * SOUND_TARGET_RANGE) {
                    listener.connection.send(packet);
                }
            }
        } catch (RuntimeException e) {
            ToTheSky.LOGGER.debug("播放乐器音效失败: {}", sound.getLocation(), e);
        }
    }

    /** /particle note ~ ~1.5 ~ 0.25 0.25 0.25 0.05 1 normal */
    private static void spawnNoteParticle(ServerPlayer performer) {
        if (!isOnline(performer)) {
            return;
        }
        ServerLevel level = serverLevelOf(performer);
        level.sendParticles(ParticleTypes.NOTE, performer.getX(), performer.getY() + 1.5D,
                performer.getZ(), 1, 0.25D, 0.25D, 0.25D, 0.05D);
    }

    /**
     * 鼓音色：0-2 号与脚本一致保留原版音符盒音效；3-7 号在脚本里来自 ywzj_midi / chinjufumod
     * （目标模组不含这些外部模组），按回退规则全部改用吉他音。
     */
    private static SoundEvent drumSound(int element) {
        // 1.20.1 SoundEvents 字段是 Holder.Reference，取 .value()
        return switch (element) {
            case 0 -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case 1 -> SoundEvents.NOTE_BLOCK_HAT.value();
            case 2 -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            default -> ModSounds.GUITAR_SOUND.get();
        };
    }

    /** allVolum[item.id]：吉他 0.3 / 电子琴 0.8，未列出的乐器回退 1.0（脚本里是 undefined 静默无声） */
    private static float melodicVolume(ItemStack instrument) {
        if (instrument.is(ModItems.GUITAR.get())) {
            return VOLUME_GUITAR;
        }
        if (instrument.is(ModItems.PIANO.get())) {
            return VOLUME_PIANO;
        }
        return 1.0F;
    }

    // ==================== 空白乐谱 → 乐谱（成书转换） ====================

    /**
     * ItemEvents.rightClicked("kubejs:empty_music_sheet")：副手成书 + 主手空白乐谱右键。
     * 原脚本把 pages 写到 config/musicSheets/temp.txt 再读回、清理引号/换行后整体 JSON.parse；
     * 这里直接逐页解析并宽容提取 text/extra 文本，容错语义与 fixJson/清理逻辑等价，产出 allNodes 一致。
     */
    private static void convertWrittenBookToSheet(ServerPlayer player) {
        Path dir = sheetDir(player);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ToTheSky.LOGGER.warn("创建乐谱目录失败: {}", dir, e);
        }
        ItemStack book = player.getOffhandItem();
        if (!book.is(Items.WRITTEN_BOOK)) {
            return;
        }
        CompoundTag bookTag = book.getTag();
        if (bookTag == null || !bookTag.contains("pages", Tag.TAG_STRING)) {
            return;
        }
        ListTag pages = bookTag.getList("pages", Tag.TAG_STRING);
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            List<String> pageFragments = parsePageFragments(player, pages.getString(i));
            if (pageFragments == null) {
                return; // 解析失败已提示玩家
            }
            if (i == 0) {
                // 脚本只取第一页的第一段 text（其余随 forEach 的 element != nbtJson[0] 被跳过）
                if (!pageFragments.isEmpty()) {
                    fragments.add(pageFragments.get(0));
                }
            } else {
                fragments.addAll(pageFragments);
            }
        }
        String content = fragments.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ").trim();
        if (content.isEmpty()) {
            return;
        }
        String songName = bookTitleText(bookTag.getString("title"));
        String author = bookTag.getString("author");
        ItemStack sheet = new ItemStack(ModItems.MUSIC_SHEET.get(), 1);
        setCustomName(sheet, Component.literal(songName + "的乐谱")
                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
        addLore(sheet, Component.literal("记录者：" + author)
                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
        sheet.getOrCreateTag().putString(TAG_ALL_NODES, content);
        if (!player.getInventory().add(sheet)) {
            player.drop(sheet, false);
        }
        player.getMainHandItem().shrink(1);
    }

    /**
     * 解析成书单页 JSON（数组或 {text,extra} 对象），按文档顺序收集所有文本片段。
     * 返回 null 表示解析失败（已向玩家播报，与脚本 JSON.parse 失败后 tell(error) 一致）。
     */
    private static List<String> parsePageFragments(ServerPlayer player, String pageJson) {
        JsonElement element;
        try {
            element = JsonParser.parseString(pageJson);
        } catch (JsonSyntaxException e) {
            // 脚本的清理逻辑：去 \' 、\\n 与单引号后再试一次
            String cleaned = pageJson.replaceFirst("\\\\'", "").replace("\\\\n", " ").replace("'", "");
            try {
                element = JsonParser.parseString(cleaned);
            } catch (JsonSyntaxException e2) {
                chat(player, Component.literal(e2.getMessage() == null ? e2.toString() : e2.getMessage()));
                return null;
            }
        }
        List<String> fragments = new ArrayList<>();
        collectText(element, fragments);
        return fragments;
    }

    private static void collectText(JsonElement element, List<String> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                collectText(child, out);
            }
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement text = object.get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                out.add(text.getAsString());
            }
            collectText(object.get("extra"), out);
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.add(element.getAsString());
        }
    }

    // ==================== 乐谱 → 学习（写入文件） ====================

    /** ItemEvents.rightClicked("kubejs:music_sheet")：把 allNodes 逐行写入 config/musicSheets/玩家/曲名.txt */
    private static void learnSong(ServerPlayer player) {
        ItemStack sheet = player.getMainHandItem();
        String musicName = displayTextOrNull(sheet);
        if (musicName == null) {
            return;
        }
        // 物品名 = “曲名 + 的乐谱”，去掉后缀还原曲名（脚本用 slice(1,-4) 同时剥掉显示名引号）
        if (musicName.endsWith("的乐谱")) {
            musicName = musicName.substring(0, musicName.length() - 3);
        }
        if (musicName.isEmpty()) {
            return;
        }
        String safeName = sanitizeFileName(musicName);
        String nodes = tagStringOrNull(sheet, TAG_ALL_NODES);
        String[] lines = nodes == null ? new String[0] : nodes.split(" ");
        try {
            Files.write(sheetDir(player).resolve(safeName + ".txt"), List.of(lines), StandardCharsets.UTF_8);
        } catch (IOException e) {
            chat(player, Component.literal(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        sendActionbar(player, green("你学会了新的歌曲：" + musicName));
    }

    // ==================== 小工具 ====================

    private static CompoundTag dataOf(ServerPlayer player) {
        return player.getPersistentData();
    }

    private static ServerLevel serverLevelOf(ServerPlayer player) {
        return player.serverLevel();
    }

    /** @a[name=…] 只有在线的玩家才会被选中：断线时跳过所有播报/音效（原脚本命令静默无目标） */
    private static boolean isOnline(ServerPlayer player) {
        return player.getServer() != null && player.getServer().getPlayerList().getPlayer(player.getUUID()) != null;
    }

    private static void sendActionbar(ServerPlayer player, Component component) {
        if (isOnline(player)) {
            player.displayClientMessage(component, true);
        }
    }

    private static void chat(ServerPlayer player, Component component) {
        if (player != null && isOnline(player)) {
            player.displayClientMessage(component, false);
        }
    }

    private static Component red(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    private static Component yellow(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    private static Component green(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    /** 玩家乐谱根目录（对应 kjs 相对路径 config/musicSheets） */
    private static Path sheetRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("musicSheets");
    }

    private static Path sheetDir(ServerPlayer player) {
        return sheetRoot().resolve(player.getName().getString());
    }

    /** 列出玩家目录下所有普通文件名（与原脚本 FilesJS.listFiles 相对，这里按文件名排序保证稳定） */
    private static List<String> listSheetFiles(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ToTheSky.LOGGER.debug("读取乐谱失败: {}", file, e);
            return Collections.emptyList();
        }
    }

    private static String lineAt(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "";
    }

    /** 文件名去扩展名：替换脚本的 match 去目录部分后按第一个点截断（取第一个点前的内容） */
    private static String stripExtension(String fileName) {
        int dot = fileName.indexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /** JS parseInt 语义：跳过前导空白、解析前导数字，失败返回 empty（对应 NaN） */
    private static OptionalInt jsParseInt(String s) {
        if (s == null) {
            return OptionalInt.empty();
        }
        int i = 0;
        int len = s.length();
        while (i < len && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= len) {
            return OptionalInt.empty();
        }
        boolean negative = false;
        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            negative = c == '-';
            i++;
        }
        long value = 0;
        boolean any = false;
        while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            value = value * 10 + (s.charAt(i) - '0');
            if (value > Integer.MAX_VALUE) {
                return OptionalInt.of(negative ? Integer.MIN_VALUE : Integer.MAX_VALUE);
            }
            any = true;
            i++;
        }
        if (!any) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) (negative ? -value : value));
    }

    /** 现有自定义名（无自定义名返回 null——原脚本 slice 会把默认名切成乱码，故不再覆写） */
    private static String displayTextOrNull(ItemStack stack) {
        return stack.hasCustomHoverName() ? stack.getHoverName().getString() : null;
    }

    private static String tagStringOrNull(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
    }

    private static int tagIntOrZero(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : 0;
    }

    private static boolean sameAllNodes(ItemStack a, ItemStack b) {
        String va = tagStringOrNull(a, TAG_ALL_NODES);
        String vb = tagStringOrNull(b, TAG_ALL_NODES);
        return va == null ? vb == null : va.equals(vb);
    }

    /** “准备演奏”状态的重建物品（保留原自定义名，写入 allNodes/songName/length 与绿色提示 lore） */
    private static ItemStack buildPreparedInstrument(ItemStack template, String itemName, String songName,
                                                     String allNodes, int length) {
        ItemStack stack = new ItemStack(template.getItem(), 1);
        if (itemName != null) {
            setCustomName(stack, itemName);
        }
        addLore(stack, Component.literal("准备演奏的曲目：" + songName)
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false)));
        CompoundTag tag = stack.getOrCreateTag();
        if (allNodes != null) {
            tag.putString(TAG_ALL_NODES, allNodes);
        }
        if (songName != null) {
            tag.putString(TAG_SONG_NAME, songName);
        }
        if (length > 0) {
            tag.putInt(TAG_LENGTH, length);
        }
        return stack;
    }

    /** 物品自定义名（display.Name JSON，颜色白 + 斜体关闭，与脚本 withName 一致） */
    private static void setCustomName(ItemStack stack, String text) {
        setCustomName(stack, Component.literal(text)
                .withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)));
    }

    private static void setCustomName(ItemStack stack, Component component) {
        stack.getOrCreateTagElement("display").putString("Name", Component.Serializer.toJson(component));
    }

    /** 追加一行 lore（display.Lore JSON） */
    private static void addLore(ItemStack stack, Component component) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        lore.add(StringTag.valueOf(Component.Serializer.toJson(component)));
        display.put("Lore", lore);
    }

    /** 空格分割名单（容忍 null → 空名单） */
    private static List<String> splitNames(String list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        for (String name : list.split(" ")) {
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /** 附近 40 格内的玩家（level.getEntitiesOfClass(Player, boundingBox.inflate(40))） */
    private static List<ServerPlayer> nearbyPlayers(ServerPlayer player) {
        List<ServerPlayer> result = new ArrayList<>();
        for (Player p : serverLevelOf(player).getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(CHOIR_SEARCH_INFLATE))) {
            if (p instanceof ServerPlayer sp) {
                result.add(sp);
            }
        }
        return result;
    }

    /** 成书标题：可能是组件 JSON（{"text":...}）也可能是纯文本，统一取纯文本 */
    private static String bookTitleText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.charAt(0) == '{') {
            try {
                Component component = Component.Serializer.fromJson(raw);
                if (component != null) {
                    return component.getString();
                }
            } catch (Exception ignored) {
                // 回退到原文本
            }
        }
        return raw;
    }

    /** 文件名净化：去掉路径分隔符/非法字符（原脚本直接用书名当文件名，可被 “/” 等字符注入路径） */
    private static String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("[\\x00-\\x1f]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return "song";
        }
        return cleaned;
    }
}
