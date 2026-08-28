# Server 测试待办

## 危险派对多人模式测试

验证击鼓传花（`tothesky:hot_potato`）多场并发 + 全流程：

- [ ] 喝下危险派对 → 获得「击鼓传花」效果（橙色图标，名称显示正确），广播开场消息。
- [ ] 每 10 秒收到全服广播「xxx身上的击鼓传花还剩余xx秒」。
- [ ] 攻击别人 → 效果传给对方（对方获得效果 + 短暂失明/迟缓），广播传递消息，攻击者效果消失。
- [ ] 60 秒未传出 → 持有者被击杀 + 广播「没能及时把好运传给下一个人」。
- [ ] **多人同时喝** → 应见多场独立进行（各带各的时钟），互不覆盖、互不报错。
- [ ] 喝其它鸡尾酒（david / call_of_tahiti / free_nightingale / silent_midnight / shoal_in_dream / lago_de_texcoco）确认 buff / 自定义效果正常、不崩溃。
- [ ] 创造栏「胡萝卜厨房-鸡尾酒」标签能看到全部 7 种 fstwines 鸡尾酒。

排查提示：若某场异常，看 `run/logs/latest.log` 是否有 `hot_potato` / `DelayedTasks` 相关的 exception。

## 下界合金产线 + 魔女因子链测试

验证整条产线在生存/创造模式下可执行、不崩溃：

- [ ] **流体**：`netherite_liquar` / `unstable_netherite_liquar` 流体桶在创造栏可见，放置后显示正确贴图（静态/流动），收回桶正常。
- [ ] **unstable_netherite_liquar**：mixing heated（血瓶+下界溶液原料）产出正确。
- [ ] **netherite_liquar**：mixing 产出正确。
- [ ] **impure_alloy_base**：mixing superheated（钻石+铁+金）产出正确。
- [ ] **raw_alloy_base**：compacting（不纯合金坯+岩浆）产出正确。
- [ ] **netherite_ingot（序列组装）**：sequenced assembly loops5（filling 下界溶液 + pressing）最终产出下界合金锭。
- [ ] **netherite_ingot（粉碎）**：下界合金块 → 9 锭 crushing 正确。
- [ ] **ancient_debris**：mixing 产出远古残骸。
- [ ] **netherite_scrap**：下界合金锭 → 4 碎片 crushing 正确。
- [ ] **原生 netherite_ingot 配方已屏蔽**：工作台 4 金 + 4 碎片 不应再合成下界合金锭（neoforge:false 覆盖生效）。
- [ ] **witch_factor**：compacting superheated（AE2 奇点 + scrap + soul_bead + netherite_liquar 50mB）产出。
- [ ] **activated_witch_factor**：createaddition charging（50000 RF）产出。
- [ ] **drink659**：brewing_barrel（3600t）产出饮品659。

排查提示：若配方未识别，看 `run/logs/latest.log` 是否有 `Unknown recipe type` / `Failed to parse recipe` 相关报错；确认 CEI / 龙加 / createaddition / ae2 依赖全部加载（0 missing）。

## 食物配方测试

验证 21 个新增食物配方在对应机器/工作台可合成：

- [ ] **farmersdelight 厨锅**：squid_festival / pasta_with_chocolate / delta_porridge / caramel_cod_soup / ramen 五道菜烹饪正常，返回空碗。
- [ ] **farmersdelight 切板**：cut_cheese（奶酪 → 4 切制奶酪，需刀）。
- [ ] **熔炉/烟熏炉**：sunshine_cod / pizza_margarita / pork_pizza / apple_pizza 烟熏正常。
- [ ] **工作台合成**：raw_pizza 三种（shaped）+ beef_over_rice + bug_soup 合成正常。
- [ ] **create mixing**：raw_sunshine_cod（三角币+cod+烈焰粉）、phantom_shrimp（火药+4熟虾+幻翼膜）、fried_cod（森罗油脂+生鳕鱼，heated）。
- [ ] **create compacting**：cheese（牛奶 1000mB）、digestion_pellow（×3）。
- [ ] **create pressing**：pizza_base（面团 tag）。
- [ ] **create sequencedAssembly**：三种披萨序列组装，确认特色食材（猪肉/苹果）步骤排在 tomato 之前。
- [ ] **cod_burger**：shapeless + create mixing 两种方式都能合成。
- [ ] **bomb_cod_burger**：shapeless（面包+油炸鳕鱼+爆弹椒+酸黄瓜）合成后食用爆炸正常。

注意事项：
- `delta_coin` / `delta_coin_chip` / `delta_dust` 上游来自经济系统（sell&roll），目前只能创造模式获取——生晴天鳕鱼 / 三角粥 / 饮品659 暂无完整生存链。
- `fried_cod` 用 `kaleidoscope_cookery:oil`（森罗物语油脂物品）替代原 kjs 的 `bean_oil` 流体。
- `bug_soup` / `bomb_cod_burger` 用的辣椒为 `mynethersdelight:bullet_pepper`（爆弹椒），非原 kjs 的 chilipepper tag。
