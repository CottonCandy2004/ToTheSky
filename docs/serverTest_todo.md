# 服务器测试清单（ToTheSky 1.20.1 全量注册回归）

> 生成日期：2026-09-09。逐项核对 `src/main/java/com/fst/tothesky/` 当前源码（ModItems / ModBlocks / ModFluids / ModEffects / 全部事件与方块实体），覆盖模组新增/注册的**全部物品、方块、状态效果、流体与逻辑**。
> **入口指令**：`gradlew runClient`（或把 `build/libs/tothesky-1.0.0.jar` 放进 RiaFST 4 实例 `mods/` → 启动）。Forge 1.20.1 / Java 17 / Create 6.0.8。
> 游戏内测试由用户手动进行——按下列分组逐项打勾。**本清单为验收参考，阈值/文案以源码注释为准。**

---

## 0. 存档迁移与 kubejs 兼容（MissingMappings）

- [ ] **启动日志无 FAIL 级 MissingMappings**：搜日志 `映射` / `Missing mapping`，kubejs:* 条目应显示 `映射 kubejs:xxx -> tothesky:xxx`（INFO 级）。覆盖方块/物品/流体/流体桶/状态效果/音效/方块实体（`MissingMappingEvents`）
- [ ] **已放置方块保留**：旧存档里的售货机/扭蛋机/披萨/拉面/酒坊机器/鸡尾酒杯，外观与位置完好（同名 remap 生效）
- [ ] **披萨阶段方块**：存档里 `pizza_margarita2/3/4`、`pork_pizza2/3/4`、`apple_pizza2/3/4`（9 个阶段方块）显示为对应缺角模型；右键给切片并进入下一阶段（末阶段变空气）
- [ ] **售货机/扭蛋机内容物**：打开旧存档的售货机，商品栏位、owner、价格（price1.price2）与已售数据还在
- [ ] **物品栏/箱子里的 kubejs 物品**：全部变成 tothesky 同名物品（豆腐系列/鳕鱼堡/切片披萨/乐器/金币/三角片等）
- [ ] **流体桶**：旧存档的下界溶液/不稳定下界溶液/豆浆/酱油/大豆油/恶魂之泪桶是否保留
- [ ] **药水效果残留**：受击鼓传花/死亡回溯/绿玩/谵妄影响的玩家重新登录，效果图标不报错
- [ ] **未迁移条目 FAIL 提示**：存档含未迁移注册（`seat`/`player_seat` 实体、`event_item_1~5` FPS 物品、`rocket` 实体、部分酿酒/服务器运营条目）时 Forge 打 FAIL——确认属预期（见 `NOT_MIGRATED.md`）
- [ ] **新世界无回归**：新建世界，确认以上所有注册物仍能正常创造模式获取与使用

## 1. 注册内容回归（新世界即可测）

- [ ] 创造标签页 `tothesky:main`：图标为晴天鳕鱼；所有 `ModItems` 条目 + 6 个流体桶均可获取（**不含** KC 镰刀 / `firing_multiple_fireworks` 无物品方块）
- [ ] 全部物品/方块图标与本地化名正确（中文 `zh_cn.json`，无 `translation key` 字面量）
- [ ] 六个流体桶：桶装取放、薄层渲染正确（见 §9 色值）
- [ ] 物品栏 tooltip 行数正确（各 TooltipItem / TooltipBlockItem 的灰色说明）
- [ ] 索拉里斯勋章 `solaris0/1/2`、四色魔石 `blue/red/yellow/green_magic_stone`、`roller_ticket` 可获取

## 2. 食物物品行为（食用触发）

