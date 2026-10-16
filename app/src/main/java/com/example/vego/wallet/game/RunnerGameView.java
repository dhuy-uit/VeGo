package com.example.vego.wallet.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.vego.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Game chạy vượt chướng ngại vật kiểu "khủng long Chrome" — vẽ bằng Canvas, không dùng thư viện ngoài.
 * Hình khủng long, xương rồng, tảng đá, mây, đồi lấy đúng path từ Figma (node 2003:2665).
 *
 * Mọi tọa độ thế giới tính bằng dp rồi nhân mật độ màn hình khi vẽ, nên game chạy giống nhau trên mọi máy.
 * Vòng lặp dùng Choreographer (đồng bộ với khung hình), cập nhật theo thời gian thực nên máy yếu
 * vẫn chạy đúng tốc độ, chỉ ít khung hình hơn.
 */
public class RunnerGameView extends View implements Choreographer.FrameCallback {

    public enum State { READY, RUNNING, PAUSED, OVER }

    public interface Listener {
        /** Lần chạm đầu tiên: bắt đầu chạy (lúc này mới tính lượt chơi). */
        void onGameStarted();
        void onScoreChanged(int score);
        void onGameOver(int score);
    }

    // ---------- Thông số game (đơn vị dp, giây) ----------
    private static final float DINO_SCALE = 0.6f;          // 78×88 trong Figma -> ~47×53dp
    private static final float DINO_X = 28f;
    private static final float GROUND_FROM_BOTTOM = 44f;   // tối thiểu, khung cao thì phần đất dày hơn
    private static final float GRAVITY_UP = 2800f;
    private static final float GRAVITY_DOWN = 3400f;
    private static final float JUMP_VELOCITY = -880f;
    private static final float JUMP_CUT_VELOCITY = -380f; // thả tay sớm -> nhảy thấp
    private static final float START_SPEED = 300f;
    private static final float MAX_SPEED = 720f;
    private static final float ACCELERATION = 10f;         // dp/s mỗi giây
    private static final float DP_PER_POINT = 12f;          // 12dp quãng đường = 1 điểm
    private static final int COIN_BONUS = 30;               // điểm cộng khi ăn xu trên đường
    private static final float COIN_RADIUS = 11f;
    private static final float MAX_DT = 0.05f;

    // Path gốc từ Figma (hộp tọa độ riêng của từng hình)
    private static final String DINO_PATH = "M36 0H78V31.4286H56V41.9048H68V50.2857H56V44H46V67.0476H38V79.619H50V88H30V67.0476H22V83.8095H8V75.4286H16V60.7619H8V52.381H0V23.0476H8V39.8095H16V46.0952H28V37.7143H36V0Z";
    private static final String CACTUS_PATH = "M13 0H21V22H27V10H34V29H21V48H13V34H0V16H7V27H13V0Z";
    private static final String ROCK_PATH = "M0 22V10H8V0H24V8H30V22H0Z";
    private static final String CLOUD_PATH = "M33 1V9.23535H46V19H1V11.5879H13V1H33Z";
    private static final String HILL_PATH = "M0 63V44H57V26H112V44H178V32H224V0H278V32H331V44H372V63H0Z";
    private static final float LEG_TOP = 67.0476f;

    private enum Kind {
        CACTUS(34, 48, 0.8f, true), CACTUS_SMALL(34, 48, 0.6f, true),
        CACTUS_PAIR(34, 48, 0.6f, true), ROCK(30, 22, 1f, false);

        final float w, h, scale;
        final boolean cactus;

        Kind(float w, float h, float scale, boolean cactus) {
            this.w = w; this.h = h; this.scale = scale; this.cactus = cactus;
        }

        float width() { return this == CACTUS_PAIR ? (w * 2 + 6) * scale : w * scale; }
        float height() { return h * scale; }
    }

    private static final class Obstacle {
        Kind kind;
        float x;
    }

    private static final class Coin {
        float x, y, phase;
    }

    private static final class FloatText {
        float x, y, age;
        String text;
    }

