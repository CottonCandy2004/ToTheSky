package com.fst.tothesky.arcade.games;

import com.mrh0.buildersaddition.arcade.ArcadeGame;
import com.mrh0.buildersaddition.arcade.ArcadeScreen;
import com.mrh0.buildersaddition.tileentity.ArcadeTileEntity;

import java.util.Random;

/**
 * 街机版 2048：4x4 棋盘，方向键 / WASD 滑动合并，R 重开。
 * <p>
 * 画面坐标：屏幕 40x25（每格 8px），棋盘左上角 (8, 4)；
 * 每格 6 字符宽、4 行高，所以棋盘占 x = 8..31、y = 4..19。
 * <ul>
 *   <li>y = 0：居中标题 "2048"</li>
 *   <li>y = 1：分数（棋盘正上方，左标签 + 右对齐数值）</li>
 *   <li>y = 20..22：状态提示（胜利 / 失败）</li>
 *   <li>y = 24：操作说明</li>
 * </ul>
 * 屏幕上的文字全部为 ASCII（字体按单字节字符渲染）。
 * 只在物理客户端加载，见 {@code com.fst.tothesky.arcade.ArcadeGames}。
 */
public final class Game2048 extends ArcadeGame {

    /** 棋盘边长（4x4）。 */
    private static final int SIZE = 4;
    /** 棋盘左上角。 */
    private static final int BOARD_X = 8;
    private static final int BOARD_Y = 4;
    /** 每格 6 字符宽 x 4 行高。 */
    private static final int CELL_W = 6;
    private static final int CELL_H = 4;
    /** 4 行高的格子里放数字的那一行：留 1 行上边距、2 行下边距。 */
    private static final int TEXT_ROW = (CELL_H - 1) / 2;
    /** 棋盘总宽，用于右对齐分数。 */
    private static final int BOARD_W = SIZE * CELL_W;
    private static final String BLANK_CELL = " ".repeat(CELL_W);

    private static final String TITLE = "2048";
    private static final String FOOTER = "ARROWS/WASD move  R restart";
    /** GLFW 键码：R。 */
    private static final int KEY_R = 82;

    private static final int DIR_LEFT = 0;
    private static final int DIR_RIGHT = 1;
    private static final int DIR_UP = 2;
    private static final int DIR_DOWN = 3;

    /** board[row][col]，0 表示空格。 */
    private final int[][] board = new int[SIZE][SIZE];
    private final Random random = new Random();

    private int score;
    private boolean gameOver;
    /** 2048 是否已经出现过：提示只弹一次，之后仍可继续玩。 */
    private boolean won;
    /** 当前画面是否要显示 YOU WIN 提示（下一次有效移动后消失）。 */
    private boolean showWin;

    public Game2048(ArcadeScreen s, ArcadeTileEntity te) {
        super(s, te);
    }

    @Override
    public void start() {
        super.start();
        restart();
    }

