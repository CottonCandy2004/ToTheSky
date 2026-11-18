package com.fst.tothesky.arcade;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.arcade.games.Game2048;
import com.fst.tothesky.arcade.games.MinesweeperGame;
import com.fst.tothesky.arcade.games.PacmanGame;
import com.fst.tothesky.arcade.games.TetrisGame;
import com.mrh0.buildersaddition.arcade.ArcadeManager;

/**
 * 把 ToTheSky 自己写的街机游戏挂到 BuildersAddition 的街机菜单里。
 * <p>
 * 上游 {@link ArcadeManager} 是一个公开单例（{@code instance}），{@code add(GameConstructor, key)}
 * 接受 {@code (ArcadeScreen, ArcadeTileEntity) -> ArcadeGame} 的构造器引用；菜单里显示的名字来自
 * 翻译键 {@code "arcade.buildersaddition.game." + key}，键虽然带 buildersaddition 命名空间，
 * 但语言文件是全模组合并的，写在 {@code assets/tothesky/lang} 里同样生效。
 * <p>
 * 注意：只在物理客户端调用（见 {@link ToTheSkyArcadeClient}），因为游戏基类持有客户端 GUI 的
 * {@code ArcadeScreen}，服务端不该加载这些类。
 */
public final class ArcadeGames {

    public static void register() {
        ArcadeManager manager = ArcadeManager.instance;
        if (manager == null) {
            ToTheSky.LOGGER.warn("街机管理器尚未初始化（BuildersAddition 未完成构造），跳过注册");
            return;
        }

        manager.add(TetrisGame::new, "tothesky_tetris");
        manager.add(MinesweeperGame::new, "tothesky_minesweeper");
        manager.add(Game2048::new, "tothesky_2048");
        manager.add(PacmanGame::new, "tothesky_pacman");

        ToTheSky.LOGGER.info("已注册 ToTheSky 街机游戏：俄罗斯方块 / 扫雷 / 2048 / 吃豆人");
    }

    private ArcadeGames() {
    }
}
