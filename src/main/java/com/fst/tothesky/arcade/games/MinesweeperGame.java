package com.fst.tothesky.arcade.games;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

import com.mrh0.buildersaddition.arcade.ArcadeGame;
import com.mrh0.buildersaddition.arcade.ArcadeScreen;
import com.mrh0.buildersaddition.tileentity.ArcadeTileEntity;

import net.minecraft.client.Minecraft;

/**
 * Minesweeper for the Builders Crafts & Additions arcade cabinet.
 *
 * <p>The board is 16x16 cells with 40 mines. It occupies the character cells
 * (4,4)..(19,19) of the 40x25 arcade screen; the info panel starts at column 22,
 * the title sits on row 0 and a one line control reminder on row 24.</p>
 *
 * <p>Mines are placed only after the first dig, and the first dug cell plus its
 * 3x3 neighbourhood are guaranteed to be safe. Empty areas are expanded with an
 * iterative queue (no recursion). Controls: arrows/WASD move the cursor,
 * space/enter digs, F toggles a flag, R restarts the game.</p>
 */
public final class MinesweeperGame extends ArcadeGame {

    private static final int BOARD_SIZE = 16;
    private static final int BOARD_LEFT = 4;
    private static final int BOARD_TOP = 4;
    private static final int MINE_COUNT = 40;
    private static final int INFO_LEFT = 22;
    private static final int TITLE_ROW = 0;
    private static final int HELP_ROW = 24;
    private static final int TICKS_PER_SECOND = 20;

    private static final int KEY_F = 70;
    private static final int KEY_R = 82;

    private static final int COLOR_BLACK = 0;
    private static final int COLOR_DARK_BLUE = 1;
    private static final int COLOR_DARK_GREEN = 2;
    private static final int COLOR_DARK_RED = 4;
    private static final int COLOR_LIGHT_GRAY = 7;
    private static final int COLOR_DARK_GRAY = 8;
    private static final int COLOR_BLUE = 9;
    private static final int COLOR_GREEN = 10;
    private static final int COLOR_CYAN = 11;
    private static final int COLOR_RED = 12;
    private static final int COLOR_YELLOW = 14;
    private static final int COLOR_WHITE = 15;

    /** Foreground colors of the numbers 1..8, drawn on a light gray background. */
    private static final int[] NUMBER_COLORS = {
            COLOR_BLUE, COLOR_GREEN, COLOR_RED, COLOR_DARK_BLUE,
            COLOR_DARK_RED, COLOR_CYAN, COLOR_BLACK, COLOR_DARK_GRAY
    };

    private static final int CELL_COUNT = BOARD_SIZE * BOARD_SIZE;
    private static final int SAFE_CELL_COUNT = CELL_COUNT - MINE_COUNT;

    private final boolean[] mines = new boolean[CELL_COUNT];
    private final int[] nearby = new int[CELL_COUNT];
    private final boolean[] revealed = new boolean[CELL_COUNT];
    private final boolean[] flagged = new boolean[CELL_COUNT];
    private final Random random = new Random();

    private int cursorX = BOARD_SIZE / 2;
    private int cursorY = BOARD_SIZE / 2;
    private boolean minesPlaced;
    private boolean dead;
    private boolean won;
    private int revealedCount;
    private int flagCount;
    private long startTick = -1L;
    private long currentTick;
    private int elapsedSeconds;

    public MinesweeperGame(ArcadeScreen s, ArcadeTileEntity te) {
        super(s, te);
    }

    @Override
    public void start() {
        super.start();
        reset();
    }

    @Override
    public void frame(long time) {
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        currentTick = time;
        if (!minesPlaced || dead || won) {
            return;
        }
        if (startTick < 0L) {
            startTick = time;
        }
        int seconds = secondsElapsed();
        if (seconds != elapsedSeconds) {
            elapsedSeconds = seconds;
            drawInfo();
        }
    }

