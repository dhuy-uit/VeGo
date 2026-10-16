package com.example.vego.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.booking.TripDetailActivity;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.SuggestionEngine;
import com.example.vego.data.Ticket;
import com.example.vego.data.Trip;

/** Figma: "Khám phá · Đề xuất chuyến" — tab "Gợi ý". */
public class SuggestActivity extends BaseActivity {

    private final BookingDraft draft = BookingDraft.get();

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tính lại mỗi lần mở (vd vừa đi xong / vừa đánh giá chuyến khác)
        render(SuggestionEngine.suggest());
    }

    private void render(SuggestionEngine.Result result) {
        Trip main = result.main;
        Ticket basedOn = result.basedOn;

        TextView heading = findViewById(R.id.tv_suggest_heading);
        TextView reason = findViewById(R.id.tv_suggest_reason);
        if (basedOn != null) {
            heading.setText(getString(R.string.suggest_heading, main.destination));
            reason.setText(getString(R.string.suggest_reason_body, basedOn.origin, basedOn.destination,
                    Format.shortDate(basedOn.date), basedOn.operator,
                    SuggestionEngine.slotName(basedOn.departMinutes)));
        } else {
            heading.setText(getString(R.string.suggest_heading_new, main.destination));
            reason.setText(R.string.suggest_reason_new);
        }

        View mainCard = findViewById(R.id.card_main);
        TripCardBinder.bindWithBadge(mainCard, main, draft.date, getString(R.string.suggest_match_badge), false);
        mainCard.setBackgroundResource(R.drawable.bg_card_clickable);
        mainCard.setOnClickListener(v -> openTrip(main));

        TextView viewTrip = findViewById(R.id.btn_view_trip);
        viewTrip.setText(getString(R.string.suggest_view_trip, Format.date(draft.date)));
        viewTrip.setOnClickListener(v -> openTrip(main));

        LinearLayout listMore = findViewById(R.id.list_more);
        listMore.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Trip trip : result.more) {
            View card = inflater.inflate(R.layout.view_trip_card, listMore, false);
            TripCardBinder.bindWithSeatsLeft(card, trip, draft.date);
            card.setBackgroundResource(R.drawable.bg_card_clickable);
            card.setOnClickListener(v -> openTrip(trip));
            listMore.addView(card);
        }
        int moreVisibility = result.more.isEmpty() ? View.GONE : View.VISIBLE;
        findViewById(R.id.tv_more_title).setVisibility(moreVisibility);
        listMore.setVisibility(moreVisibility);
    }

    /** Theo prototype: "Xem chuyến" -> Chi tiết chuyến. */
    private void openTrip(Trip trip) {
        draft.origin = trip.origin;
        draft.destination = trip.destination;
        draft.selectTrip(trip);
        open(TripDetailActivity.class);
    }
}