- [ ] **巧克力意面** `pasta_with_chocolate`：+12 饥/+1.0 饱；2% 厄运 30 秒(600t)；75% 恶心 10 秒(200t, II 级)
- [ ] **焦糖鳕鱼羹** `caramel_cod_soup`：碗装食物吃完返碗；吃后自伤 2 点；若残血 ≤2 直接致命并全服广播「摄入焦糖鳕鱼羹过多而死」；否则 actionbar「是错觉吗？似乎胃里有什么蹦跳了一下」
- [ ] **健胃消食片** `digestion_pellow`：0 饥饿可随时吃；饥饿 300t amp79
- [ ] **鱿鱼狂欢节** `squid_festival`：+12 饥/+1.0 饱；恶心 200t amp5；吃完返碗；非潜行吃/潜行放切换
- [ ] **深海鳕鱼堡** `cod_burger`：+12 饥/+1.0 饱
- [ ] **油炸鳕鱼** `fried_cod`：+8 饥/+1.0 饱
- [ ] **切制奶酪** `cut_cheese`：+4 饥/+1.0 饱
- [ ] **切片玛格丽特披萨** `sliced_pizza_margarita`：+4 饥/+1.0 饱；农夫乐事「滋养」1200t（FD 缺失时无效果）
- [ ] **切片猪肉碎披萨** `sliced_pork_pizza`：+5 饥/+1.2 饱；滋养 1800t
- [ ] **切片苹果披萨** `sliced_apple_pizza`：+5 饥/+1.0 饱；滋养 1300t；2% 厄运 600t
- [ ] **幻翼虾仁** `phantom_shrimp`：+7 饥/+1.5 饱；发光 60 秒(1200t)；每隔 1 秒头顶放一发礼花共 4 发（红/橙/黄/青绿）
- [ ] **三角粥** `delta_porridge`：饮用动画；+9 饥/+0.8 饱；返碗；物品冷却 60 秒；给绿玩+移速 II+力量+抗性各 1200t
- [ ] **饮品659** `drink659`：饮用/可放置；+2 饥/+1.5 饱；记录当前坐标为死亡回溯点；给予死亡回溯 6000t（可叠加时长）
- [ ] **晴天鳕鱼** `sunshine_cod`：+5 饥/+1.5 饱；在有天空维度的世界食用后雨过天晴（12000~180000t），全服广播「…食用了晴天鳕鱼，善哉，天公作美！」
- [ ] **温泉蛋牛肉盖饭** `beef_over_rice`：+10 饥/+0.6 饱；无特殊效果
- [ ] **劲爆鳕鱼堡** `bomb_cod_burger`：+14 饥/+14 饱；吃下后在头顶一格生成强度 4.0 真实爆炸（不破坏方块，但有声音/粒子/击退；**吃堡玩家不受伤**）
- [ ] **秘封洋葱绿叶肥虫汤** `bug_soup`：+4 饥/+0.2 饱；挖掘疲劳 600t、移速 1200t；谵妄 2400t（可叠加）
- [ ] **麻婆豆腐** `spicy_bean_curd`：+8 饥/+1.0 饱；滋养 600t；返碗
- [ ] **浆果麻婆豆腐** `berry_bean_curd`：+10 饥/+1.0 饱；滋养 600t；返碗；**20% 概率招来 5 道雷电劈中玩家**
- [ ] **甜豆花** `sweet_bean_curd`：+8 饥/+1.0 饱；返碗；可放置
- [ ] **咸豆腐脑** `salty_bean_curd`：+8 饥/+1.0 饱；返碗；可放置
- [ ] **豆腐** `bean_curd`：0 功能（食材/合成中间品）
- [ ] **小块豆腐** `cut_bean_curd`：+3 饥/+0.0 饱

## 3. 食材/产线中间品（无交互，仅合成参与）

