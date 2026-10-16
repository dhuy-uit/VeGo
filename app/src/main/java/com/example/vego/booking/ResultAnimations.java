package com.example.vego.booking;

import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Hiệu ứng nhỏ cho biểu tượng ở các màn kết quả. */
final class ResultAnimations {

    private ResultAnimations() { }

    /** Biểu tượng "nảy" lên từ nhỏ đến đúng kích thước. */
    static void popIn(View icon) {
        icon.setScaleX(0.4f);
        icon.setScaleY(0.4f);
        icon.setAlpha(0f);
        icon.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(150)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();
    }
}
