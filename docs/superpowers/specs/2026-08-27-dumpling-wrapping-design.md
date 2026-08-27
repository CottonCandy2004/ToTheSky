# 手持包制饺子（砂纸式交互）设计

> 日期：2026-08-27
> 参考：Create 6.0.10-280 `SandPaperItem`（本地 gradle 缓存 sources jar 源码）
> 背景：`docs/dumplings.md` 记载的砧板包制交互实测失效（FD `useItemOn` 拦截不住）。改为手持长按包制，完全替代砧板路径。

## 决策记录（用户拍板）

- 手持包制**完全替代**砧板包制，删除失效的 `RightClickBlock` 取消路径。
- 动画：`UseAnim.EAT` + 零粒子 + 零声音（2026-08-28 修订：追加 Create 风格自定义渲染——包制中馅料浮在皮上并随进度起伏，`client/DumplingWrapperItemRenderer` 照搬 `SandPaperItemRenderer` 动画参数）。
- 使用时长 16t（砂纸 32t 的一半）。

## 后续修订（2026-08-28）

- 组件值不得为裸 ItemStack：NeoForge `CommonHooks.validateComponent` 要求组件值实现 equals/hashCode。`dumpling_filling`/`dumpling_wrapping` 值类型改为 `ItemStackSnapshot` record。
- `DumplingPlateContents` 覆写 equals/hashCode 为按内容比较（record 默认 equals 走 ItemStack 引用比较，导致厨锅会话的 `ItemStack.matches` 校验永远失败、盘子煮不了）。
- 合成配方的碗数量校验 `== 1` 改为 `>= 1`，允许成摞碗参与合成。

## 交互流程

1. 任意手持饺子皮，另一手持馅料（任意非饺子类物品），长按右键。
2. `use()` 时从另一手拆 1 个馅料存入皮的 `DUMPLING_WRAPPING` 组件（创造模式改为 copy，不消耗），开始进入使用状态。
3. 16t 内零特效：`CustomUseEffectsItem.shouldTriggerUseEffects → TriState.FALSE`，Create 的 `CustomItemUseEffectsMixin` 使原版 `shouldTriggerItemUseEffects()` 返回 false，吃食粒子/音效被抑制，仅保留 EAT 的手部动作。
4. 完成 `finishUsingItem`（仅服务端）：核销 1 张皮 + 组件内馅料 → `DumplingFactory.rawDumpling(馅料, 玩家名)` 经 `placeItemBackInInventory` 回收；移除组件。
5. 中止 `releaseUsing`：组件内馅料退还背包，移除组件（对齐砂纸，不分端）。
6. 再次右键持有脏组件的皮：`use()` 首个分支直接 `startUsingItem` 恢复包制（对齐砂纸的防御分支）。

## 关键边界

- 另一手为空 → FAIL，不进入使用。
- 另一手是饺子类（生/熟饺子、生/熟饺子盘）→ 状态栏"饺子皮已经足够厚了！"，FAIL（保留旧脚本文案）。
- 皮可叠 64：组件挂在整叠上，完成时先 `remove(WRAPPING)` 再 `shrink(1)`，残叠不带脏组件。
- 主副手对称（同砂纸 `otherHand` 逻辑）。

## 改动清单

| 文件 | 改动 |
|---|---|
| `item/DumplingWrapperItem.java` | 新增。继承 `TooltipItem`（保 tooltip），实现 `CustomUseEffectsItem` |
| `registry/ModDataComponents.java` | 新增 `DUMPLING_WRAPPING`；`DUMPLING_FILLING`/`DUMPLING_WRAPPING` 值类型为 `ItemStackSnapshot`（NeoForge 校验组件值必须实现 equals/hashCode，裸 ItemStack 会崩，对齐 Create `SandPaperItemComponent` 的 record 包法） |
| `registry/ModItems.java` | `DUMPLING_WRAPPER` 改用 `DumplingWrapperItem` |
| `event/DumplingEvents.java` | 删 `onCuttingBoard` 及 `onRightClickBlock` 中的砧板分支；清理仅其使用的 import（`CuttingBoardBlock(Entity)`、`Direction`、`ItemEntity`、`ParticleTypes`）。厨锅/汤锅分支不动 |
| `assets/tothesky/lang/{zh_cn,en_us}.json` | `tooltip.tothesky.dumpling_wrapper.0` → "副手持馅料，长按右键包制"（本仓库 en_us 同样使用中文文案） |
| `docs/dumplings.md` | 流程第 2 步改写为手持包制；删「已知问题」节；代码结构表补充新类；差异节补充说明 |

## 依赖确认

- `CustomUseEffectsItem` 在 create slim jar 内 ✓
- `TriState` 在 ponder jar 内（build.gradle 已声明 ponder）✓
- 不新增任何依赖，无 mixin（符合硬约束）。

## 验证

- `gradlew build` 编译通过。
- 游戏内实测（用户执行）：主手皮+副手馅长按 → 16t 后生饺子入背包；松手 → 馅料退还；副手饺子类 → 提示且不消耗。