- [ ] 下界合金产线：`impure_alloy_base` / `raw_alloy_base` / `incomplete_netherite_ingot` / `witch_factor` / `activated_witch_factor`（可放置/可合成，数值与 kjs 一致）
- [ ] 钻石产线：`diamond_core` / `uncomplete_diamond`
- [ ] 图腾/石墨产线：`emerald_nugget` / `raw_totem` / `incomplete_totem` / `fiber_mixture` / `frother_mixture` / `incomplete_tortilla` / `he_graphite` / `small_crystal` / `faded_small_crystal`
- [ ] 经济系统：`delta_coin`（10Δ）/ `delta_coin_chip`（1Δ）/ `delta_dust` / `delta_porridge` 关联
- [ ] 披萨食材：`cheese` / `pizza_base` / `raw_pizza_margarita` / `raw_pork_pizza` / `raw_apple_pizza` / `raw_sunshine_cod`
- [ ] 其他：`soy_sause_bottle` / `soy_bean_oil`（酱油瓶/大豆油瓶，作烹饪油/食材）
- [ ] 酿酒占位：`wine_bottle`（0 饥饿可吃，无逻辑）/ `incomplete_wine_bottle`（无逻辑占位）

## 4. 工具/医疗/功能道具

- [ ] **采血套装** `hemostix`：无功能占位（普通物品，堆叠 16）
- [ ] **采血套装plus** `hemostix_plus`：主手右键；血量 >5 → 给 1 血瓶、自伤 5、actionbar「§c你感到血正在流出...」、冷却 30t、耐久 -1（耐久 13）；血量 ≤5 仅提示「§c你不能再抽血了」
- [ ] **收割黑夜** `harvest_the_night`：主手右键；end_rod 粒子 400 个 + trident.return 音效；肃清 150 格内 Size:0 的普通幻翼（传送到玩家上方 4 格后击杀）；存在非 0 Size 幻翼时提示「无法下手」；冷却 300t；耐久 200；恒有附魔光效
- [ ] **竹蜻蜓** `copter`：主手右键；漂浮 III 3 秒（60t, amp4）+ 云粒子 200 个；冷却 300t；耐久 20；望远镜使用动画
- [ ] **机械手润滑剂** `deployer_lubricant`：主手右键 `create:deployer`（无耐久有逻辑）；写公共 Owner NBT、播放 `create:slime_added`、冷却 10t、耐久 -1（耐久 100）
- [ ] **血瓶** `blood_bottle`：堆叠 1、合成余物为玻璃瓶；下界溶液原料
- [ ] **包装颜料** `packed_colors`：NBT StoredColors 记录染料列表；潜行右键拆包返还全部染料（播放拾取音）；tooltip 列出内含颜料
- [ ] **遗忘之露** `dew_of_oblivion`：对驯服生物右键；是主人 → 解绑（setOwnerUUID null、取消驯服、16 点附魔粒子 + 附魔桌音效 + actionbar「它忘记了一切……」、消耗 1）；非主人红字「它的主人好像不是你呢……」

## 5. 乐器系统

- [ ] **吉他 / 电子琴 / 电子鼓机** `guitar`/`piano`/`drum_808`：主手右键/左键切换 5 种模式（独奏/合奏/合唱），演奏时长与音效正常（吉他音 `tothesky:guitar_sound` 有声音，非静音）
- [ ] **乐器拨弦音效**：`guitar_sound` 生效；鼓机 3-7 音回退到吉他音
- [ ] **空白乐谱** `empty_music_sheet`：主手空白乐谱 + 副手成书右键 → 生成乐谱物品（NBT allNodes）
- [ ] **乐谱** `music_sheet`：主手右键 → 学习歌曲写入 `config/musicSheets/<玩家>/`，actionbar 确认；物品恒有附魔光效

## 6. 方块行为

