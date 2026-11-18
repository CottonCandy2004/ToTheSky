package com.fst.tothesky.arcade.games;

import com.mrh0.buildersaddition.arcade.ArcadeGame;
import com.mrh0.buildersaddition.arcade.ArcadeScreen;
import com.mrh0.buildersaddition.tileentity.ArcadeTileEntity;
import java.util.Arrays;
import net.minecraft.client.Minecraft;

/**
 * BuildersAddition 街机上的俄罗斯方块（40 x 25 字符屏，每格 8px）。
 *
 * <p>屏幕布局（左上角为 0,0）：
 * <pre>
 *   y=0        居中标题 "TETRIS"
 *   x=4 / x=15 井的左右边界（y=2..22），y=22 为底边，颜色 8（深灰）
 *   x=5..14    井内 10 列，y=2..21 井内 20 行
 *   x=17 起    侧栏：SCORE / LINES / LEVEL / NEXT（4x4 预览）
 *   y=23/24    操作提示
 * </pre>
 *
 * <p>节奏：{@link #frame(long)} 每个客户端 tick（20/s）调用一次，重力用 {@code time} 取模驱动；
 * 所有状态变化后整屏重绘（1000 格，20fps 下开销可忽略）。
 *
 * <p>注意：关闭街机 GUI 后方块实体仍在 tick，因此 {@code frame} 开头必须检查
 * {@code Minecraft.getInstance().screen == null} 直接返回。
 */
public final class TetrisGame extends ArcadeGame {

    // ---------------------------------------------------------------- 屏幕与井的几何

    private static final int SCREEN_W = 40;
    private static final int SCREEN_H = 25;

    /** 井内左上角在屏幕上的坐标。 */
    private static final int WELL_X = 5;
    private static final int WELL_Y = 2;
    /** 井内格子数。 */
    private static final int COLS = 10;
    private static final int ROWS = 20;

    private static final int BORDER_COLOR = 8;

    private static final int SIDEBAR_X = 17;
    private static final int PREVIEW_X = SIDEBAR_X;
    private static final int PREVIEW_Y = 12;
    private static final int PREVIEW_SIZE = 4;

    /** R 键的 GLFW 键码。 */
    private static final int KEY_R = 82;

    /** 一次消除 1/2/3/4 行的基础分，最终得分还要乘以当前等级。 */
    private static final int[] LINE_CLEAR_SCORES = {0, 100, 300, 500, 800};

    /** 7 种方块的基础形状，顺序：I、O、T、S、Z、J、L（旋转时按方阵顺时针转 90 度）。 */
    private static final int[][][] SHAPES = {
            // I
            {
                    {0, 0, 0, 0},
                    {1, 1, 1, 1},
                    {0, 0, 0, 0},
                    {0, 0, 0, 0}
            },
            // O
            {
                    {1, 1},
                    {1, 1}
            },
            // T
            {
                    {0, 1, 0},
                    {1, 1, 1},
                    {0, 0, 0}
            },
            // S
            {
                    {0, 1, 1},
                    {1, 1, 0},
                    {0, 0, 0}
            },
            // Z
            {
                    {1, 1, 0},
                    {0, 1, 1},
                    {0, 0, 0}
            },
            // J
            {
                    {1, 0, 0},
                    {1, 1, 1},
                    {0, 0, 0}
            },
            // L
            {
                    {0, 0, 1},
                    {1, 1, 1},
                    {0, 0, 0}
            }
    };

    /** 与 {@link #SHAPES} 同序的配色：I=11 O=14 T=5 S=10 Z=12 J=9 L=6。 */
    private static final int[] TYPE_COLORS = {11, 14, 5, 10, 12, 9, 6};

    /** 标题 "TETRIS" 每个字母的颜色。 */
    private static final int[] TITLE_COLORS = {11, 14, 5, 10, 12, 9};

    // ---------------------------------------------------------------- 游戏状态

    /** 已锁定方块的颜色表，0 表示空。 */
    private final int[][] board = new int[ROWS][COLS];

    /** 当前方块形状（方阵副本，旋转不影响 {@link #SHAPES}）。 */
    private int[][] shape;
    /** 当前方块的方阵左上角在井内的格子坐标。 */
    private int pieceX;
    private int pieceY;
    private int pieceColor;

    /** 下一个方块类型（侧栏预览用）。 */
    private int nextType;

    private int score;
    private int lines;
    private boolean gameOver;