    private final float dp;
    private final Random random = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF hit = new RectF();
    private final RectF other = new RectF();

    private final Path dinoPath;
    private final Path cactusPath;
    private final Path rockPath;
    private final Path cloudPath;
    private final Path hillPath;

    private final int colorPrimary, colorCloud, colorHill, colorDash, colorGround, colorCoinBg, colorCoin, colorText, colorTextSub;

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private final List<FloatText> floats = new ArrayList<>();
    private final float[] cloudX = new float[3];
    private final float[] cloudY = new float[3];
    private final float[] dashX = new float[8];
    private final float[] dashY = new float[8];
    private final float[] dashW = new float[8];

    @Nullable private Listener listener;
    private String readyText = "Tap to start";
    private String pausedText = "Paused";
    private String continueText = "Tap to continue";
    private String overText = "Game over";

    private State state = State.READY;
    private boolean looping = false;
    private long lastFrameNanos = 0;

    // Trạng thái thế giới
    private float worldW, worldH, groundY;
    private float speed = START_SPEED;
    private float distance = 0;
    private int bonus = 0;
    private int lastReportedScore = -1;
    private float dinoY = 0;       // độ cao so với mặt đất (âm = trên không)
    private float dinoVy = 0;
    private boolean onGround = true;
    private boolean holding = false;
    private float runClock = 0;
    private float hillOffset = 0;
    private float nextSpawnIn = 0;
    private float idleClock = 0;

    public RunnerGameView(Context context) {
        this(context, null);
    }