### 6.1 食品方块
- [ ] **拉面** `ramen`：放置时朝向玩家（新加 FACING）；右键吃一口（+4 饥 +4 饱 + 打嗝声），前四口吃食、第五口返还 1 碗并移除方块；破坏不掉落
- [ ] **整张披萨** `pizza_margarita`/`pork_pizza`/`apple_pizza`：非潜行右键给 1 片切片，SLICES 0→3；取完第 4 片消失；只有未动过的整张掉自身；潜行右键 PASS
- [ ] **披萨阶段方块** `pizza_*_2/3/4`（9 个）：非潜行右键给切片并进入下一阶段，末阶段变空气（存档兼容，无物品不掉落）
- [ ] **可放置食物方块** `salty_bean_curd` / `sweet_bean_curd` / `phantom_shrimp` / `drink659` / `sunshine_cod` / `beef_over_rice` / `squid_festival`：非潜行右键吃（32t EAT）、潜行右键放方块；方块需要下方支撑，无支撑自动弹出（`canSurvive`）

### 6.2 鸡尾酒杯方块（空杯 + 12 种酒）
- [ ] **三空杯** `martini_glass` / `hurricane_glass` / `old_fashioned_glass`：纯装饰，无物品形态，破坏不掉落；置于对应酒杯下方配对
- [ ] **12 种鸡尾酒放置方块**（马天尼杯：七月二十一/傲娇女主/甜莓马天尼/桦树伏特加；飓风杯：红蜥蜴/二次猜想/萤火/流星；古典杯：暮色/杰克故事/上海海滩/节肢克星）
- [ ] 放置：潜行 + 手持对应鸡尾酒右键坚实方块**顶面** → 放置（朝向玩家），消耗 1 瓶
- [ ] 叠杯：手持同种鸡尾酒右键已放酒杯 → 叠一份至杯型上限（马天尼 2 / 飓风 3 / 古典 4）
- [ ] 取回：潜行空手右键 → 取回 1 瓶；取完最后一份整杯消失（不留空杯）
- [ ] 喝掉：空手右键最后一份（stacks=1）→ 直接喝掉（触发 kk 鸡尾酒 buff/效果）+ 打嗝声，留下对应空杯
- [ ] 破坏掉落：非创造手动破坏 → 按当前份数掉落鸡尾酒

### 6.3 售货机/扭蛋机方块结构
- [ ] 放置朝向：机器放置时朝向玩家（FACING）
- [ ] **售货机** `seller`：见 §10.1
- [ ] **扭蛋机** `roller`：见 §10.2

### 6.4 酿酒方块（仅形态，逻辑不迁移）
- [ ] **蒸馏器/发酵罐/陈酿罐/标签机** `distiller`/`ferment_container`/`aging_container`/`lable_printer`：水平朝向装饰，无交互逻辑
- [ ] **酿酒工作台** `wine_crafting_table`：带 AGE(0-2) 属性水平朝向；无交互逻辑
- [ ] **你不该拿到的酒** `wine_bottle`：0 饥饿可吃（占位）

### 6.5 装饰方块
- [ ] **烛台** `candle_stick`：发光等级 14，常规放置
- [ ] **汉堡彩蛋** `burger`：非潜行右键 actionbar「§a你的嘴巴似乎被什么粘住了」+ 全服广播「• §8<§7名字§8>: §f唔唔唔，呜呜！」；潜行右键不触发
- [ ] **纳奈子雕像** `nanako_sculpture`：发光 8，水平朝向装饰
- [ ] **四种烹饪锅** `golden_cooking_pot` / `silver_cooking_pot` / `copper_cooking_pot` / `golden_skillet`：无交互
- [ ] **活动方块** `event_block_1/2/3`：无交互占位
- [ ] **高热石墨块** `he_graphite_block`：需正确工具才掉落（`requiresCorrectToolForDrops`）

### 6.6 春节方块
- [ ] **礼炮** `multiple_fireworks`：主手打火石右键 → 播 tnt.primed；5t 后清除 3×3×3 火焰并把本格换成 `firing_multiple_fireworks`；此后 20/30/…/100t 共发 9 发彩色烟花；最后一发同时换 `fireworks_box`
- [ ] **燃放的礼炮** `firing_multiple_fireworks`：临时态，无物品不掉落
- [ ] **礼炮纸壳** `fireworks_box`：燃尽残骸，不掉落
- [ ] **鞭炮** `multiple_firecrackers`：主手打火石右键 → 玩家原点音效 tnt.primed；40t 后召唤小盔甲架标记；3/6/…/24t 共 8 次脉冲（机枪声 createbigcannons:fire_machine_gun + 岩浆/营火/火焰粒子）；24t 后移除标记、变空气并**连锁引爆周围 8 格未点燃鞭炮**

