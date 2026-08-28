# 饺子玩法（Dumpling）迁移文档

> 来源：`kubejs/server_scripts/feature/dumpling_making.js`（1.20.1，KubeJS 6，大量反射）
> 目标：迁移为 ToTheSky 模组原生实现（NeoForge 1.21.1）。
> 状态：**代码完成、`gradlew build` 通过。包制已改为砂纸式手持长按（2026-08-27），原砧板包制路径已删除。**
> 硬约束：**不允许 mixin 其他模组**（用户明确要求）。

## 玩法流程（与旧脚本一致，除包制方式）

1. **饺子皮**：`farmersdelight:cutting` 配方——`c:dough`（面团）+ 木棍 → 8 个饺子皮。
2. **包制**（砂纸式手持长按，参考机械动力 `SandPaperItem`）：任意手持饺子皮、另一手持任意物品（馅料），长按右键 16t → 消耗 1 皮 1 馅料，生饺子回收入背包（记录馅料与厨师名）。松手中止则馅料退还。另一手是饺子类物品时拒绝（"饺子皮已经足够厚了！"）。动画为 `UseAnim.EAT`，经 Create 的 `CustomUseEffectsItem` 抑制粒子与音效。
3. **合成一盘**：8 个生饺子 + 1 碗 → 1 盘生饺子（自定义合成配方 `tothesky:dumpling_plate`，聚合全部馅料/厨师；无馅饺子不能参与）。
4. **煮制**：
   - **厨锅**（农夫乐事）：右键生饺子/生饺子盘下锅，耗时 = 馅料随机时长（50~250t；盘子为总和×0.8），完成后产出在锅的输出槽。
5. **食用**：熟饺子吃下后按馅料生效——馅料可吃则委托调用馅料自己的 `finishUsingItem`（伪装成直接吃下该食物：数值/效果含概率/进食进度/容器返还/紫颂果传送等自定义逻辑全按原版）；馅料是 TNT 则脚下生成视觉 TNT，78t 后以 `ExplosionInteraction.NONE` 引爆；不可食用则原物返还。
6. **熟饺子盘方块**：右键逐个取食（每份馅料/厨师独立），第 8 口（bite=8）还碗并移除方块。破坏不掉落。内容物存于 `DumplingPlateBlockEntity`。

**命名器**（`DumplingNamer`）：以馅料身份哈希为种子确定性生成前缀/评语/颜色/时长；负面食物、普通食物、非食物各一套词库（原样移植）；随机抽取顺序与旧脚本一致（颜色→前缀→时长→评语），同一馅料永远同名。

## 代码结构

| 类 | 职责 |
|---|---|
| `registry/ModDataComponents` | `dumpling_filling`、`dumpling_author`(String)、`dumpling_plate`(DumplingPlateContents)、`dumpling_wrapping`(包制中快照) 四个组件；馅料类组件的值类型是 `ItemStackSnapshot`（NeoForge 要求组件值实现 equals/hashCode，裸 ItemStack 会抛 `IllegalArgumentException`，同 Create `SandPaperItemComponent` 的包法） |
| `item/DumplingWrapperItem` | 砂纸式手持包制（`use`/`finishUsingItem`/`releaseUsing`，实现 Create `CustomUseEffectsItem` 抑制特效） |
| `client/DumplingWrapperItemRenderer` | 包制中馅料浮在皮上随进度起伏（照搬 `SandPaperItemRenderer` 动画参数，经 `SimpleCustomRenderer` 挂载） |
| `dumpling/DumplingPlateContents` | 盘子内容物 record（8 馅料 + 8 作者），CODEC + StreamCodec；equals/hashCode 按内容比较（`ItemStack.matches` 依赖之，引用比较会导致厨锅会话即刻作废） |
| `dumpling/DumplingNamer` | 确定性命名与时长 |
| `dumpling/DumplingFactory` | 生成生饺子/熟饺子/熟饺子盘物品（组件 + 自定义名 + lore） |
| `dumpling/DumplingCookingManager` | 烹饪会话管理器，仅厨锅（见下） |
| `dumpling/CookingPotProgressBridge` | 反射桥：把厨锅会话进度写进 FD 的 `cookingPotData`（按类型扫描字段），驱动 GUI 进度箭头 |
| `item/CookedDumplingItem` | 食用效果（馅料食物/TNT/返还） |
| `block/CookedDumplingPlateBlock` + `blockentity/DumplingPlateBlockEntity` | 熟饺子盘方块（`facing` + `bite 0-8`）与内容物持久化 |
| `item/CookedDumplingPlateItem` | 放置时把物品组件写入方块实体 |
| `recipe/DumplingPlateRecipe` + `registry/ModRecipes` | 自定义合成（`SimpleCraftingRecipeSerializer`） |
| `event/DumplingEvents` | 一个 `RightClickBlock` 入口：厨锅下锅 |

