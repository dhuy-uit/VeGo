package com.example.vego.booking;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.CoinWallet;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.tickets.ETicketActivity;

/** Figma: "Đặt vé · Thanh toán thành công". */
public class PaymentSuccessActivity extends BaseActivity {

    @Override
    protected Transition transition() {
        return Transition.RESULT;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_payment_success);

        BookingDraft draft = BookingDraft.get();
        if (draft.trip == null) {
            goHome();
            return;
        }
        Trip trip = draft.trip;
        String code = draft.lastTicketCode != null ? draft.lastTicketCode : draft.issueTicketCode();
        // Số tiền thật đã trả (sau mã giảm giá) lấy từ server
        int paid = draft.total();
        // Server đã lưu vé, cộng xu (1.000đ = 1 xu) và gửi thông báo; ở đây chỉ báo cho người dùng
        if (savedInstanceState == null) {
            int coins = paid / 1000;
            if (coins > 0) {
                VeGoBanner.post(getString(R.string.wallet_booking_reward, coins), VeGoBanner.Type.SUCCESS);
            }
            // Vé của tôi / Ví xu / Thông báo sẽ tải lại bản mới từ server khi mở
            CoinWallet.get().setBalance(CoinWallet.get().balance() + coins);
        }

        ((TextView) findViewById(R.id.tv_success_badge)).setText(getString(R.string.success_badge, draft.seat));
        ((InfoRowView) findViewById(R.id.row_ticket_code)).setValue(code);
        ((InfoRowView) findViewById(R.id.row_passenger)).setValue(draft.passengerName);
        ((InfoRowView) findViewById(R.id.row_payment)).setValue(
                draft.paymentMethod.label + " · " + Format.money(paid));
        TripCardBinder.bindWithSeat(findViewById(R.id.card_trip), trip, draft.date, draft.seat);
        // Đã thanh toán: ghế chuyển sang "đã đặt", không còn đếm ngược giữ chỗ
        draft.releaseHold();

        ResultAnimations.popIn(findViewById(R.id.iv_result));

        // Sau khi đặt xong, quay lại sẽ về Trang chủ thay vì quay về màn Thanh toán
        setupBackButton(this::goHome);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goHome();
            }
        });
        onClick(R.id.btn_go_home, v -> goHome());
        onClick(R.id.btn_view_ticket, v -> startActivity(ETicketActivity.intent(this, code)));
    }
}