### 6.7 动力雕刻台
- [ ] `mechanical_chisel_table` 方块形态与配方合成（青砖+安山外壳+合金+齿轮）见 §11

## 7. 鸡尾酒系统（kk 协作）

- [ ] **kitchenkarrot 鸡尾酒识别**：`CocktailHelper` 读 NBT `cocktail` id，不引用 kk 类；kk 缺失时相关功能禁用不崩溃
- [ ] **酒保/调酒**：kk 鸡尾酒物品可正常饮用，buff 由配方 JSON `content.effect` 提供（1.20.1 数据驱动）
- [ ] **`fstwines:call_of_tahiti`**：喝完全服广播「• 名字>: 我一定会上工的，嗝～」
- [ ] **`fstwines:free_nightingale`**：随机赠送 1 件礼物（4 选 1：mynethersdelight 子弹椒 / culturaldelights 腌菜 / create_confectionery 焦糖棉花糖 / crabbersdelight 珍珠，各 4 个）
- [ ] **`fstwines:dangerous_party`**：全服广播「…饮下了危险派对！」；饮用者获得击鼓传花 1200t + 发光 1200t
- [ ] **`fstwines:shoal_in_dream`**：脚下方为空气 → 在四方向找空位放一张蓝色床（脚/头两段）；否则给 1 张蓝色床
- [ ] 其他鸡尾酒（david / silent_midnight / lago_de_texcoco）：无自定义效果，仅配方 buff

## 8. 状态效果

- [ ] **绿玩** `fair_play`：效果存续期间每 200t 让 1~20 格内其他实体发光 7 秒（140t持）
- [ ] **死亡回溯** `rewind`：受到致命伤（剩余血量≤1）时取消伤害、血量置 1、传送到记录点（rewind_pos，登录时刷新），播放图腾动画(35)与音效
- [ ] **谵妄** `madness`：效果期间玩家无法发言（`ServerChatEvent` 取消）；平均约 250t 一次全服「说胡话」（随机语录）
- [ ] **击鼓传花** `hot_potato`：duration 编码本场剩余时长；每 10s 广播剩余秒数；剩余 1t 时击杀持有者并广播「没能及时把好运传给下一个人」；持有者攻击别人时把效果+发光+致盲+迟缓传给受害者（见 §13.1）

## 9. 流体（6 种，全部 `noBlock`，仅供 Create 配方/桶搬运）

- [ ] **下界溶液** `netherite_liquar`（桶 `netherite_liquar_bucket`）
- [ ] **不稳定下界溶液** `unstable_netherite_liquar`（桶 `unstable_netherite_liquar_bucket`）
- [ ] **豆浆** `bean_sause`（桶 `bean_sause_bucket`，乳白薄层）
- [ ] **大豆油** `bean_oil`（桶 `bean_oil_bucket`，淡黄薄层）
- [ ] **酱油** `soy_sause`（桶 `soy_sause_bucket`，深棕薄层）
- [ ] **恶魂之泪** `ghast_tear`（桶 `ghast_tear_bucket`，淡青薄层）
- [ ] 共性：桶空放返还桶；流体不自然流动（noBlock）；纹理由 `textures/block/<name>_still|flow.png` 绑定；mcmeta 流动动画正常

## 10. 售货机/扭蛋机（VendingEvents + BE）

