package com.example.vego.common;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;

/**
 * Đếm ngược thời gian giữ ghế trên thanh view_hold_timer.
 * Gọi start() trong onResume, stop() trong onPause; khi hết giờ gọi onExpired.
 */
public class HoldTimer {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView display;
    private final Runnable onExpired;
    private Runnable onOneMinuteLeft;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long remaining = BookingDraft.get().holdRemainingMs();
            display.setText(Format.countdown(remaining));
            BookingDraft draft = BookingDraft.get();
            if (remaining > 0 && remaining <= 60_000 && !draft.warnedOneMinute && onOneMinuteLeft != null) {
                draft.warnedOneMinute = true;
                onOneMinuteLeft.run();
            }
            if (remaining <= 0) {
                running = false;
                onExpired.run();
                return;
            }
            // Cập nhật mỗi khi qua một giây tròn
            handler.postDelayed(this, remaining % 1000 + 1);
        }
    };

    public HoldTimer(TextView display, Runnable onExpired) {
        this.display = display;
        this.onExpired = onExpired;
    }

    /** Báo một lần khi còn 1 phút (dùng để hiện thanh cảnh báo). */
    public HoldTimer onOneMinuteLeft(Runnable action) {
        this.onOneMinuteLeft = action;
        return this;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }
}
