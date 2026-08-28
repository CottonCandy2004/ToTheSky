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