### 10.1 售货机 `seller`
- [ ] **放置记录 owner**：放置时 BE 记录 owner UUID/名字
- [ ] **店主加价**：右键 +1（price1）、潜行右键 +0.1（price2，满 10 进位）；左键减价（潜行减小数）；actionbar「当前价格：x.xΔ」
- [ ] **顾客看信息**：右键（非店主）显示「此售货机属于 <owner>，每个 <商品> x.xΔ」；无上方容器提示「售货机未正确设置！」；空提示「售货机已空！」
- [ ] **顾客购买**：左键购买最后一个非空槽商品；按三角币(10Δ)/三角片(1Δ)计算余额、扣款、给商品、找零；余额不足提示；扣款/提取失败会退款
- [ ] **经济打款**：购买后执行 `money give <owner> <price>`（经济系统命令占位，日志记录）
- [ ] **容器保护**：非 owner 不能破坏机器；破坏时上方容器有物品 → 阻止（提示先清空）；非 owner 不能打开紧贴机器上方的容器

### 10.2 扭蛋机 `roller`
- [ ] **店主持抽奖券绑定**：owner 手持抽奖券右键 → 写入本机 key 到券 NBT + 附魔光效（提示「抽奖券已绑定！」）；已绑定的券不可覆盖
- [ ] **抽奖（无下方容器）**：需手持匹配 key 的抽奖券；随机取上方容器 1 件奖品；消耗 1 张券、给玩家；券不匹配/空提示相应文案
- [ ] **抽奖（有下方容器）**：直接输出到下方容器（机械接入模式，无需券）；插入剩余丢地上（不吞）
- [ ] **容器保护**：同上（owner、上方容器非空阻止破坏、非 owner 禁开上方容器）
- [ ] 空/未配置提示：「扭蛋机已空！」/「扭蛋机未正确配置！」/「你需要手持抽奖券！」/「抽奖券与扭蛋机不匹配」

## 11. 动力雕刻台（PR#59）

- [ ] **配方**：青砖+安山外壳+合金+齿轮合成 `mechanical_chisel_table`
- [ ] **FE 供能**：接收 FE（createaddition 电动马达带动），上限 10000，每 tick 输入 1000；每执行一次凿刻消耗 100 FE；最多 10 次/tick
- [ ] **7 栏位**：0 材料 / 1 模板 / 2-5 染料 / 6 输出；ultramarine 凿刻配方匹配
- [ ] **过滤器**：可设 filterId；ultramarine 模板识别（软依赖，缺失时仅 WARN 不可设模板）
- [ ] **交互**：空手潜行右键清空所有槽位；空手右键清空过滤器；持模板设置模板；`ultramarine:chisel_dye` tag 含 16 原版染料+17 ultramarine 染料粉
- [ ] **扳手拆除**：潜行 + `forge:tools/wrench` 或 `create:wrench` 右键 → 播放 `create:wrench_remove`、返还方块物品、移除方块（不破坏地形）；非创造才返还
- [ ] 中文 GUI/提示正常

## 12. 检查点系统

- [ ] **放置/破坏维护索引**：`checker` 放置/破坏时读写 `config/CheckerData/posList.txt`（服务端）
- [ ] **玩家经过判定**：每 tick 对每个索引点泛洪填充相连 `supplementaries:checker_block`（XZ 3×3）；玩家站在 xz∈[-0.5,+1.5]、y∈[y,y+4) 且 5s 冷却已过、非持扳手 → 记录时间戳并播放粒子/音效/烟花
- [ ] **扳手交互**：主手 `create:wrench` 右键检查点 → 非潜行打印通过记录；潜行清空记录

## 13. 其他交互逻辑