    @Override
    public void onKeyPressed(int key) {
        super.onKeyPressed(key);
        if (key == KEY_R) {
            reset();
            return;
        }
        if (dead || won) {
            return;
        }
        if (isUp(key)) {
            moveCursor(0, -1);
        } else if (isDown(key)) {
            moveCursor(0, 1);
        } else if (isLeft(key)) {
            moveCursor(-1, 0);
        } else if (isRight(key)) {
            moveCursor(1, 0);
        } else if (isSpace(key) || isEnter(key)) {
            dig();
        } else if (key == KEY_F) {
            toggleFlag();
        } else {
            return;
        }
        drawBoard();
        drawInfo();
        drawStatus();
    }

    private void reset() {
        Arrays.fill(mines, false);
        Arrays.fill(nearby, 0);
        Arrays.fill(revealed, false);
        Arrays.fill(flagged, false);
        cursorX = BOARD_SIZE / 2;
        cursorY = BOARD_SIZE / 2;
        minesPlaced = false;
        dead = false;
        won = false;
        revealedCount = 0;
        flagCount = 0;
        startTick = -1L;
        elapsedSeconds = 0;
        renderAll();
    }

    private void moveCursor(int dx, int dy) {
        cursorX = clamp(cursorX + dx, 0, BOARD_SIZE - 1);
        cursorY = clamp(cursorY + dy, 0, BOARD_SIZE - 1);
    }

    private void toggleFlag() {
        int index = cursorY * BOARD_SIZE + cursorX;
        if (revealed[index]) {
            return;
        }
        flagged[index] = !flagged[index];
        flagCount += flagged[index] ? 1 : -1;
    }

    private void dig() {
        int index = cursorY * BOARD_SIZE + cursorX;
        if (revealed[index] || flagged[index]) {
            return;
        }
        if (!minesPlaced) {
            placeMines(cursorX, cursorY);
        }
        if (mines[index]) {
            dead = true;
            elapsedSeconds = secondsElapsed();
            return;
        }
        expandFrom(index);
        if (revealedCount == SAFE_CELL_COUNT) {
            won = true;
            elapsedSeconds = secondsElapsed();
        }
    }