    public TetrisGame(ArcadeScreen s, ArcadeTileEntity te) {
        super(s, te);
        // 先备好一个可玩的局面，即使 start() 之前就有人调 frame() 也不会空指针。
        this.nextType = randomType();
        this.spawnPiece();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void start() {
        super.start();
        this.reset();
    }

    @Override
    public void frame(long time) {
        // 关闭 GUI 后方块实体仍在 tick，此时不应继续推进游戏（也不该重绘别人的画面）。
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        super.frame(time);
        if (!this.gameOver && time % (long) this.dropInterval() == 0L) {
            if (!this.tryMove(0, 1)) {
                this.lockPiece();
            }
        }
        this.render();
    }

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (key == KEY_R) {
            this.reset();
            return;
        }
        if (this.gameOver) {
            return;
        }
        if (isLeft(key)) {
            this.tryMove(-1, 0);
        } else if (isRight(key)) {
            this.tryMove(1, 0);
        } else if (isDown(key)) {
            // 软降：落不动就直接锁定
            if (!this.tryMove(0, 1)) {
                this.lockPiece();
            }
        } else if (isUp(key)) {
            this.rotate();
        } else if (isSpace(key)) {
            this.hardDrop();
        }
        this.render();
    }

    // ---------------------------------------------------------------- 规则

    private void reset() {
        for (int row = 0; row < ROWS; row++) {
            Arrays.fill(this.board[row], 0);
        }
        this.score = 0;
        this.lines = 0;
        this.gameOver = false;
        this.nextType = randomType();
        this.spawnPiece();
        this.render();
    }

    /** 从顶部中央生成下一个方块；生成位置就被占住则游戏结束。 */
    private void spawnPiece() {
        int type = this.nextType;
        this.nextType = randomType();
        this.shape = copyShape(SHAPES[type]);
        this.pieceColor = TYPE_COLORS[type];
        this.pieceX = (COLS - this.shape.length) / 2;
        this.pieceY = 0;
        if (this.collides(this.shape, this.pieceX, this.pieceY)) {
            this.gameOver = true;
        }
    }

    private boolean tryMove(int dx, int dy) {
        int nextX = this.pieceX + dx;
        int nextY = this.pieceY + dy;
        if (this.collides(this.shape, nextX, nextY)) {
            return false;
        }
        this.pieceX = nextX;
        this.pieceY = nextY;
        return true;
    }

    /** 顺时针旋转；贴墙或贴块转不动时保持原状（不做踢墙）。 */
    private void rotate() {
        int[][] rotated = rotateClockwise(this.shape);
        if (!this.collides(rotated, this.pieceX, this.pieceY)) {
            this.shape = rotated;
        }
    }

    private void hardDrop() {
        while (!this.collides(this.shape, this.pieceX, this.pieceY + 1)) {
            this.pieceY++;
        }
        this.lockPiece();
    }

    private void lockPiece() {
        for (int row = 0; row < this.shape.length; row++) {
            for (int col = 0; col < this.shape[row].length; col++) {
                if (this.shape[row][col] == 0) {
                    continue;
                }
                int boardX = this.pieceX + col;
                int boardY = this.pieceY + row;
                if (boardX >= 0 && boardX < COLS && boardY >= 0 && boardY < ROWS) {
                    this.board[boardY][boardX] = this.pieceColor;
                }
            }
        }
        int cleared = this.clearFullRows();
        if (cleared > 0) {
            int level = this.level();
            this.score += LINE_CLEAR_SCORES[Math.min(cleared, LINE_CLEAR_SCORES.length - 1)] * level;
            this.lines += cleared;
        }
        this.spawnPiece();
    }

    /** 消掉所有满行并把上面的行整体下压；返回消除行数。 */
    private int clearFullRows() {
        int cleared = 0;
        for (int row = ROWS - 1; row >= 0; row--) {
            if (!this.isRowFull(row)) {
                continue;
            }
            for (int r = row; r > 0; r--) {
                System.arraycopy(this.board[r - 1], 0, this.board[r], 0, COLS);
            }
            Arrays.fill(this.board[0], 0);
            cleared++;
            // 下压后同一行还要再判一次
            row++;
        }
        return cleared;
    }