    /** 重开：清空棋盘、放两个初始块、画首屏。 */
    private void restart() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                board[row][col] = 0;
            }
        }
        score = 0;
        gameOver = false;
        won = false;
        showWin = false;
        spawnTile();
        spawnTile();
        render();
    }

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (key == KEY_R) {
            restart();
            return;
        }
        if (gameOver) {
            return;
        }
        if (isUp(key)) {
            move(DIR_UP);
        } else if (isDown(key)) {
            move(DIR_DOWN);
        } else if (isLeft(key)) {
            move(DIR_LEFT);
        } else if (isRight(key)) {
            move(DIR_RIGHT);
        }
    }

    /**
     * 执行一次滑动：只有真的发生变化才算有效移动，才生成新块并重绘；
     * 否则不生成新块（无效移动）。
     */
    private void move(int dir) {
        if (!slide(dir)) {
            return;
        }
        showWin = false;
        spawnTile();
        if (!won && anyTile2048()) {
            won = true;
            showWin = true;
        }
        gameOver = !hasMove();
        render();
    }

    /**
     * 把四行/四列朝 dir 方向压缩合并。
     * 每行从目标方向的边缘开始扫描：相邻的两个相同数字合并一次，
     * 合并出来的新块本次移动内不再参与合并。
     *
     * @return 棋盘是否发生了变化
     */
    private boolean slide(int dir) {
        boolean changed = false;
        int gained = 0;
        for (int line = 0; line < SIZE; line++) {
            int[] oldLine = new int[SIZE];
            for (int i = 0; i < SIZE; i++) {
                oldLine[i] = getTile(dir, line, i);
            }
            int[] newLine = new int[SIZE];
            int write = 0;
            int read = 0;
            while (read < SIZE) {
                int value = oldLine[read];
                if (value == 0) {
                    read++;
                    continue;
                }
                int next = read + 1;
                while (next < SIZE && oldLine[next] == 0) {
                    next++;
                }
                if (next < SIZE && oldLine[next] == value) {
                    int merged = value * 2;
                    newLine[write++] = merged;
                    gained += merged;
                    read = next + 1;
                } else {
                    newLine[write++] = value;
                    read = next;
                }
            }
            for (int i = 0; i < SIZE; i++) {
                if (oldLine[i] != newLine[i]) {
                    changed = true;
                }
                setTile(dir, line, i, newLine[i]);
            }
        }
        if (changed) {
            score += gained;
        }
        return changed;
    }

    /** i = 0 表示该方向上最靠边的格子。 */
    private int getTile(int dir, int line, int i) {
        switch (dir) {
            case DIR_LEFT:
                return board[line][i];
            case DIR_RIGHT:
                return board[line][SIZE - 1 - i];
            case DIR_UP:
                return board[i][line];
            default:
                return board[SIZE - 1 - i][line];
        }
    }

    private void setTile(int dir, int line, int i, int value) {
        switch (dir) {
            case DIR_LEFT:
                board[line][i] = value;
                break;
            case DIR_RIGHT:
                board[line][SIZE - 1 - i] = value;
                break;
            case DIR_UP:
                board[i][line] = value;
                break;
            default:
                board[SIZE - 1 - i][line] = value;
                break;
        }
    }

    /** 在随机空格生成新块：90% 是 2，10% 是 4。 */
    private void spawnTile() {
        int[] empty = new int[SIZE * SIZE];
        int count = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == 0) {
                    empty[count++] = row * SIZE + col;
                }
            }
        }
        if (count == 0) {
            return;
        }
        int index = empty[random.nextInt(count)];
        board[index / SIZE][index % SIZE] = random.nextInt(10) < 9 ? 2 : 4;
    }

    private boolean anyTile2048() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] >= 2048) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 还有空格，或者存在可合并的相邻格子，就还能继续。 */
    private boolean hasMove() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int value = board[row][col];
                if (value == 0) {
                    return true;
                }
                if (col + 1 < SIZE && board[row][col + 1] == value) {
                    return true;
                }
                if (row + 1 < SIZE && board[row + 1][col] == value) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 渲染

    /** 全量重绘（每次有效移动后调用）。 */
    private void render() {
        s.setColors(0, 0);
        s.clear();

        // y = 0：标题
        s.setColors(0, 14);
        s.print(centerX(TITLE), 0, TITLE);

        // y = 1：棋盘上方的分数，数值右对齐到棋盘右边缘
        s.setColors(0, 7);
        s.print(BOARD_X, 1, "SCORE");
        String scoreText = Integer.toString(score);
        s.setColors(0, 15);
        s.print(BOARD_X + BOARD_W - scoreText.length(), 1, scoreText);

        drawBoard();
        drawStatus();

        // y = 24：操作说明
        s.setColors(0, 7);
        s.print(centerX(FOOTER), 24, FOOTER);
    }

    private void drawBoard() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                drawCell(col, row, board[row][col]);
            }
        }
    }

    /** 空格整块深灰底；数字格按值上色，数字在格内水平、垂直居中。 */
    private void drawCell(int col, int row, int value) {
        int x = BOARD_X + col * CELL_W;
        int y = BOARD_Y + row * CELL_H;
        if (value == 0) {
            s.setColors(8, 8);
            for (int i = 0; i < CELL_H; i++) {
                s.print(x, y + i, BLANK_CELL);
            }
            return;
        }

        s.setColors(tileBg(value), tileFg(value));
        String text = Integer.toString(value);
        if (text.length() > CELL_W) {
            // 实战中到不了（6 位以内已够），防御性截断避免画到隔壁格
            text = text.substring(0, CELL_W);
        }
        int pad = (CELL_W - text.length()) / 2;
        String line = " ".repeat(pad) + text + " ".repeat(CELL_W - pad - text.length());
        for (int i = 0; i < CELL_H; i++) {
            s.print(x, y + i, i == TEXT_ROW ? line : BLANK_CELL);
        }
    }

    private void drawStatus() {
        if (gameOver) {
            drawCentered(20, "GAME OVER", 12);
            drawCentered(21, "SCORE " + score, 15);
            drawCentered(22, "PRESS R TO RESTART", 14);
        } else if (showWin) {
            drawCentered(20, "YOU WIN!", 14);
            drawCentered(21, "KEEP GOING", 15);
        }
    }

    private void drawCentered(int y, String text, int fg) {
        s.setColors(0, fg);
        s.print(centerX(text), y, text);
    }

    private int centerX(String text) {
        return (s.width - text.length()) / 2;
    }

    // ---------------------------------------------------------------- 配色

    /** 数字格背景色（按规格表）。 */
    private static int tileBg(int value) {
        return switch (value) {
            case 2 -> 7;
            case 4 -> 14;
            case 8 -> 6;
            case 16 -> 12;
            case 32 -> 4;
            case 64 -> 13;
            case 128 -> 11;
            case 256 -> 3;
            case 512 -> 10;
            case 1024 -> 2;
            case 2048 -> 9;
            default -> 5;
        };
    }

    /** 数字前景色：浅色底用黑字，深色底用白字。 */
    private static int tileFg(int value) {
        return switch (value) {
            case 2, 4, 8, 16, 64, 128, 512 -> 0;
            default -> 15;
        };
    }
}