    public RunnerGameView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);

        dinoPath = parse(DINO_PATH);
        cactusPath = parse(CACTUS_PATH);
        rockPath = parse(ROCK_PATH);
        cloudPath = parse(CLOUD_PATH);
        hillPath = parse(HILL_PATH);

        colorPrimary = ContextCompat.getColor(context, R.color.vego_primary);
        colorCloud = ContextCompat.getColor(context, R.color.vego_game_cloud);
        colorHill = ContextCompat.getColor(context, R.color.vego_game_hill);
        colorDash = ContextCompat.getColor(context, R.color.vego_game_dash);
        colorGround = ContextCompat.getColor(context, R.color.vego_game_ground);
        colorCoinBg = ContextCompat.getColor(context, R.color.vego_orange_bg);
        colorCoin = ContextCompat.getColor(context, R.color.vego_orange_text);
        colorText = ContextCompat.getColor(context, R.color.vego_text);
        colorTextSub = ContextCompat.getColor(context, R.color.vego_text_secondary);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        if (!isInEditMode()) {
            Typeface bold = ResourcesCompat.getFont(context, R.font.inter_bold);
            if (bold != null) text.setTypeface(bold);
        }
    }

    /** Đọc path SVG gồm M/H/V/L/Z (đúng dạng Figma xuất cho các hình pixel). */
    private static Path parse(String d) {
        Path path = new Path();
        float x = 0, y = 0;
        int i = 0, n = d.length();
        char cmd = 'M';
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
                if (cmd == 'Z' || cmd == 'z') path.close();
                continue;
            }
            if (c == ' ' || c == ',') { i++; continue; }
            int start = i;
            while (i < n && (Character.isDigit(d.charAt(i)) || d.charAt(i) == '.' || (i == start && d.charAt(i) == '-'))) i++;
            float a = Float.parseFloat(d.substring(start, i));
            switch (cmd) {
                case 'H': x = a; path.lineTo(x, y); break;
                case 'V': y = a; path.lineTo(x, y); break;
                case 'M':
                case 'L': {
                    while (i < n && (d.charAt(i) == ' ' || d.charAt(i) == ',')) i++;
                    int s2 = i;
                    while (i < n && (Character.isDigit(d.charAt(i)) || d.charAt(i) == '.' || (i == s2 && d.charAt(i) == '-'))) i++;
                    float b = Float.parseFloat(d.substring(s2, i));
                    x = a; y = b;
                    if (cmd == 'M') { path.moveTo(x, y); cmd = 'L'; } else path.lineTo(x, y);
                    break;
                }
                default: break;
            }
        }
        return path;
    }

    // ---------- API cho Activity ----------

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setTexts(String ready, String paused, String tapToContinue, String over) {
        readyText = ready;
        pausedText = paused;
        continueText = tapToContinue;
        overText = over;
        invalidate();
    }

    public State getState() {
        return state;
    }

    public int getScore() {
        return (int) (distance / DP_PER_POINT) + bonus;
    }

    /** Tạm dừng (vd khi rời app hoặc mở hộp thoại). Chạm vào khung để chơi tiếp. */
    public void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            holding = false;
            stopLoop();
            invalidate();
        }
    }

    /** Chơi tiếp ngay (vd bấm "Chơi tiếp" trong hộp thoại). */
    public void resume() {
        if (state == State.PAUSED) {
            state = State.RUNNING;
            startLoop();
        }
    }

    /** Kết thúc lượt ngay, vẫn tính điểm hiện tại. */
    public void endNow() {
        if (state == State.RUNNING || state == State.PAUSED) gameOver(false);
    }

    // ---------- Vòng đời view ----------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (state == State.READY || state == State.RUNNING) startLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopLoop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        worldW = w / dp;
        worldH = h / dp;
        // Khung thấp: giống Figma (đất 40dp); khung cao: mặt đất ở ~72% để bầu trời không quá trống
        groundY = worldH - Math.max(GROUND_FROM_BOTTOM, worldH * 0.28f);
        if (state == State.READY) resetWorld();
    }

    private void resetWorld() {
        speed = START_SPEED;
        distance = 0;
        bonus = 0;
        dinoY = 0;
        dinoVy = 0;
        onGround = true;
        obstacles.clear();
        coins.clear();
        floats.clear();
        nextSpawnIn = worldW + 260f; // chướng ngại đầu tiên tới sau ~2 giây
        float sky = Math.max(40f, groundY - 150f);
        for (int i = 0; i < cloudX.length; i++) {
            cloudX[i] = worldW * (0.15f + 0.35f * i) + random.nextFloat() * 30f;
            cloudY[i] = 24f + random.nextFloat() * Math.max(10f, sky - 24f);
        }
        for (int i = 0; i < dashX.length; i++) {
            dashX[i] = random.nextFloat() * worldW;
            dashY[i] = randomDashY();
            dashW[i] = 10f + random.nextFloat() * 24f;
        }
        reportScore();
    }

    private void startLoop() {
        if (looping) return;
        looping = true;
        lastFrameNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopLoop() {
        looping = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!looping) return;
        float dt = lastFrameNanos == 0 ? 0f : (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = frameTimeNanos;
        update(Math.min(dt, MAX_DT));
        invalidate();
        if (looping) Choreographer.getInstance().postFrameCallback(this);
    }

    // ---------- Điều khiển ----------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                press();
                return true;
            case MotionEvent.ACTION_UP:
                release();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                release();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void press() {
        switch (state) {
            case READY:
                resetWorld();
                state = State.RUNNING;
                if (listener != null) listener.onGameStarted();
                startLoop();
                jump();
                break;
            case RUNNING:
                jump();
                break;
            case PAUSED:
                resume();
                break;
            default:
                break;
        }
    }

    private void release() {
        holding = false;
        // Thả tay sớm thì cắt bớt lực nhảy -> nhảy thấp, giữ lâu thì nhảy cao
        if (!onGround && dinoVy < JUMP_CUT_VELOCITY) dinoVy = JUMP_CUT_VELOCITY;
    }

    private void jump() {
        holding = true;
        if (onGround) {
            onGround = false;
            dinoVy = JUMP_VELOCITY;
        }
    }

    // ---------- Cập nhật thế giới ----------

    private void update(float dt) {
        if (state == State.READY) {
            idleClock += dt;
            for (int i = 0; i < cloudX.length; i++) cloudX[i] = wrap(cloudX[i] - 8f * dt, 60f);
            return;
        }
        if (state != State.RUNNING) return;

        speed = Math.min(MAX_SPEED, speed + ACCELERATION * dt);
        float dx = speed * dt;
        distance += dx;
        runClock += dt;

        // Khủng long
        if (!onGround) {
            dinoVy += (dinoVy < 0 && holding ? GRAVITY_UP : GRAVITY_DOWN) * dt;
            dinoY += dinoVy * dt;
            if (dinoY >= 0) {
                dinoY = 0;
                dinoVy = 0;
                onGround = true;
            }
        }

        // Nền: mây trôi chậm, đồi chậm vừa, vệt đường theo tốc độ chạy (hiệu ứng chiều sâu)
        for (int i = 0; i < cloudX.length; i++) cloudX[i] = wrap(cloudX[i] - dx * 0.12f, 60f);
        hillOffset = (hillOffset + dx * 0.35f) % 372f;
        for (int i = 0; i < dashX.length; i++) {
            dashX[i] -= dx;
            if (dashX[i] + dashW[i] < 0) {
                dashX[i] = worldW + random.nextFloat() * 60f;
                dashY[i] = randomDashY();
                dashW[i] = 10f + random.nextFloat() * 24f;
            }
        }

        // Chướng ngại vật & xu
        for (Iterator<Obstacle> it = obstacles.iterator(); it.hasNext(); ) {
            Obstacle o = it.next();
            o.x -= dx;
            if (o.x + o.kind.width() < -10) it.remove();
        }
        for (Iterator<Coin> it = coins.iterator(); it.hasNext(); ) {
            Coin c = it.next();
            c.x -= dx;
            c.phase += dt * 5f;
            if (c.x < -20) it.remove();
        }
        for (Iterator<FloatText> it = floats.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.age += dt;
            f.y -= 40f * dt;
            if (f.age > 0.8f) it.remove();
        }
        nextSpawnIn -= dx;
        if (nextSpawnIn <= 0) spawn();

        checkCoins();
        if (checkCollision()) {
            gameOver(true);
            return;
        }
        reportScore();
    }

    private float randomDashY() {
        float band = Math.max(14f, worldH - groundY - 24f);
        return groundY + 12f + random.nextFloat() * band;
    }

    private float wrap(float x, float width) {
        return x < -width ? worldW + random.nextFloat() * 80f : x;
    }

    private void spawn() {
        Obstacle o = new Obstacle();
        float roll = random.nextFloat();
        // Càng chạy nhanh càng hay gặp cụm xương rồng đôi
        if (roll < 0.30f) o.kind = Kind.CACTUS;
        else if (roll < 0.55f) o.kind = Kind.CACTUS_SMALL;
        else if (roll < 0.75f + Math.min(0.1f, (speed - START_SPEED) / 4000f)) o.kind = Kind.CACTUS_PAIR;
        else o.kind = Kind.ROCK;
        o.x = worldW + 10f;
        obstacles.add(o);

        // Xu: lơ lửng trên chướng ngại vật (phải nhảy mới ăn được) hoặc nằm thấp giữa hai chướng ngại
        float c = random.nextFloat();
        if (c < 0.45f) {
            addCoin(o.x + o.kind.width() / 2f, groundY - o.kind.height() - 52f);
        } else if (c < 0.70f) {
            float gapX = o.x + o.kind.width() + 90f;
            for (int i = 0; i < 3; i++) addCoin(gapX + i * 30f, groundY - 20f);
        }

        float minGap = 220f + speed * 0.55f;
        nextSpawnIn = o.kind.width() + minGap + random.nextFloat() * (160f + speed * 0.6f);
    }

    private void addCoin(float x, float y) {
        Coin coin = new Coin();
        coin.x = x;
        coin.y = y;
        coin.phase = random.nextFloat() * 6f;
        coins.add(coin);
    }

    /** Hộp va chạm của khủng long (nhỏ hơn hình vẽ một chút để chơi "dễ chịu"). */
    private boolean dinoHits(RectF target) {
        float s = DINO_SCALE;
        float top = groundY - 88f * s + dinoY;
        // đầu
        hit.set(DINO_X + 38 * s, top + 3 * s, DINO_X + 75 * s, top + 29 * s);
        if (RectF.intersects(hit, target)) return true;
        // thân
        hit.set(DINO_X + 10 * s, top + 26 * s, DINO_X + 52 * s, top + 64 * s);
        if (RectF.intersects(hit, target)) return true;
        // chân
        hit.set(DINO_X + 18 * s, top + 64 * s, DINO_X + 44 * s, top + 86 * s);
        return RectF.intersects(hit, target);
    }

    private boolean checkCollision() {
        for (Obstacle o : obstacles) {
            float w = o.kind.width(), h = o.kind.height();
            other.set(o.x + 3f, groundY - h + 3f, o.x + w - 3f, groundY);
            if (dinoHits(other)) return true;
        }
        return false;
    }

    private void checkCoins() {
        for (Iterator<Coin> it = coins.iterator(); it.hasNext(); ) {
            Coin c = it.next();
            float cy = c.y + (float) Math.sin(c.phase) * 3f;
            other.set(c.x - COIN_RADIUS, cy - COIN_RADIUS, c.x + COIN_RADIUS, cy + COIN_RADIUS);
            if (dinoHits(other)) {
                it.remove();
                bonus += COIN_BONUS;
                FloatText f = new FloatText();
                f.x = c.x;
                f.y = cy - 14f;
                f.text = "+" + COIN_BONUS;
                floats.add(f);
            }
        }
    }

    private void reportScore() {
        int score = getScore();
        if (score != lastReportedScore) {
            lastReportedScore = score;
            if (listener != null) listener.onScoreChanged(score);
        }
    }

    private void gameOver(boolean crashed) {
        state = State.OVER;
        holding = false;
        stopLoop();
        if (crashed) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        reportScore();
        invalidate();
        if (listener != null) listener.onGameOver(getScore());
    }

    // ---------- Vẽ ----------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (worldW <= 0) return;
        canvas.save();
        canvas.scale(dp, dp);

        drawClouds(canvas);
        drawHills(canvas);
        drawGround(canvas);
        for (Coin c : coins) drawCoin(canvas, c.x, c.y + (float) Math.sin(c.phase) * 3f);
        for (Obstacle o : obstacles) drawObstacle(canvas, o);
        drawDino(canvas);
        drawFloats(canvas);
        drawOverlay(canvas);

        canvas.restore();
    }

    private void drawClouds(Canvas canvas) {
        stroke.setColor(colorCloud);
        stroke.setStrokeWidth(2f);
        for (int i = 0; i < cloudX.length; i++) {
            canvas.save();
            canvas.translate(cloudX[i], cloudY[i]);
            canvas.drawPath(cloudPath, stroke);
            canvas.restore();
        }
    }

    private void drawHills(Canvas canvas) {
        fill.setColor(colorHill);
        float top = groundY - 62f;
        for (float x = -hillOffset; x < worldW; x += 372f) {
            canvas.save();
            canvas.translate(x, top);
            canvas.drawPath(hillPath, fill);
            canvas.restore();
        }
    }

    private void drawGround(Canvas canvas) {
        fill.setColor(colorGround);
        canvas.drawRect(0, groundY, worldW, worldH, fill);
        fill.setColor(colorPrimary);
        canvas.drawRect(0, groundY, worldW, groundY + 2f, fill);
        fill.setColor(colorDash);
        for (int i = 0; i < dashX.length; i++) {
            canvas.drawRect(dashX[i], dashY[i], dashX[i] + dashW[i], dashY[i] + 2f, fill);
        }
    }

    private void drawObstacle(Canvas canvas, Obstacle o) {
        Kind k = o.kind;
        fill.setColor(k.cactus ? colorPrimary : colorDash);
        Path path = k.cactus ? cactusPath : rockPath;
        canvas.save();
        canvas.translate(o.x, groundY - k.height() + 1f);
        canvas.scale(k.scale, k.scale);
        canvas.drawPath(path, fill);
        if (k == Kind.CACTUS_PAIR) {
            canvas.translate(k.w + 6, 0);
            canvas.drawPath(path, fill);
        }
        canvas.restore();
    }

    private void drawCoin(Canvas canvas, float cx, float cy) {
        fill.setColor(colorCoinBg);
        canvas.drawCircle(cx, cy, COIN_RADIUS, fill);
        stroke.setColor(colorCoin);
        stroke.setStrokeWidth(1.5f);
        canvas.drawCircle(cx, cy, COIN_RADIUS - 0.75f, stroke);
        text.setColor(colorCoin);
        text.setTextSize(10f);
        canvas.drawText("V", cx, cy + 3.6f, text);
    }

    private void drawDino(Canvas canvas) {
        float s = DINO_SCALE;
        canvas.save();
        canvas.translate(DINO_X, groundY - 88f * s + dinoY + 1f);
        canvas.scale(s, s);
        fill.setColor(colorPrimary);

        boolean running = state == State.RUNNING && onGround;
        if (!running) {
            // Đứng chờ / đang nhảy / va chạm: dùng nguyên dáng nhảy trong Figma
            canvas.drawPath(dinoPath, fill);
        } else {
            // Chạy: thân giữ nguyên, hai chân đổi bước liên tục
            canvas.save();
            canvas.clipRect(0, 0, 78, LEG_TOP);
            canvas.drawPath(dinoPath, fill);
            canvas.restore();
            boolean stepA = ((int) (runClock * 12f)) % 2 == 0;
            if (stepA) {
                canvas.drawRect(30, LEG_TOP, 38, 88, fill);   // chân trước chạm đất
                canvas.drawRect(38, 80, 46, 88, fill);
                canvas.drawRect(16, LEG_TOP, 22, 76, fill);   // chân sau co lên
                canvas.drawRect(22, 70, 28, 76, fill);
            } else {
                canvas.drawRect(30, LEG_TOP, 38, 78, fill);
                canvas.drawRect(38, 72, 46, 78, fill);
                canvas.drawRect(16, LEG_TOP, 22, 88, fill);
                canvas.drawRect(22, 80, 28, 88, fill);
            }
        }

        // Mắt (chớp mắt khi đứng chờ, mắt "hoảng" khi va chạm)
        fill.setColor(0xFFFFFFFF);
        if (state == State.OVER) {
            canvas.drawRect(55, 5, 65, 15, fill);
            fill.setColor(colorPrimary);
            canvas.drawRect(58, 8, 62, 12, fill);
        } else if (state == State.READY && (idleClock % 3f) > 2.85f) {
            canvas.drawRect(57, 10, 63, 11.5f, fill);
        } else {
            canvas.drawRect(57, 7, 63, 13, fill);
        }
        canvas.restore();
    }

    private void drawFloats(Canvas canvas) {
        text.setTextSize(13f);
        for (FloatText f : floats) {
            int alpha = (int) (255 * Math.max(0f, 1f - f.age / 0.8f));
            text.setColor((colorCoin & 0x00FFFFFF) | (alpha << 24));
            canvas.drawText(f.text, f.x, f.y, text);
        }
    }

    private void drawOverlay(Canvas canvas) {
        String title = null;
        String sub = null;
        if (state == State.READY) {
            title = readyText;
        } else if (state == State.PAUSED) {
            title = pausedText;
            sub = continueText;
        } else if (state == State.OVER) {
            title = overText;
        }
        if (title == null) return;

        float cy = Math.max(48f, (groundY - 88f * DINO_SCALE) / 2f + 8f);
        if (state == State.READY) cy += (float) Math.sin(idleClock * 3f) * 3f;
        text.setColor(colorText);
        text.setTextSize(18f);
        canvas.drawText(title, worldW / 2f, cy, text);
        if (sub != null) {
            text.setColor(colorTextSub);
            text.setTextSize(13f);
            canvas.drawText(sub, worldW / 2f, cy + 22f, text);
        }
    }
}
