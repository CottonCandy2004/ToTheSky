package com.fst.tothesky.dumpling;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * 饺子命名器：按馅料确定性地生成前缀、评语、颜色与烹饪时长。
 * 移植自 {@code dumpling_making.js} 的 {@code dumpling$processNBT}。
 *
 * <p>随机种子与抽取顺序都按脚本原样复刻，因此**同一馅料在脚本与模组里得到同一个名字**：
 * 脚本的种子串是 {@code 物品 id}（NBT 为空）或 {@code id + " " + tag.getAsString()}，
 * 抽取顺序为 颜色 → 前缀 → 时长 → 评语。改动其中任何一处都会让盘子里拿出的饺子
 * 与锅里煮出的饺子重名不上。
 */
public final class DumplingNamer {
    private DumplingNamer() {
    }

    /**
     * @param prefix       名称前缀（如 "热腾腾的"）
     * @param trait        评语（金色 lore）
     * @param color        名称颜色 RGB
     * @param processTicks 烹饪时长（tick）
     */
    public record Profile(String prefix, String trait, int color, int processTicks) {
    }

    /** 为单个馅料生成档案；馅料为空返回 null */
    @Nullable
    public static Profile profileFor(ItemStack filling) {
        if (filling.isEmpty()) {
            return null;
        }
        boolean isFood = filling.getItem().isEdible();
        boolean negative = isFood && hasNegativeEffect(filling.getItem().getFoodProperties(filling, null));

        Random random = new Random(seedOf(filling));

        String[] prefixPool;
        String[] traitPool;
        if (isFood) {
            prefixPool = negative ? BAD_PREFIXES : GOOD_PREFIXES;
            traitPool = negative ? BAD_TRAITS : GOOD_TRAITS;
        } else {
            prefixPool = INEDIBLE_PREFIXES;
            traitPool = INEDIBLE_TRAITS;
        }

        int color = random.nextInt(0xFFFFFF);
        String prefix = prefixPool[random.nextInt(prefixPool.length)];
        int processTicks = 50 + random.nextInt(201);
        String trait = traitPool[random.nextInt(traitPool.length)];
        return new Profile(prefix, trait, color, processTicks);
    }

    /** 脚本的种子串：无 NBT 用 id，有 NBT 用 {@code id + " " + SNBT} */
    private static int seedOf(ItemStack filling) {
        String key = String.valueOf(BuiltInRegistries.ITEM.getKey(filling.getItem()));
        CompoundTag tag = filling.getTag();
        return (tag == null || tag.isEmpty() ? key : key + " " + tag.getAsString()).hashCode();
    }