    /**
     * Places the mines so that the 3x3 area around the first dug cell stays empty.
     */
    private void placeMines(int safeX, int safeY) {
        minesPlaced = true;
        int[] candidates = new int[CELL_COUNT];
        int count = 0;
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (Math.abs(x - safeX) <= 1 && Math.abs(y - safeY) <= 1) {
                    continue;
                }
                candidates[count++] = y * BOARD_SIZE + x;
            }
        }
        for (int i = 0; i < MINE_COUNT; i++) {
            int pick = i + random.nextInt(count - i);
            int swap = candidates[i];
            candidates[i] = candidates[pick];
            candidates[pick] = swap;
            mines[candidates[i]] = true;
        }
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                int index = y * BOARD_SIZE + x;
                nearby[index] = mines[index] ? 0 : countNearbyMines(x, y);
            }
        }
    }

    private int countNearbyMines(int x, int y) {
        int total = 0;
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if (offsetX == 0 && offsetY == 0) {
                    continue;
                }
                int nextX = x + offsetX;
                int nextY = y + offsetY;
                if (nextX < 0 || nextY < 0 || nextX >= BOARD_SIZE || nextY >= BOARD_SIZE) {
                    continue;
                }
                if (mines[nextY * BOARD_SIZE + nextX]) {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * Iterative flood fill: opens the start cell, and if it has no neighbouring
     * mine it pushes its neighbours onto the queue instead of recursing.
     */
    private void expandFrom(int start) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.addLast(start);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if (revealed[index] || flagged[index] || mines[index]) {
                continue;
            }
            revealed[index] = true;
            revealedCount++;
            if (nearby[index] != 0) {
                continue;
            }
            int x = index % BOARD_SIZE;
            int y = index / BOARD_SIZE;
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    if (offsetX == 0 && offsetY == 0) {
                        continue;
                    }
                    int nextX = x + offsetX;
                    int nextY = y + offsetY;
                    if (nextX < 0 || nextY < 0 || nextX >= BOARD_SIZE || nextY >= BOARD_SIZE) {
                        continue;
                    }
                    int next = nextY * BOARD_SIZE + nextX;
                    if (!revealed[next] && !flagged[next] && !mines[next]) {
                        queue.addLast(next);
                    }
                }
            }
        }
    }

    private int secondsElapsed() {
        if (startTick < 0L || currentTick <= startTick) {
            return 0;
        }
        return (int) ((currentTick - startTick) / TICKS_PER_SECOND);
    }

    private void renderAll() {
        s.setColors(COLOR_BLACK, COLOR_BLACK);
        s.clear();
        s.setColors(COLOR_BLACK, COLOR_YELLOW);
        s.print((s.width - "MINESWEEPER".length()) / 2, TITLE_ROW, "MINESWEEPER");
        drawBoard();
        drawInfo();
        drawStatus();
        s.setColors(COLOR_BLACK, COLOR_LIGHT_GRAY);
        s.print(0, HELP_ROW, "ARROWS MOVE  SPACE DIG  F FLAG  R RESET");
    }

    private void drawBoard() {
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                drawCell(x, y);
            }
        }
    }

    private void drawCell(int x, int y) {
        int index = y * BOARD_SIZE + x;
        int background = COLOR_DARK_GRAY;
        int foreground = COLOR_WHITE;
        char glyph = ' ';
        if ((dead || won) && mines[index]) {
            background = COLOR_BLACK;
            foreground = COLOR_RED;
            glyph = '*';
        } else if (flagged[index]) {
            background = COLOR_DARK_GRAY;
            foreground = COLOR_RED;
            glyph = 'F';
        } else if (revealed[index]) {
            background = COLOR_LIGHT_GRAY;
            int count = nearby[index];
            if (count > 0) {
                foreground = NUMBER_COLORS[count - 1];
                glyph = (char) ('0' + count);
            }
        }
        if (!dead && !won && x == cursorX && y == cursorY) {
            background = COLOR_YELLOW;
        }
        s.screen[s.getIndex(BOARD_LEFT + x, BOARD_TOP + y)] =
                ArcadeScreen.encode(glyph, background, foreground);
    }

    private void drawInfo() {
        s.setColors(COLOR_BLACK, COLOR_LIGHT_GRAY);
        s.print(INFO_LEFT, 4, "MINES");
        s.setColors(COLOR_BLACK, COLOR_YELLOW);
        s.print(INFO_LEFT, 5, padRight(Integer.toString(MINE_COUNT - flagCount), 5));
        s.setColors(COLOR_BLACK, COLOR_LIGHT_GRAY);
        s.print(INFO_LEFT, 7, "TIME");
        s.setColors(COLOR_BLACK, COLOR_YELLOW);
        s.print(INFO_LEFT, 8, padRight(Integer.toString(elapsedSeconds) + "s", 5));
        s.setColors(COLOR_BLACK, COLOR_LIGHT_GRAY);
        s.print(INFO_LEFT, 10, "CONTROLS");
        s.setColors(COLOR_BLACK, COLOR_WHITE);
        s.print(INFO_LEFT, 11, "ARROWS/WASD");
        s.print(INFO_LEFT, 12, "SPACE DIG");
        s.print(INFO_LEFT, 13, "F FLAG");
        s.print(INFO_LEFT, 14, "R RESET");
    }

    private void drawStatus() {
        if (dead) {
            printCentered(21, "BOOM! GAME OVER", COLOR_DARK_RED, COLOR_WHITE);
            printCentered(22, "Press R to restart", COLOR_BLACK, COLOR_RED);
        } else if (won) {
            printCentered(21, "YOU WIN", COLOR_DARK_GREEN, COLOR_WHITE);
            printCentered(22, "TIME " + elapsedSeconds + "s", COLOR_BLACK, COLOR_YELLOW);
        }
    }

    private void printCentered(int row, String text, int background, int foreground) {
        int column = BOARD_LEFT + (BOARD_SIZE - text.length()) / 2;
        s.setColors(background, foreground);
        s.print(column, row, text);
    }

    private static String padRight(String text, int width) {
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