    private boolean isRowFull(int row) {
        for (int col = 0; col < COLS; col++) {
            if (this.board[row][col] == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean collides(int[][] matrix, int originX, int originY) {
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                if (matrix[row][col] == 0) {
                    continue;
                }
                int boardX = originX + col;
                int boardY = originY + row;
                if (boardX < 0 || boardX >= COLS || boardY >= ROWS) {
                    return true;
                }
                if (boardY >= 0 && this.board[boardY][boardX] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 等级 = 已消行数 / 10 + 1。 */
    private int level() {
        return this.lines / 10 + 1;
    }

    /** 每多少 tick 自然下落一格：1 级 10 tick，逐级 -1，最快 2 tick。 */
    private int dropInterval() {
        return Math.max(2, 10 - (this.level() - 1));
    }

    // ---------------------------------------------------------------- 绘制

    private void render() {
        this.s.setColors(0, 15);
        this.s.clear();
        this.drawTitle();
        this.drawWellBorder();
        this.drawStack();
        if (!this.gameOver) {
            this.drawCurrentPiece();
        }
        this.drawSidebar();
        this.drawHints();
        if (this.gameOver) {
            this.drawGameOverPanel();
        }
    }

    private void drawTitle() {
        String title = "TETRIS";
        int x = (SCREEN_W - title.length()) / 2;
        for (int i = 0; i < title.length(); i++) {
            this.s.setColors(0, TITLE_COLORS[i % TITLE_COLORS.length]);
            this.s.print(x + i, 0, String.valueOf(title.charAt(i)));
        }
    }

    private void drawWellBorder() {
        int left = WELL_X - 1;
        int right = WELL_X + COLS;
        int bottom = WELL_Y + ROWS;
        for (int y = WELL_Y; y <= bottom; y++) {
            this.fill(left, y, BORDER_COLOR);
            this.fill(right, y, BORDER_COLOR);
        }
        for (int x = left; x <= right; x++) {
            this.fill(x, bottom, BORDER_COLOR);
        }
    }

    private void drawStack() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int color = this.board[row][col];
                if (color != 0) {
                    this.fill(WELL_X + col, WELL_Y + row, color);
                }
            }
        }
    }

    private void drawCurrentPiece() {
        if (this.shape == null) {
            return;
        }
        for (int row = 0; row < this.shape.length; row++) {
            for (int col = 0; col < this.shape[row].length; col++) {
                if (this.shape[row][col] != 0) {
                    this.fill(WELL_X + this.pieceX + col, WELL_Y + this.pieceY + row, this.pieceColor);
                }
            }
        }
    }

    private void drawSidebar() {
        this.label(SIDEBAR_X, 2, "SCORE");
        this.value(SIDEBAR_X, 3, this.score);
        this.label(SIDEBAR_X, 5, "LINES");
        this.value(SIDEBAR_X, 6, this.lines);
        this.label(SIDEBAR_X, 8, "LEVEL");
        this.value(SIDEBAR_X, 9, this.level());
        this.label(SIDEBAR_X, 11, "NEXT");
        this.drawNextPreview();
    }

    private void drawNextPreview() {
        int[][] next = SHAPES[this.nextType];
        int size = next.length;
        int x = PREVIEW_X + (PREVIEW_SIZE - size) / 2;
        int y = PREVIEW_Y + (PREVIEW_SIZE - size) / 2;
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (next[row][col] != 0) {
                    this.fill(x + col, y + row, TYPE_COLORS[this.nextType]);
                }
            }
        }
    }

    private void drawHints() {
        // 完整提示 50 字符放不进 40 列，拆成两行居中。
        this.centeredText(23, "ARROWS/WASD MOVE   UP ROTATE", 7);
        this.centeredText(24, "SPACE DROP   R RESTART", 7);
    }

    private void drawGameOverPanel() {
        int left = 6;
        int right = 33;
        int top = 8;
        int bottom = 15;
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                this.fill(x, y, 0);
            }
        }
        for (int x = left; x <= right; x++) {
            this.fill(x, top, BORDER_COLOR);
            this.fill(x, bottom, BORDER_COLOR);
        }
        for (int y = top; y <= bottom; y++) {
            this.fill(left, y, BORDER_COLOR);
            this.fill(right, y, BORDER_COLOR);
        }
        this.centeredText(9, "GAME OVER", 12);
        this.centeredText(11, "SCORE " + this.score, 15);
        this.centeredText(13, "Press R to restart", 14);
    }

    private void label(int x, int y, String text) {
        this.s.setColors(0, 7);
        this.s.print(x, y, text);
    }

    private void value(int x, int y, int number) {
        this.s.setColors(0, 15);
        this.s.print(x, y, Integer.toString(number));
    }

    private void centeredText(int y, String text, int fg) {
        int x = (SCREEN_W - text.length()) / 2;
        this.s.setColors(0, fg);
        this.s.print(Math.max(0, x), y, text);
    }

    /** 画一个实心色块；坐标越界时直接忽略（{@code getIndex} 会把越界索引夹到 0）。 */
    private void fill(int x, int y, int color) {
        if (x < 0 || x >= SCREEN_W || y < 0 || y >= SCREEN_H) {
            return;
        }
        this.s.screen[this.s.getIndex(x, y)] = ArcadeScreen.encode('\0', color, color);
    }

    // ---------------------------------------------------------------- 工具方法

    private static int randomType() {
        return (int) (Math.random() * SHAPES.length);
    }

    private static int[][] copyShape(int[][] matrix) {
        int[][] copy = new int[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            copy[row] = matrix[row].clone();
        }
        return copy;
    }

    private static int[][] rotateClockwise(int[][] matrix) {
        int size = matrix.length;
        int[][] rotated = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                rotated[col][size - 1 - row] = matrix[row][col];
            }
        }
        return rotated;
    }
}