    /** 食物是否带负面效果（无法检测药水、谜之炖菜等） */
    private static boolean hasNegativeEffect(@Nullable FoodProperties food) {
        if (food == null) {
            return false;
        }
        for (var pair : food.getEffects()) {
            MobEffectInstance instance = pair.getFirst();
            if (!instance.getEffect().isBeneficial()) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 词库（原样移植） ----------------

    private static final String[] BAD_PREFIXES = {
            "糊了的", "齁咸的", "夹生的", "馊掉的", "凉透的", "卖相诡异的", "味道古怪的", "来历不明的",
            "偷工减料的", "包得歪歪扭扭的", "馅料可疑的", "皮厚如墙的", "咸到发苦的", "油腻腻的",
            "软烂如泥的", "硬如石头的", "毫无味道的", "腥气冲天的", "酸溜溜的", "甜到发腻的", "苦不堪言的",
            "辣到流泪的", "麻到失去知觉的", "像是隔夜的", "像是从垃圾桶捡回来的", "被诅咒的",
            "令人作呕的", "黑暗料理界的", "食之无味的", "弃之可惜的", "挑战人类极限的", "吃一口需要勇气的",
            "吃完会做噩梦的", "仿佛在嚼蜡烛的", "一股子怪味的", "不知道什么肉做的", "看起来就不太对劲的",
            "颜色诡异的", "散发奇怪气味的", "粘糊糊的", "干巴巴的", "冷冰冰的", "半生不熟的", "熟过头的",
            "烤焦的", "炸过头的", "煮烂的", "没放盐的", "放了两遍盐的", "调料放错的", "像是用脚包的",
            "像是被蹂躏过的", "充满怨念的", "来自地狱的", "被黑暗力量侵蚀的", "诸神唾弃的",
            "禁忌之术搞砸的", "跨越次元的失败品", "史诗级的灾难", "梦幻般的难吃", "传说中难吃到极致的",
            "不可名状的黑暗", "自带霉运光环的", "蕴含毒气的", "被封印的恶之料理", "皇家御用的毒药"
    };

    private static final String[] BAD_TRAITS = {
            "咬开后发现是空的", "一口下去怀疑人生", "仿佛在吃橡胶", "味道像极了洗脚水", "吃完立刻想吐",
            "这饺子可能想谋杀我", "里面的馅料正在互相打架", "皮厚得能防弹", "像是从战场上下来的",
            "吃完感觉生命值-100", "建议搭配解毒剂食用", "吃完需要去医院洗胃", "这饺子有毒",
            "吃了一口就哭了", "难吃到令人发指", "难吃到怀疑自己为什么要吃", "吃完后悔三生三世",
            "这是对饺子的侮辱", "连狗都不吃", "看一眼就饱了", "闻到味道就跑了", "吃完后感觉世界失去了颜色",
            "吃出了绝望的味道", "仿佛在咀嚼自己的失败", "吃完后整个人都不好了", "这饺子让我想起了前任",
            "吃一口折寿十年", "这是减肥神器", "吃下去胃开始了罢工", "牙齿表示受到了伤害",
            "味蕾集体自杀", "舌头在尖叫", "喉咙在拒绝下咽", "这是来自地狱的馈赠", "吃完后想要净化自己",
            "这饺子可能受到了诅咒", "里面藏着一整个世界的恶意", "吃完后获得了负面状态",
            "这是对美食的亵渎", "建议直接扔掉", "包它的人是不是恨你", "如果你吃了它，它会哭的",
            "这是一只极其黑暗的饺子", "不要吃它，快跑", "里面的食材正在密谋逃跑",
            "哪怕是神明也会为此颤抖", "足以引发一场外交灾难", "如果你愿意，它可以是一切的终结"
    };

    private static final String[] GOOD_PREFIXES = {
            "热腾腾的", "香喷喷的", "圆滚滚的", "白净的", "晶莹剔透的", "刚出锅的",
            "皮薄的", "秘制的", "多汁的", "地道的", "鲜美的", "扎实的", "浓郁的", "爽口的",
            "油亮亮的", "饱满的", "软糯的", "弹牙的", "汤汁盈口的", "面香浓郁的", "金黄酥脆的",
            "热情的", "充满爱心的", "奶奶包的", "邻居送的", "令人怀念的", "卖相极佳的", "平平无奇的",
            "走心的", "诚意满满的", "深夜食堂的", "异乡人的", "老字号的", "大厨练手的", "手工现揉的",
            "逢年过节的", "冬至限定 的", "除夕夜的", "团圆的", "邻里赞赏的", "深巷里的",
            "发光的", "传说中的", "被祝福的", "注入灵魂 of", "甚至在动的", "深藏不露的", "有故事的",
            "梦幻般的", "史诗级的", "诸神赞叹的", "禁忌之术包出的", "跨越次元的", "流传千年的",
            "不可名状的", "自带光环的", "蕴含内功的", "被封印的", "开光的", "皇家御用的"
    };

    private static final String[] GOOD_TRAITS = {
            "鲜嫩多汁", "皮薄馅大", "香气四溢", "火候刚好", "嚼劲十足", "入口即化", "汤汁浓郁", "层次分明",
            "咸甜适中", "回味悠长", "鲜掉眉毛", "肥而不腻", "清香解腻", "口感如云朵般轻盈", "每一口都是惊喜",
            "筋道有力", "由于太好吃被禁止参加比赛", "咬开后有龙在飞", "这滋味，谁吃谁知道",
            "家乡的味道", "过年的气息", "儿时的记忆", "温暖的慰藉", "这就是幸福", "根本停不下来",
            "一种久违的踏实感", "平凡中的不凡", "每一褶都是艺术", "承载了三代的配方", "治愈一切不开心",
            "舌尖上的华尔兹", "仿佛回到了那个遥远的下午", "吃出了妈妈的味道",
            "米其林水准", "路边摊奇迹", "足以传世的杰作", "这馅料绝了", "皮的厚度堪称艺术", "内里乾坤大",
            "小心烫嘴", "建议一口一个", "似乎能听到大海的声音", "里面的馅料正在跳舞",
            "这饺子，简直是艺术品", "吃完感觉增加了 10 年内力", "如果你不吃，它会哭的",
            "这是一只极其努力的饺子", "不要盯着它看，直接吃掉", "里面的食材正在开会",
            "哪怕是神明也会为此排队", "足以解决一切外交纷争", "如果你愿意，它可以是任何口味"
    };

    private static final String[] INEDIBLE_PREFIXES = {
            "硬邦邦的", "沉重的", "硌牙的", "充满土腥味的", "无法理解的", "工业风的",
            "结构稳定的", "原始的", "粗犷的", "不可磨灭的", "高密度的", "重量级的",
            "边缘锐利的", "仿佛在拒绝被吃的", "来自大地的", "矿工严选的", "成分复杂的",
            "绝对会崩掉门牙的", "违背常理的", "密度极大的", "令人迷惑的", "这种东西也包的"
    };

    private static final String[] INEDIBLE_TRAITS = {
            "嚼起来像在咬石头", "牙医的噩梦", "极其考验咬合力", "充满了二氧化硅的气息",
            "吞下去需要巨大的勇气", "这已经不是饺子了，是暗器", "感觉在吃地壳碎片",
            "胃部表示抗议", "可以拿来砸核桃", "一口下去全是各种微量元素", "建议配合碎石机食用",
            "它似乎并不想离开你的肠道", "这可能是世界上最硬的午餐", "你真的确定要吃这个吗？",
            "吃完后体重增加了 5 公斤", "里面藏着一整块文明的基石"
    };
}
