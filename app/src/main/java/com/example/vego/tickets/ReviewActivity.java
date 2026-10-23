package com.example.vego.tickets;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.List;

/** Figma: "Vé & sau chuyến · Đánh giá nhà xe". */
public class ReviewActivity extends BaseActivity {

    private static final String EXTRA_CODE = "ticket_code";
    private static final int[] STARS = { R.id.star_1, R.id.star_2, R.id.star_3, R.id.star_4, R.id.star_5 };
    private static final int[] LABELS = {
            R.string.review_rating_1, R.string.review_rating_2, R.string.review_rating_3,
            R.string.review_rating_4, R.string.review_rating_5
    };
    private static final int[][] TAGS = {
            { R.id.tag_clean, R.string.review_tag_clean },
            { R.id.tag_ontime, R.string.review_tag_ontime },
            { R.id.tag_staff, R.string.review_tag_staff }
    };
    /** Mã nhãn gửi lên server, cùng thứ tự với TAGS. */
    private static final String[] TAG_CODES = { "CLEAN", "ONTIME", "STAFF" };

    private Ticket ticket;
    private int rating = 5;

    public static Intent intent(Context context, String ticketCode) {
        return new Intent(context, ReviewActivity.class).putExtra(EXTRA_CODE, ticketCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_review);

        String code = getIntent().getStringExtra(EXTRA_CODE);
        ticket = code != null ? TicketStore.get().find(code) : null;
        if (ticket == null) {
            if (code != null && UserSession.get().isLoggedIn()) loadAndRecreate(code);
            else finish();
            return;
        }

        ((TextView) findViewById(R.id.tv_operator)).setText(ticket.operator);
        ((TextView) findViewById(R.id.tv_route)).setText(ticket.route());
        ((TextView) findViewById(R.id.tv_schedule)).setText(
                ticket.departTime() + " · " + Format.date(ticket.date) + " → " + ticket.arriveTime() + " · "
                        + Format.date(Format.plusDays(ticket.date, ticket.arriveDayOffset())));
        ((TextView) findViewById(R.id.tv_ticket)).setText("Vé " + ticket.code + " · Ghế " + ticket.seat);
        ((TextView) findViewById(R.id.tv_review_info)).setText(
                getString(R.string.review_info_body, Format.shortDate(ticket.date)));

        if (ticket.rating > 0) rating = ticket.rating;
        for (int i = 0; i < STARS.length; i++) {
            final int value = i + 1;
            TextView star = findViewById(STARS[i]);
            star.setContentDescription(getString(R.string.cd_star, value));
            star.setOnClickListener(v -> setRating(value));
        }
        for (int i = 0; i < TAGS.length; i++) {
            int[] tag = TAGS[i];
            TextView chip = findViewById(tag[0]);
            // Đã đánh giá: hiện lại nhãn đã chọn; chưa đánh giá: chọn sẵn cả 3 như Figma
            chip.setSelected(ticket.rating == 0 || ticket.reviewTags.contains(TAG_CODES[i]));
            styleTag(chip, tag[1]);
            chip.setOnClickListener(v -> {
                chip.setSelected(!chip.isSelected());
                styleTag(chip, tag[1]);
            });
        }
        setRating(rating);

        // Theo prototype: quay lại / Gửi đánh giá -> Vé của tôi
        setupBackButton(this::finish);
        onClick(R.id.btn_submit, v -> submit());
    }

    private void styleTag(TextView chip, int labelRes) {
        chip.setText(chip.isSelected() ? getString(labelRes) + " ✓" : getString(labelRes));
    }

    /** Tô màu số sao đã chọn và "nảy" nhẹ ngôi sao vừa chạm. */
    private void setRating(int value) {
        rating = value;
        for (int i = 0; i < STARS.length; i++) {
            TextView star = findViewById(STARS[i]);
            star.setTextColor(ContextCompat.getColor(this, i < value ? R.color.vego_star : R.color.vego_star_off));
        }
        TextView tapped = findViewById(STARS[value - 1]);
        tapped.setScaleX(1.3f);
        tapped.setScaleY(1.3f);
        tapped.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        ((TextView) findViewById(R.id.tv_rating_label)).setText(LABELS[value - 1]);
    }

    private void submit() {
        if (rating == 0) {
            VeGoBanner.show(this, R.string.review_need_rating, VeGoBanner.Type.WARNING);
            return;
        }
        EditText comment = findViewById(R.id.et_comment);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < TAGS.length; i++) {
            if (findViewById(TAGS[i][0]).isSelected()) tags.add(TAG_CODES[i]);
        }
        String text = comment.getText().toString().trim();
        comment.clearFocus();
        setBusy(R.id.btn_submit, true);
        ApiClient.api().reviewTicket(ticket.code, new ApiModels.ReviewRequest(rating, tags, text.isEmpty() ? null : text))
                .enqueue(new ApiCallback<ApiModels.Ticket>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.Ticket dto) {
                        if (dto != null) ticket = TicketStore.get().put(dto);
                        VeGoDialog.with(ReviewActivity.this)
                                .type(VeGoDialog.Type.SUCCESS)
                                .title(R.string.review_done_title)
                                .message(getString(R.string.review_done_message, rating, ticket.operator))
                                .cancelable(false)
                                .primary(R.string.action_ok, () -> {
                                    startActivity(MyTicketsActivity.intent(ReviewActivity.this, Ticket.Status.COMPLETED));
                                    finish();
                                })
                                .show();
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        showError(error);
                    }

                    @Override
                    protected void onDone() {
                        setBusy(R.id.btn_submit, false);
                    }
                });
    }

    /** Mở từ thông báo khi chưa tải danh sách vé: lấy vé từ server rồi mở lại màn này. */
    private void loadAndRecreate(String code) {
        ApiClient.api().ticket(code).enqueue(new ApiCallback<ApiModels.Ticket>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Ticket dto) {
                if (dto == null) {
                    finish();
                    return;
                }
                TicketStore.get().put(dto);
                recreate();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
                finish();
            }
        });
    }
}
