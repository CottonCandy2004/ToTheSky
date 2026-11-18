package com.fst.tothesky.arcade.games;

import com.mrh0.buildersaddition.arcade.ArcadeGame;
import com.mrh0.buildersaddition.arcade.ArcadeScreen;
import com.mrh0.buildersaddition.tileentity.ArcadeTileEntity;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.Minecraft;

/**
 * BuildersAddition 街机上的吃豆人。
 *
 * <p>屏幕是 40x25 字符（每格 8px），原版迷宫 28x31 放不下，这里用一张手绘的
 * 28x21 迷你迷宫（{@link #MAZE}），放在屏幕 (1,2) 起的位置，右侧 x=30 起是信息栏。
 *
 * <p>操作：方向键 / WASD 移动，R 重开。没有鼠标（上游 ArcadeGui 不转发鼠标事件）。
 * 关闭 GUI 后上游方块实体仍会 tick，所以 {@link #frame(long)} 开头检查
 * {@code Minecraft.getInstance().screen == null} 直接返回，相当于暂停。
 *
 * <p>实现要点：
 * <ul>
 *   <li>格子逻辑：吃豆人每 {@link #PAC_INTERVAL} tick 走一格，鬼每 {@link #GHOST_INTERVAL} tick
 *       走一格；方向键只记录“期望方向”，每到一格能转就转，撞墙就停下。</li>
 *   <li>鬼的移动用 BFS 距离场选方向（每次决策一次 BFS，28x21 = 588 格，开销可忽略），
 *       追击目标按四只鬼的性格区分；frightened 时随机走，被吃后回鬼屋。</li>
 *   <li>迷宫第 14 行左右两端开口，做环绕传送。</li>
 * </ul>
 */
public final class PacmanGame extends ArcadeGame {

    // ---------------------------------------------------------------- 迷宫与屏幕布局

    private static final int MAZE_X = 1;
    private static final int MAZE_Y = 2;
    private static final int COLS = 28;
    private static final int ROWS = 21;

    /** 左右环绕传送所在的行。 */
    private static final int TUNNEL_ROW = 14;

    private static final char WALL = '#';
    private static final char GATE = '-';
    private static final char HOUSE = ' ';
    private static final char DOT = '.';
    private static final char POWER = 'o';

    /**
     * 28x21 迷你迷宫（每行必须 28 字符）。图例：
     * {@code #} 墙、{@code .} 豆子、{@code o} 大力丸、{@code -} 鬼屋门、空格为鬼屋内部。
     * 左右对称；第 14 行两端开口做隧道。
     */
    private static final String[] MAZE = {
            "############################",
            "#............##............#",
            "#.####.#####.##.#####.####.#",
            "#o####.#####.##.#####.####o#",
            "#.####.#####.##.#####.####.#",
            "#..........................#",
            "#.####.##.########.##.####.#",
            "#.####.##.########.##.####.#",
            "#......##....##....##......#",
            "######.#####.##.#####.######",
            "######.#####.##.#####.######",
            "######.##..........##.######",
            "######.##.###--###.##.######",
            "######.##.#      #.##.######",
            "..........#      #..........",
            "######.##.#      #.##.######",
            "######.##.########.##.######",
            "######.##..........##.######",
            "######.##.########.##.######",
            "#o...........##...........o#",
            "############################"
    };

    // ---------------------------------------------------------------- 方向与节奏

    private static final int DIR_UP = 0;
    private static final int DIR_RIGHT = 1;
    private static final int DIR_DOWN = 2;
    private static final int DIR_LEFT = 3;

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    /** 吃豆人每 4 tick 走一格（5 格/秒）。 */
    private static final int PAC_INTERVAL = 4;
    /** 普通鬼每 5 tick 走一格。 */
    private static final int GHOST_INTERVAL = 5;
    /** frightened 鬼每 7 tick 走一格。 */
    private static final int FRIGHT_INTERVAL = 7;
    /** 被吃的鬼（回鬼屋）每 3 tick 走一格。 */
    private static final int EATEN_INTERVAL = 3;

