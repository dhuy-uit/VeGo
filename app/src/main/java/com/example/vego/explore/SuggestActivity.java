package com.example.vego.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.booking.TripDetailActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.UserSession;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Figma: "Khám phá · Đề xuất chuyến" — tab "Gợi ý".
 * GET /api/v1/suggestions: dựa trên chuyến đã đi gần nhất (cùng tuyến, nhà xe, buổi đi);
 * chưa có lịch sử thì gợi ý chuyến đánh giá cao của tuyến phổ biến.
 */
public class SuggestActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();
    /** Ngày đi của các chuyến gợi ý (server chọn, mặc định ngày mai). */
    private final Calendar date = Calendar.getInstance();

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_suggest);

        setupBottomNav(NavTab.SUGGEST);
        // Theo prototype: quay lại -> Trang chủ
        setupBackButton(this::goHome);
        // Chưa tải xong thì ẩn thẻ mẫu trong layout
        findViewById(R.id.card_main).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_view_trip).setVisibility(View.INVISIBLE);
        findViewById(R.id.tv_more_title).setVisibility(View.GONE);
        findViewById(R.id.list_more).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tính lại mỗi lần mở (vd vừa đi xong / vừa đánh giá chuyến khác)
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        ApiClient.api().suggestions(UserSession.toIsoDate(tomorrow))
                .enqueue(new ApiCallback<ApiModels.Suggestion>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.Suggestion s) {
                        if (s == null || s.main == null) {
                            ((TextView) findViewById(R.id.tv_suggest_heading)).setText(R.string.suggest_none_title);
                            ((TextView) findViewById(R.id.tv_suggest_reason)).setText(R.string.suggest_none_body);
                            return;
                        }
                        render(s);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        showError(error);
                    }
                });
    }

    private void render(ApiModels.Suggestion s) {
        Calendar d = UserSession.parseIsoDate(s.date);
        if (d != null) date.setTimeInMillis(d.getTimeInMillis());
        Trip main = Trip.from(s.main);
        List<Trip> more = new ArrayList<>();
        if (s.more != null) for (ApiModels.TripDto t : s.more) more.add(Trip.from(t));

        TextView heading = findViewById(R.id.tv_suggest_heading);
        TextView reason = findViewById(R.id.tv_suggest_reason);
        ApiModels.BasedOn basedOn = s.basedOn;
        if (basedOn != null) {
            Calendar went = UserSession.parseIsoDate(basedOn.travelDate);
            heading.setText(getString(R.string.suggest_heading, main.destination));
            reason.setText(getString(R.string.suggest_reason_body, basedOn.origin, basedOn.destination,
                    went != null ? Format.shortDate(went) : "", basedOn.operator, basedOn.slot));
        } else {
            heading.setText(getString(R.string.suggest_heading_new, main.destination));
            reason.setText(R.string.suggest_reason_new);
        }

        View mainCard = findViewById(R.id.card_main);
        mainCard.setVisibility(View.VISIBLE);
        TripCardBinder.bindWithBadge(mainCard, main, date, getString(R.string.suggest_match_badge), false);
        mainCard.setBackgroundResource(R.drawable.bg_card_clickable);
        mainCard.setOnClickListener(v -> openTrip(main));

        TextView viewTrip = findViewById(R.id.btn_view_trip);
        viewTrip.setVisibility(View.VISIBLE);
        viewTrip.setText(getString(R.string.suggest_view_trip, Format.date(date)));
        viewTrip.setOnClickListener(v -> openTrip(main));

        LinearLayout listMore = findViewById(R.id.list_more);
        listMore.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Trip trip : more) {
            View card = inflater.inflate(R.layout.view_trip_card, listMore, false);
            TripCardBinder.bindWithSeatsLeft(card, trip, date);
            card.setBackgroundResource(R.drawable.bg_card_clickable);
            card.setOnClickListener(v -> openTrip(trip));
            listMore.addView(card);
        }
        int moreVisibility = more.isEmpty() ? View.GONE : View.VISIBLE;
        findViewById(R.id.tv_more_title).setVisibility(moreVisibility);
        listMore.setVisibility(moreVisibility);
    }

    /** Theo prototype: "Xem chuyến" -> Chi tiết chuyến (ngày đi theo gợi ý). */
    private void openTrip(Trip trip) {
        draft.origin = trip.origin;
        draft.destination = trip.destination;
        draft.date.setTimeInMillis(date.getTimeInMillis());
        draft.selectTrip(trip);
        open(TripDetailActivity.class);
    }
}
