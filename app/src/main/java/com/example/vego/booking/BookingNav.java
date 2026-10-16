package com.example.vego.booking;

import android.app.Activity;
import android.content.Intent;

import com.example.vego.R;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.BookingDraft;

/** Các bước điều hướng dùng chung trong luồng Đặt vé. */
final class BookingNav {

    private BookingNav() { }

    /** Về Danh sách chuyến, đóng các màn phía trên nó (Chi tiết, Chọn ghế, ...). */
    static void backToTripList(Activity from) {
        Intent intent = new Intent(from, TripListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
        from.finish();
    }

    /** Hỏi lại trước khi hủy giữ chỗ; đồng ý thì giải phóng ghế rồi chạy afterRelease. */
    static void confirmCancelHold(Activity from, Runnable afterRelease) {
        BookingDraft draft = BookingDraft.get();
        VeGoDialog.with(from)
                .type(VeGoDialog.Type.WARNING)
                .title(R.string.cancel_hold_title)
                .message(from.getString(R.string.cancel_hold_message, draft.seat))
                .primary(R.string.confirm_cancel_hold, () -> {
                    draft.releaseHold();
                    VeGoBanner.post(from.getString(R.string.hold_released), VeGoBanner.Type.INFO);
                    afterRelease.run();
                })
                .secondary(R.string.cancel_hold_keep, null)
                .show();
    }

    /** Thanh cảnh báo khi chỉ còn 1 phút giữ ghế. */
    static void warnOneMinute(Activity from) {
        VeGoBanner.show(from, from.getString(R.string.hold_one_minute, BookingDraft.get().seat),
                VeGoBanner.Type.WARNING);
    }

    /** Hết thời gian giữ ghế: giải phóng ghế và mở màn thông báo. */
    static void holdExpired(Activity from) {
        BookingDraft.get().releaseHold();
        from.startActivity(new Intent(from, HoldExpiredActivity.class));
    }
}