    private static final long FRIGHT_TICKS = 160L;   // 8 秒
    private static final long READY_TICKS = 40L;     // 2 秒
    private static final long DYING_TICKS = 30L;     // 1.5 秒
    private static final long LEVEL_TICKS = 50L;     // 2.5 秒
    private static final long HOUSE_WAIT = 60L;      // 被吃后回屋等待 3 秒
    private static final long SCATTER_TICKS = 140L;
    private static final long CHASE_TICKS = 280L;

    private static final int START_LIVES = 3;
    private static final int PAC_SPAWN_COL = 13;
    private static final int PAC_SPAWN_ROW = 17;
    private static final int GHOST_HOME_COL = 13;
    private static final int GHOST_HOME_ROW = 14;
    private static final int GHOST_EXIT_COL = 13;
    private static final int GHOST_EXIT_ROW = 11;

    /** 鬼的出生格（鬼屋内部）。 */
    private static final int[][] GHOST_SPAWNS = {
            {12, 14}, {13, 14}, {14, 14}, {15, 14}
    };
    /** 四只鬼的配色：红、粉、青、橙。 */
    private static final int[] GHOST_COLORS = {12, 13, 11, 6};
    /** 四只鬼的散开目标（迷宫四角）。 */
    private static final int[][] SCATTER_TARGETS = {
            {COLS - 2, 0}, {1, 0}, {COLS - 2, ROWS - 1}, {1, ROWS - 1}
    };

    private static final int KEY_R = 82;

    private enum State {
        READY, PLAYING, DYING, GAME_OVER, LEVEL_CLEAR
    }

    private static final class Ghost {
        int col;
        int row;
        int dir;
        final int kind;
        boolean inHouse = true;
        boolean eaten;
        boolean frightened;
        long nextMove;

        Ghost(int kind) {
            this.kind = kind;
            this.dir = DIR_UP;
        }
    }

    // ---------------------------------------------------------------- 状态

    private final char[][] cells = new char[ROWS][COLS];
    private final boolean[][] walkable = new boolean[ROWS][COLS];
    private final boolean[][] pellets = new boolean[ROWS][COLS];
    private final boolean[][] power = new boolean[ROWS][COLS];

    private final Ghost[] ghosts = {new Ghost(0), new Ghost(1), new Ghost(2), new Ghost(3)};
    private final Random random = new Random();

    private State state = State.READY;
    private long stateEnd = -1L;
    private long now;

    private int pacCol = PAC_SPAWN_COL;
    private int pacRow = PAC_SPAWN_ROW;
    private int pacDir = DIR_LEFT;
    private int pacWantDir = DIR_LEFT;
    private long pacNextMove;

    private int score;
    private int lives = START_LIVES;
    private int level = 1;
    private int pelletsLeft;
    private int combo;

    private boolean frightActive;
    private long frightEnd;
    private boolean scatterMode = true;
    private long modeEnd;

    public PacmanGame(ArcadeScreen s, ArcadeTileEntity te) {
        super(s, te);
    }

    @Override
    public void start() {
        super.start();
        this.score = 0;
        this.lives = START_LIVES;
        this.level = 1;
        this.loadLevel();
        this.state = State.READY;
        this.stateEnd = -1L;
        this.render(0L);
    }

    // ---------------------------------------------------------------- 帧循环

    @Override
    public void frame(long time) {
        // 关闭 GUI 后游戏对象仍会被方块实体 tick：直接当暂停。
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        this.now = time;

        switch (this.state) {
            case READY -> {
                if (this.stateEnd < 0L) {
                    this.stateEnd = time + READY_TICKS;
                }
                if (time >= this.stateEnd) {
                    this.beginPlay(time);
                }
            }
            case PLAYING -> this.tickPlay(time);
            case DYING -> {
                if (time >= this.stateEnd) {
                    if (this.lives <= 0) {
                        this.state = State.GAME_OVER;
                        this.stateEnd = Long.MAX_VALUE;
                    } else {
                        this.resetActors(time);
                        this.state = State.READY;
                        this.stateEnd = time + READY_TICKS;
                    }
                }
            }
            case LEVEL_CLEAR -> {
                if (time >= this.stateEnd) {
                    this.level++;
                    this.loadLevel();
                    this.state = State.READY;
                    this.stateEnd = time + READY_TICKS;
                }
            }
            case GAME_OVER -> {
                // 等 R
            }
        }

        this.render(time);
    }