- [ ] **击鼓传花传递**（`LivingHurtEvent`）：持有 hot_potato 的玩家攻击别人 → 把效果传给受害者（附发光/致盲 20t/迟缓254 20t），广播「恭喜！你把好运传给了…/哦不！…把好运传给了你」「…眼前一黑」
- [ ] **箱子防潜影盒**：潜行 + 主手/副手潜影壳右键 `forge:chests` 标签方块 → 事件取消
- [ ] **行商召唤**：主手 `ultramarine:copper_cash_coin` 右键钟 → 复制行商（重置交易次数）放到钟上方、消耗 1 硬币、提示「召唤了行商」、48000t 后 discard；ultramarine 为软依赖
- [ ] **魔法照片** `MagicPhotoEvents`：潜行右键 `exposure:photograph` 且 NBT `XXXXXAllowteleport="true"` → 传送粒子爆发、50t 后传送到照片 `Pos`、再播 end_rod 粒子
- [ ] **镰刀范围收获** `SickleEvents`：主手 `kaleidoscope_cookery:*_sickle` 右键 → 以射线目标为中心 5×2×5 收割成熟作物+灌木（自动补种）；SWEEP 音效/动画；每次收获耐久 -count；冷却 10t
- [ ] **钻石镰刀** `kaleidoscope_cookery:diamond_sickle`：挖掘 4、耐久 3000、速度 9、攻击 3、附魔 10
- [ ] **下界合金镰刀** `kaleidoscope_cookery:netherite_sickle`：耐久 4000、攻击 5、附魔 15、防火

## 14. 配方抽测

- [ ] `data/tothesky/recipes` 共 109 条（统计于 `src/main/resources/data/tothesky`）：抽测 10 条（压实豆腐、切割豆腐、Create 序列钻石、下界合金产线、魔女因子、奶酪、饮品659、晴天鳕鱼、牛肉盖饭、动力雕刻台）；**日历**：无序配方金粒+黄绿色染料+纸（`calendar_from_shapeless.json`）
- [ ] `fstwines` 配方 7 条（酒类数据驱动）
- [ ] 镰刀合成（`kaleidoscope_cookery` 命名空间：钻石镰刀 shaped + 下界合金 smithing）
- [ ] 遗忘之露（金锭+恶魂之泪十字）
- [ ] 包装颜料（`ultramarine:xuan_paper` + 1~4 `ultramarine:chisel_dye` → `packed_colors` 带 StoredColors NBT，`PackedColorsRecipe`）
- [ ] 邮筒配方（红/绿邮筒，contact 模组；`forge:cheese`/`ultramarine:chisel_dye` tag 已迁移）

## 15. 资源渲染 & 本地化

- [ ] 所有方块模型/贴图加载无紫黑棋盘（F3 检查 missing texture 日志）
- [ ] 拉面 `bites=0~4 × facing` 变体渲染正确（新加朝向）
- [ ] 阶段披萨 9 方块模型正确
- [ ] 流体动画（mcmeta 流动贴图）
- [ ] 可放置食物方块多段碰撞箱正确（豆腐脑碗形/幻翼虾仁扁盘/饮品659杯形/晴天鳕鱼扁盘/牛肉盖饭三层/鱿鱼狂欢节矮盘）
- [ ] lang（`zh_cn.json`/`en_us.json`）无 `translation key` 字面量残留

## 16. 与其他模组的兼容

- [ ] **kitchenkarrot**：鸡尾酒系统（酒保/调酒/饮后效果）正常
- [ ] **farmersdelight**：滋养效果/切割/烹饪配方（豆腐切割、麻婆豆腐锅）——FD 缺失时滋养效果被跳过，不崩溃
- [ ] **Create 6.0.8**：部署器润滑、混合器、序列装配全部 kjs 配方等价物；扳手/检查点子方块协作
- [ ] **createaddition**：FE 供能雕刻台
- [ ] **ultramarine**：凿刻模板识别（反射软依赖，缺失时仅 WARN）；行商硬币；齐宣纸/凿刻染料
- [ ] **exposure**：照片传送
- [ ] **kaleidoscope_cookery**：镰刀（仅在 mod 加载时注册；userdev 不加载时跳过）
- [ ] **create_confectionery**：缺失时（EffectStack.get）仅 WARN 不崩
- [ ] **软依赖总检**：依次去掉 kk/fd/create/ultramarine/exposure/kaleidoscope_cookery 各 mod，确认相关功能禁用但不崩溃