## 烹饪会话与防刷设计（仅厨锅）

旧脚本用反射驱动厨锅/汤锅内部的私有烹饪流程（`processCooking`、`applyRecipe`）。厨锅改为显式会话（`DumplingCookingManager`，按 `GlobalPos` 登记，`ServerTickEvent` 驱动）：

- **输入先行**：会话开始时输入物真实进入容器（厨锅 0 号槽）。
- **完成前校验**：厨锅要求 0 号槽仍是原输入物；被玩家取走/改动 → 会话作废，**只亏不赚**。
- **先核销后产出**：先模拟插入输出槽，放得下才核销输入并产出。
- **容器被毁**：内容物走容器自身掉落逻辑，会话作废。
- 锅忙（原料槽非空）→ 拒绝下锅（旧脚本是清空弹出，会吞物品）。
- 每 tick 把 `elapsed/total` 写入 FD 厨锅的 `cookingPotData`（经 `CookingPotProgressBridge`），GUI 进度箭头随之移动。原拟同包访问 protected 字段，但 NeoForge 模块化启动器禁止 split package（`tothesky` 与 `farmersdelight` 同导出 FD 包 → `ResolutionException`），改为反射（FD 为无 module-info 的 automatic module，包全量 opens；字段按类型扫描以抗改名）。

## 资源

- 模型/贴图从 kubejs 复制并改命名空间 `kubejs:` → `tothesky:`：`models/block/cooked_dumpling_plate[_1..8].json`、`models/item/cooked_dumpling_plate.json`、`textures/block/{dumpling,plate}.png`、`textures/item/cooked_dumpling_plate.png`。
- `blockstates/cooked_dumpling_plate.json` 重新生成（bite 0-8 × facing），顺带修了原文件 `bite=0,,facing=east` 的双逗号笔误。
- 配方 JSON：`recipe/dumpling_plate.json`（自定义合成）、`recipe/dumpling_wrapper.json`（砧板切割）。
- lang：`block.tothesky.cooked_dumpling_plate` = 一盘熟饺子。

## 与旧脚本的刻意差异

- **包制从砧板右键改为手持长按**（砂纸式）：旧脚本的砧板交互在 1.21 下被 FD 自身的 `useItemOn` 抢先（实测弹"或许得换个工具……"），且 NeoForge 事件取消拦不住、根因未明；手持路径不依赖任何方块交互，彻底绕开。砧板仅保留切割配方出皮。
- 煮饺子的判定不再劫持锅的内部烹饪循环；厨锅进度仅按 tick 计数、失热暂停（旧脚本失热直接丢弃任务），经 `CookingPotProgressBridge` 同步到 FD GUI。
- 去掉了 `player.tell("[debug]"...)` 调试输出。
- `CookedDumplingPlateBlock` 覆写 `getRenderShape → MODEL`（`BaseEntityBlock` 默认 `INVISIBLE` 曾导致方块模型不渲染）。
- 森罗物语汤锅的饺子烹饪已移除（不做兼容），一盘生饺子只能走厨锅煮制。

## 参考来源

包制交互参考机械动力砂纸（Create 6.0.10-280 `SandPaperItem`）：馅料快照存入物品组件防偷换、`finishUsingItem` 核销并回收产物、`releaseUsing` 中止退还。刻意差异：时长 16t（砂纸 32t）、零粒子零声音（砂纸有打磨粒子与周期音效）、皮按个消耗而非耐久。

厨锅进度显示经 `CookingPotProgressBridge` 反射写 FD 的 `cookingPotData`（cookTime 在 data[0]、cookTimeTotal 在 data[1]）。曾尝试同包访问 protected 字段，被 NeoForge 模块化启动器的 split-package 限制否决（两个模块不能导出同一包），故用反射；FD 是 automatic module，字段可 `setAccessible`。