    private void beginPlay(long time) {
        this.state = State.PLAYING;
        this.scatterMode = true;
        this.modeEnd = time + SCATTER_TICKS;
        this.frightActive = false;
        this.combo = 0;
        this.pacNextMove = time;
        for (int i = 0; i < this.ghosts.length; i++) {
            this.ghosts[i].nextMove = time + 10L * i;
        }
    }

    private void tickPlay(long time) {
        if (this.frightActive && time >= this.frightEnd) {
            this.frightActive = false;
            for (Ghost g : this.ghosts) {
                g.frightened = false;
            }
        }
        if (time >= this.modeEnd) {
            this.scatterMode = !this.scatterMode;
            this.modeEnd = time + (this.scatterMode ? SCATTER_TICKS : CHASE_TICKS);
            for (Ghost g : this.ghosts) {
                if (!g.inHouse && !g.frightened) {
                    g.dir = (g.dir + 2) % 4;
                }
            }
        }

        if (time >= this.pacNextMove) {
            this.pacNextMove = time + PAC_INTERVAL;
            this.movePacman();
        }
        for (Ghost g : this.ghosts) {
            if (time >= g.nextMove) {
                this.stepGhost(g, time);
            }
        }
        this.checkCollisions(time);
    }

    // ---------------------------------------------------------------- 吃豆人

    private void movePacman() {
        if (this.pacWantDir >= 0 && this.canPacWalk(this.pacCol, this.pacRow, this.pacWantDir)) {
            this.pacDir = this.pacWantDir;
        }
        if (this.canPacWalk(this.pacCol, this.pacRow, this.pacDir)) {
            this.pacCol = this.wrapCol(this.pacCol + DX[this.pacDir], this.pacRow);
            this.pacRow += DY[this.pacDir];
            this.eatAt(this.pacCol, this.pacRow);
        }
    }

    private boolean canPacWalk(int col, int row, int dir) {
        if (dir < 0) {
            return false;
        }
        int col2 = this.wrapCol(col + DX[dir], row);
        int row2 = row + DY[dir];
        if (col2 < 0 || col2 >= COLS || row2 < 0 || row2 >= ROWS) {
            return false;
        }
        return this.walkable[row2][col2];
    }

    private void eatAt(int col, int row) {
        if (this.power[row][col]) {
            this.power[row][col] = false;
            this.pelletsLeft--;
            this.score += 50;
            this.startFright();
        } else if (this.pellets[row][col]) {
            this.pellets[row][col] = false;
            this.pelletsLeft--;
            this.score += 10;
        }
        if (this.pelletsLeft <= 0) {
            this.state = State.LEVEL_CLEAR;
            this.stateEnd = this.now + LEVEL_TICKS;
        }
    }

    private void startFright() {
        this.frightActive = true;
        this.frightEnd = this.now + FRIGHT_TICKS;
        this.combo = 0;
        for (Ghost g : this.ghosts) {
            if (!g.inHouse && !g.eaten) {
                g.frightened = true;
                g.dir = (g.dir + 2) % 4;
            }
        }
    }

    // ---------------------------------------------------------------- 鬼