## 17. 日历系统（CalendarBlock + GUI + REST API）

- [ ] **方块注册**：`calendar`（日历）可创造获取、可放置；破坏掉落自身；模型暂为紫黑棋盘（贴图未画，预期）
- [ ] **打开 GUI**：右键日历方块弹出月视图；新底图（256×256，自带标题/星期标签/格子边框），日期格区 6 行×7 列、第一格左上 (42,88)、步进 25px、格主体 22×22、内容区 18×18；「今天」白色高亮恰好覆盖内容区不压边框；日期数字在内容区左上角（格左上+3）且**始终压在图标上层**（数字在全部图标之后以 z=200 绘制）
- [ ] **翻月与标题**：左右为贴图箭头按钮（`calendar_prev.png` / `calendar_next.png`，32×32 原尺寸，距左右边缘 6px、距顶 36px，**不绘制任何文本**，仅无障碍朗读；悬停略暗）；月份标题「xxxx·x」**水平居中**，文字下缘在日期格第一行上方 13px（y=67）；跨年正确（12 月→1 月年份 +1）
- [ ] **图标渲染**：图标贴内容区右下再整体左上移 1px；`iconType=item` 物品图标 16px、`iconType=block` 方块物品形态、`iconType=player` 玩家头 14px（比物品小一圈）；头像皮肤经 `SkullBlockEntity.updateGameprofile` + `SkinManager.registerSkins` 异步解析（AW 模特同款流程），未就绪时默认皮肤占位、约 0.5s 内刷新为真皮肤；离线玩家解析后也显示正确皮肤
- [ ] **多活动轮播**：同一日期 ≥2 个事件时图标每 2 秒（40t）轮换
- [ ] **Tooltip**：悬停有事件的格子显示全部活动；**生日显示「xxx的生日」**（🎉 节日用原名 / 🎂 生日加后缀），描述在名称下方
- [ ] **管理页**（`http://127.0.0.1:39000/`，mod 自带）：表格名称列生日显示「xxx的生日」、页头今日活动同规则；编辑表单回填原始 name；增删改与游戏 GUI 即时同步
- [ ] **REST API**（服务器侧已自动化验证，GUI 联动需手测）：`GET/POST/PUT/DELETE http://127.0.0.1:39000/api/calendar/events`、`GET /today`；**网页管理端打开 GUI 时增删事件，GUI 无重开即时刷新**
- [ ] **崩溃恢复**：服务器强杀后重启，启动日志出现「[日历] 从镜像合并 N 条 SavedData 缺失的事件」（镜像 `<世界>/calendar_events.json`，与 SavedData 按 id 并集合并，两侧新增都不丢）
- [ ] **配置**：`config/tothesky_calendar.json` 改 port/bindAddress 后重启生效；端口占用时仅 WARN 不崩服（**同机开客户端+专用服务器时只有一个进程能绑 39000**——后启动的 HTTP 禁用，属预期）

| | | 17. 日历系统 | | |

---

### 测试结果记录

| 日期 | 测试人 | 分组 | 结果 | 备注 |
|------|--------|------|------|------|
| | | 0. 存档迁移 | | |
| | | 1. 注册回归 | | |
| | | 2. 食物物品行为 | | |
| | | 3. 食材/产线 | | |
| | | 4. 工具/道具 | | |
| | | 5. 乐器系统 | | |
| | | 6. 方块行为 | | |
| | | 7. 鸡尾酒系统 | | |
| | | 8. 状态效果 | | |
| | | 9. 流体 | | |
| | | 10. 售货机/扭蛋机 | | |
| | | 11. 动力雕刻台 | | |
| | | 12. 检查点 | | |
| | | 13. 其他逻辑 | | |
| | | 14. 配方抽测 | | |
| | | 15. 资源渲染 | | |
| | | 16. 模组兼容 | | |
