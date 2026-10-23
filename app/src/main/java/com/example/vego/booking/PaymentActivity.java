package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.HoldTimer;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.BookingDraft.PaymentMethod;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripRepository;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.List;

/**
 * Figma: "Đặt vé · Thanh toán".
 * Áp mã giảm giá (server kiểm tra và tính tiền), tạo giao dịch MoMo / VNPay rồi mở trang của cổng;
 * kết quả lấy từ server (đã được cổng xác nhận bằng chữ ký), không tin kết quả hiển thị trên trang.
 */
public class PaymentActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    private HoldTimer holdTimer;
    private EditText etVoucher;

    private final ActivityResultLauncher<android.content.Intent> gateway =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> checkResult(true));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_payment);

        if (draft.trip == null) draft.selectTrip(TripRepository.suggested());
        Trip trip = draft.trip;
        draft.startHoldIfNeeded();

        ((TextView) findViewById(R.id.tv_order_title)).setText(trip.operator + " · Ghế " + draft.seat);
        ((TextView) findViewById(R.id.tv_order_route)).setText(trip.origin + " → " + trip.destination);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(trip.departTime() + " · " + Format.date(draft.date));
        ((InfoRowView) findViewById(R.id.row_fare)).setValue(Format.money(draft.fare()));
        ((TextView) findViewById(R.id.tv_hold_note)).setText(getString(R.string.payment_hold_note, draft.seat));

        onClick(R.id.option_momo, v -> selectMethod(PaymentMethod.MOMO));
        onClick(R.id.option_vnpay, v -> selectMethod(PaymentMethod.VNPAY));
        onClick(R.id.option_card, v -> selectMethod(PaymentMethod.CARD));

        etVoucher = findViewById(R.id.et_voucher);
        onClick(R.id.btn_apply_voucher, v -> applyVoucher(textOf(etVoucher)));
        etVoucher.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyVoucher(textOf(etVoucher));
                return true;
            }
            return false;
        });
        onClick(R.id.btn_remove_voucher, v -> removeVoucher());
        loadVoucherSuggestions();

        holdTimer = new HoldTimer(findViewById(R.id.tv_hold_time), () -> BookingNav.holdExpired(this))
                .onOneMinuteLeft(() -> BookingNav.warnOneMinute(this));
        setupBackButton(this::finish);
        onClick(R.id.btn_pay, v -> pay());

        bindTotals();
        selectMethod(draft.paymentMethod);
    }

    @Override
    protected void onResume() {
        super.onResume();
        holdTimer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        holdTimer.stop();
    }

    // ---------- Phương thức ----------

    private void selectMethod(PaymentMethod method) {
        draft.paymentMethod = method;
        styleOption(R.id.option_momo, R.id.radio_momo, method == PaymentMethod.MOMO);
        styleOption(R.id.option_vnpay, R.id.radio_vnpay, method == PaymentMethod.VNPAY);
        styleOption(R.id.option_card, R.id.radio_card, method == PaymentMethod.CARD);

        int body;
        switch (method) {
            case VNPAY: body = R.string.payment_info_vnpay; break;
            case CARD: body = R.string.payment_info_card; break;
            default: body = R.string.payment_info_momo; break;
        }
        ((TextView) findViewById(R.id.tv_method_info_title))
                .setText(getString(R.string.payment_info_title, method.label));
        ((TextView) findViewById(R.id.tv_method_info_body)).setText(body);
        bindPayButton();
    }

    private void styleOption(int optionId, int radioId, boolean selected) {
        View option = findViewById(optionId);
        option.setSelected(selected);
        ((ImageView) findViewById(radioId))
                .setImageResource(selected ? R.drawable.ic_circle_check : R.drawable.ic_circle);
        // Vòng tròn chưa chọn màu xanh như Figma
        ((ImageView) findViewById(radioId)).setColorFilter(getColor(R.color.vego_primary));
    }

    private void bindPayButton() {
        ((TextView) findViewById(R.id.btn_pay))
                .setText(getString(R.string.payment_pay, draft.paymentMethod.label, Format.money(draft.total())));
    }

    private void bindTotals() {
        InfoRowView rowDiscount = findViewById(R.id.row_discount);
        rowDiscount.setVisibility(draft.discount > 0 ? View.VISIBLE : View.GONE);
        rowDiscount.setValue("-" + Format.money(draft.discount));
        ((InfoRowView) findViewById(R.id.row_total)).setValue(Format.money(draft.total()));

        boolean applied = draft.voucherCode != null;
        findViewById(R.id.voucher_input).setVisibility(applied ? View.GONE : View.VISIBLE);
        findViewById(R.id.voucher_applied).setVisibility(applied ? View.VISIBLE : View.GONE);
        if (applied) {
            ((TextView) findViewById(R.id.tv_voucher_applied)).setText(getString(R.string.voucher_applied,
                    draft.voucherCode, Format.money(draft.discount)));
        }
        View suggestions = findViewById(R.id.voucher_suggestions);
        if (applied) suggestions.setVisibility(View.GONE);
        else if (((LinearLayout) suggestions).getChildCount() > 0) suggestions.setVisibility(View.VISIBLE);
        bindPayButton();
    }

    // ---------- Mã giảm giá ----------

    private void loadVoucherSuggestions() {
        ApiClient.api().vouchers().enqueue(new ApiCallback<List<ApiModels.Voucher>>(this) {
            @Override
            protected void onSuccess(@Nullable List<ApiModels.Voucher> list) {
                LinearLayout box = findViewById(R.id.voucher_suggestions);
                box.removeAllViews();
                if (list == null) return;
                LayoutInflater inflater = LayoutInflater.from(PaymentActivity.this);
                for (ApiModels.Voucher v : list) {
                    View item = inflater.inflate(R.layout.item_voucher_suggestion, box, false);
                    ((TextView) item.findViewById(R.id.tv_voucher_code)).setText(v.code);
                    String desc = v.title;
                    if (v.origin != null) desc += " · " + v.origin + " – " + v.destination;
                    ((TextView) item.findViewById(R.id.tv_voucher_desc)).setText(desc);
                    item.setOnClickListener(x -> {
                        etVoucher.setText(v.code);
                        applyVoucher(v.code);
                    });
                    box.addView(item);
                }
                bindTotals();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                // Gợi ý không bắt buộc: lỗi thì vẫn nhập mã tay được
            }
        });
    }

    private void applyVoucher(String code) {
        if (code.isEmpty()) {
            etVoucher.setError(getString(R.string.voucher_empty));
            etVoucher.requestFocus();
            return;
        }
        if (!requireServerBooking()) return;
        setBusy(R.id.btn_apply_voucher, true);
        ApiClient.api().applyVoucher(draft.bookingCode, new ApiModels.CodeRequest(code))
                .enqueue(new ApiCallback<ApiModels.Booking>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.Booking b) {
                        if (b == null) return;
                        draft.applyServerBooking(b);
                        etVoucher.setText("");
                        bindTotals();
                        VeGoBanner.show(PaymentActivity.this, getString(R.string.voucher_applied_banner,
                                b.voucherCode, Format.money(b.discount)), VeGoBanner.Type.SUCCESS);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!handleBookingGone(error)) {
                            etVoucher.setError(error.message);
                            etVoucher.requestFocus();
                        }
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_apply_voucher, false);
                    }
                });
    }

    private void removeVoucher() {
        if (!requireServerBooking()) return;
        ApiClient.api().removeVoucher(draft.bookingCode).enqueue(new ApiCallback<ApiModels.Booking>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Booking b) {
                if (b == null) return;
                draft.applyServerBooking(b);
                bindTotals();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                if (!handleBookingGone(error)) showError(error);
            }
        });
    }

    // ---------- Thanh toán ----------

    private void pay() {
        if (!requireServerBooking()) return;
        setBusy(R.id.btn_pay, true);
        ApiClient.api().createPayment(draft.bookingCode, new ApiModels.CreatePaymentRequest(draft.paymentMethod.name()))
                .enqueue(new ApiCallback<ApiModels.Payment>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.Payment p) {
                        if (p == null) return;
                        gateway.launch(PaymentWebActivity.intent(PaymentActivity.this, p.payUrl, p.resultUrl,
                                getString(R.string.payment_web_title, draft.paymentMethod.label)));
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        if (!handleBookingGone(error)) showError(error);
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_pay, false);
                        bindPayButton();
                    }
                });
    }

    /** Sau khi đóng trang của cổng: hỏi server kết quả thật của đơn. */
    private void checkResult(boolean fromGateway) {
        if (draft.bookingCode == null) return;
        setBusy(R.id.btn_pay, true);
        ApiClient.api().getBooking(draft.bookingCode).enqueue(new ApiCallback<ApiModels.Booking>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Booking b) {
                if (b == null) return;
                if (b.is("PAID")) {
                    holdTimer.stop();
                    draft.applyServerBooking(b);
                    draft.issueTicketCode();
                    open(PaymentSuccessActivity.class);
                    finish();
                    return;
                }
                if (!b.is("HOLDING")) {
                    BookingNav.holdExpired(PaymentActivity.this);
                    return;
                }
                draft.applyServerBooking(b);
                bindTotals();
                boolean failed = b.lastPayment != null && "FAILED".equals(b.lastPayment.status);
                if (failed) {
                    open(PaymentFailedActivity.class);
                } else if (fromGateway) {
                    VeGoBanner.show(PaymentActivity.this, getString(R.string.payment_not_finished),
                            VeGoBanner.Type.INFO);
                }
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }

            @Override
            protected void onDone() {
                setBusy(R.id.btn_pay, false);
                bindPayButton();
            }
        });
    }

    /** Đơn chưa giữ trên server (vd vào thẳng màn này) -> quay về Xác nhận để giữ ghế. */
    private boolean requireServerBooking() {
        if (draft.bookingCode != null) return true;
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(R.string.booking_not_held_title)
                .message(R.string.booking_not_held_message)
                .primary(R.string.confirm_hold_seat, () -> {
                    open(BookingConfirmActivity.class);
                    finish();
                })
                .secondary(R.string.action_cancel, null)
                .show();
        return false;
    }

    /** Hết giờ giữ ghế / đơn bị hủy / đã thanh toán -> chuyển màn tương ứng. */
    private boolean handleBookingGone(ApiModels.ApiError error) {
        if (error.is("HOLD_EXPIRED") || error.is("BOOKING_CANCELLED") || error.is("BOOKING_NOT_FOUND")) {
            BookingNav.holdExpired(this);
            return true;
        }
        if (error.is("ALREADY_PAID")) {
            checkResult(false);
            return true;
        }
        return false;
    }
}