    private void stepGhost(Ghost g, long time) {
        g.nextMove = time + this.ghostInterval(g);

        if (g.eaten) {
            // 被吃后先回鬼屋，再从门口出去
            if (g.inHouse) {
                this.moveGhostToward(g, GHOST_EXIT_COL, GHOST_EXIT_ROW, true);
                if (this.isOutsideHouse(g)) {
                    g.inHouse = false;
                    g.eaten = false;
                }
            }
            return;
        }

        if (g.inHouse) {
            this.moveGhostToward(g, GHOST_EXIT_COL, GHOST_EXIT_ROW, true);
            if (this.isOutsideHouse(g)) {
                g.inHouse = false;
                g.dir = DIR_UP;
            }
            return;
        }

        if (g.frightened) {
            this.moveGhostRandom(g);
            return;
        }

        int[] target = this.ghostTarget(g);
        int tc = Math.max(0, Math.min(COLS - 1, target[0]));
        int tr = Math.max(0, Math.min(ROWS - 1, target[1]));
        if (!this.isGhostWalkable(tc, tr, false)) {
            tc = this.pacCol;
            tr = this.pacRow;
        }
        this.moveGhostToward(g, tc, tr, false);
    }

    private int ghostInterval(Ghost g) {
        if (g.eaten) {
            return EATEN_INTERVAL;
        }
        if (g.frightened) {
            return FRIGHT_INTERVAL;
        }
        // 等级越高鬼越快（5 → 4 tick）
        return Math.max(4, GHOST_INTERVAL - (this.level - 1) / 3);
    }

    private int[] ghostTarget(Ghost g) {
        if (this.scatterMode) {
            return SCATTER_TARGETS[g.kind];
        }
        switch (g.kind) {
            case 0:
                return new int[]{this.pacCol, this.pacRow};
            case 1:
                return new int[]{this.pacCol + 2 * DX[this.pacDir], this.pacRow + 2 * DY[this.pacDir]};
            case 2:
                return new int[]{this.pacCol + 4 * DX[this.pacDir], this.pacRow + 4 * DY[this.pacDir]};
            default: {
                int d = Math.abs(g.col - this.pacCol) + Math.abs(g.row - this.pacRow);
                return d > 8
                        ? new int[]{this.pacCol, this.pacRow}
                        : new int[]{SCATTER_TARGETS[3][0], SCATTER_TARGETS[3][1]};
            }
        }
    }

