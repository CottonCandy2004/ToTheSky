package com.fst.tothesky.arcade;

import com.fst.tothesky.ToTheSky;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 街机（BuildersAddition 的 Arcade）扩展入口。
 * <p>
 * 本类刻意不 import 任何 buildersaddition 的类：{@link ArcadeGames} 才持有对上游 API 的引用。
 * 这样在未安装 BuildersAddition 的实例上，{@link #onClientSetup} 的 {@code ModList.isLoaded}
 * 判断会先返回，JVM 根本不会去解析 {@link ArcadeGames}，不会出现 NoClassDefFoundError。
 * <p>
 * 时机：上游 {@code BuildersAddition} 构造函数里就调用了 {@code ArcadeManager.init()}，
 * 早于一切 setup 事件，因此这里注册时单例一定已就绪；上游 {@code ArcadeMenu} 的游戏列表
 * 是每次打开 GUI 时重建的，所以后注册的游戏也能出现在菜单里。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ToTheSkyArcadeClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("buildersaddition")) {
            return;
        }
        event.enqueueWork(() -> {
            try {
                ArcadeGames.register();
            } catch (Throwable t) {
                // 上游签名变化/注册顺序异常都不该拖垮客户端加载
                ToTheSky.LOGGER.warn("街机游戏注册失败，跳过 ToTheSky 街机扩展", t);
            }
        });
    }

    private ToTheSkyArcadeClient() {
    }
}