    private void moveGhostToward(Ghost g, int tc, int tr, boolean allowHouse) {
        if (tc < 0 || tc >= COLS || tr < 0 || tr >= ROWS) {
            return;
        }
        int[] dist = this.distanceField(tc, tr, allowHouse);
        int current = dist[g.row * COLS + g.col];
        if (current < 0) {
            this.moveGhostRandom(g);
            return;
        }
        int reverse = (g.dir + 2) % 4;
        int bestDir = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int pass = 0; pass < 2 && bestDir < 0; pass++) {
            for (int d = 0; d < 4; d++) {
                if (pass == 0 && d == reverse) {
                    continue;
                }
                int nc = this.wrapCol(g.col + DX[d], g.row);
                int nr = g.row + DY[d];
                if (!this.isGhostWalkable(nc, nr, allowHouse)) {
                    continue;
                }
                int nd = dist[nr * COLS + nc];
                if (nd < 0) {
                    continue;
                }
                if (nd < bestDist) {
                    bestDist = nd;
                    bestDir = d;
                }
            }
        }
        if (bestDir >= 0) {
            g.dir = bestDir;
            g.col = this.wrapCol(g.col + DX[bestDir], g.row);
            g.row += DY[bestDir];
        }
    }

    private void moveGhostRandom(Ghost g) {
        int reverse = (g.dir + 2) % 4;
        int start = this.random.nextInt(4);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                int d = (start + i) % 4;
                if (pass == 0 && d == reverse) {
                    continue;
                }
                int nc = this.wrapCol(g.col + DX[d], g.row);
                int nr = g.row + DY[d];
                if (!this.isGhostWalkable(nc, nr, g.inHouse)) {
                    continue;
                }
                g.dir = d;
                g.col = nc;
                g.row = nr;
                return;
            }
        }
    }

    private boolean isOutsideHouse(Ghost g) {
        char cell = this.cells[g.row][g.col];
        return cell != HOUSE && cell != GATE;
    }

    // ---------------------------------------------------------------- 可行走判定

    private boolean isGhostWalkable(int col, int row, boolean allowHouse) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return false;
        }
        char cell = this.cells[row][col];
        if (cell == WALL) {
            return false;
        }
        if (cell == GATE) {
            return true;
        }
        if (cell == HOUSE) {
            return allowHouse;
        }
        return true;
    }

    /** 第 {@link #TUNNEL_ROW} 行左右环绕；其他行越界返回 -1。 */
    private int wrapCol(int col, int row) {
        if (row == TUNNEL_ROW) {
            if (col < 0) {
                return COLS - 1;
            }
            if (col >= COLS) {
                return 0;
            }
            return col;
        }
        if (col < 0 || col >= COLS) {
            return -1;
        }
        return col;
    }

    // ---------------------------------------------------------------- BFS 距离场

    private int[] distanceField(int targetCol, int targetRow, boolean allowHouse) {
        int[] dist = new int[ROWS * COLS];
        Arrays.fill(dist, -1);
        if (!this.isGhostWalkable(targetCol, targetRow, allowHouse)) {
            return dist;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = targetRow * COLS + targetCol;
        dist[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int col = cur % COLS;
            int row = cur / COLS;
            for (int d = 0; d < 4; d++) {
                int nc = this.wrapCol(col + DX[d], row);
                int nr = row + DY[d];
                if (nc < 0 || nc >= COLS || nr < 0 || nr >= ROWS) {
                    continue;
                }
                int next = nr * COLS + nc;
                if (dist[next] != -1 || !this.isGhostWalkable(nc, nr, allowHouse)) {
                    continue;
                }
                dist[next] = dist[cur] + 1;
                queue.add(next);
            }
        }
        return dist;
    }

    // ---------------------------------------------------------------- 碰撞与判定

    private void checkCollisions(long time) {
        for (Ghost g : this.ghosts) {
            if (g.inHouse || g.col != this.pacCol || g.row != this.pacRow) {
                continue;
            }
            if (g.frightened) {
                this.score += 200 << Math.min(this.combo, 3);
                this.combo++;
                g.eaten = true;
                g.frightened = false;
                g.inHouse = true;
                g.col = GHOST_HOME_COL;
                g.row = GHOST_HOME_ROW;
                g.nextMove = time + HOUSE_WAIT;
            } else {
                this.lives--;
                this.state = State.DYING;
                this.stateEnd = time + DYING_TICKS;
                return;
            }
        }
    }

    private void resetActors(long time) {
        this.pacCol = PAC_SPAWN_COL;
        this.pacRow = PAC_SPAWN_ROW;
        this.pacDir = DIR_LEFT;
        this.pacWantDir = DIR_LEFT;
        this.pacNextMove = time;
        this.frightActive = false;
        this.combo = 0;
        for (int i = 0; i < this.ghosts.length; i++) {
            Ghost g = this.ghosts[i];
            g.col = GHOST_SPAWNS[i][0];
            g.row = GHOST_SPAWNS[i][1];
            g.dir = DIR_UP;
            g.inHouse = true;
            g.eaten = false;
            g.frightened = false;
            g.nextMove = time;
        }
    }

    // ---------------------------------------------------------------- 关卡

    private void loadLevel() {
        int dots = 0;
        for (int r = 0; r < ROWS; r++) {
            String line = MAZE[r];
            for (int c = 0; c < COLS; c++) {
                char cell = line.charAt(c);
                this.cells[r][c] = cell;
                boolean isDot = cell == DOT;
                boolean isPower = cell == POWER;
                this.walkable[r][c] = isDot || isPower;
                this.pellets[r][c] = isDot;
                this.power[r][c] = isPower;
                if (isDot || isPower) {
                    dots++;
                }
            }
        }
        this.pelletsLeft = dots;
        this.resetActors(0L);
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (key == KEY_R) {
            this.score = 0;
            this.lives = START_LIVES;
            this.level = 1;
            this.loadLevel();
            this.state = State.READY;
            this.stateEnd = -1L;
            return;
        }
        if (this.state != State.PLAYING) {
            return;
        }
        if (isUp(key)) {
            this.pacWantDir = DIR_UP;
        } else if (isRight(key)) {
            this.pacWantDir = DIR_RIGHT;
        } else if (isDown(key)) {
            this.pacWantDir = DIR_DOWN;
        } else if (isLeft(key)) {
            this.pacWantDir = DIR_LEFT;
        }
    }

    // ---------------------------------------------------------------- 绘制

    private void render(long time) {
        this.s.setColors(0, 15);
        this.s.clear();

        this.s.setColors(0, 14);
        this.s.print(1, 0, "PAC-MAN");

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x = MAZE_X + c;
                int y = MAZE_Y + r;
                char cell = this.cells[r][c];
                if (cell == WALL) {
                    this.s.setColors(0, 9);
                    this.s.print(x, y, "#");
                } else if (cell == GATE) {
                    this.s.setColors(0, 8);
                    this.s.print(x, y, "=");
                } else if (this.power[r][c]) {
                    this.s.setColors(0, ((time / 8) % 2 == 0) ? 14 : 15);
                    this.s.print(x, y, "o");
                } else if (this.pellets[r][c]) {
                    this.s.setColors(0, 7);
                    this.s.print(x, y, ".");
                }
            }
        }

        this.renderActors(time);
        this.renderHud(time);
    }

    private void renderActors(long time) {
        for (Ghost g : this.ghosts) {
            int x = MAZE_X + g.col;
            int y = MAZE_Y + g.row;
            if (g.eaten) {
                this.s.setColors(0, 15);
                this.s.print(x, y, "e");
            } else if (g.frightened) {
                boolean flashing = this.frightEnd - time < 40L && (time / 4) % 2 == 0;
                this.s.setColors(0, flashing ? 15 : 9);
                this.s.print(x, y, "m");
            } else {
                this.s.setColors(0, GHOST_COLORS[g.kind]);
                this.s.print(x, y, "M");
            }
        }

        int px = MAZE_X + this.pacCol;
        int py = MAZE_Y + this.pacRow;
        if (this.state == State.DYING) {
            this.s.setColors(0, 12);
            this.s.print(px, py, "X");
            return;
        }
        String glyph = switch (this.pacDir) {
            case DIR_UP -> "^";
            case DIR_RIGHT -> ">";
            case DIR_DOWN -> "v";
            default -> "<";
        };
        this.s.setColors(0, 14);
        this.s.print(px, py, glyph);
    }

    private void renderHud(long time) {
        this.s.setColors(0, 7);
        this.s.print(30, 2, "SCORE");
        this.s.setColors(0, 15);
        this.s.print(30, 3, Integer.toString(this.score));
        this.s.setColors(0, 7);
        this.s.print(30, 5, "LEVEL");
        this.s.setColors(0, 15);
        this.s.print(30, 6, Integer.toString(this.level));
        this.s.setColors(0, 7);
        this.s.print(30, 8, "LIVES");
        this.s.setColors(0, 14);
        this.s.print(30, 9, "C".repeat(Math.max(0, this.lives)));

        String status = switch (this.state) {
            case READY -> "READY!";
            case DYING -> "OUCH!";
            case LEVEL_CLEAR -> "LEVEL CLEAR";
            case GAME_OVER -> "GAME OVER - PRESS R";
            default -> null;
        };
        if (status != null) {
            int color = switch (this.state) {
                case GAME_OVER -> 12;
                case LEVEL_CLEAR -> 14;
                default -> 15;
            };
            int x = Math.max(0, MAZE_X + (COLS - status.length()) / 2);
            this.s.setColors(0, color);
            this.s.print(x, 23, status);
        }

        this.s.setColors(0, 7);
        this.s.print(1, 24, "ARROWS/WASD MOVE   R RESTART");
    }
}
