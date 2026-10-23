package com.example.vego.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.UserSession;
import com.example.vego.data.BookingDraft;
import com.example.vego.data.Format;
import com.example.vego.data.Trip;
import com.example.vego.data.TripFilter;
import com.example.vego.data.TripRepository;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Figma: "Đặt vé · Danh sách chuyến". */
public class TripListActivity extends BaseActivity {

    /** Số chuyến hiện trước khi bấm "Xem thêm chuyến" (Figma hiện 3/6). */
    private static final int PAGE_SIZE = 3;

    private enum Sort { RECOMMENDED, DEPARTURE, PRICE, RATING }

    private final BookingDraft draft = BookingDraft.get();
    private Sort sort = Sort.RECOMMENDED;
    private boolean showAll = false;
    /** Chuyến đang được chạm chọn (null = chưa chọn, nút "Chọn chuyến" ẩn). */
    private String selectedCode;
    private TextView quickButton;
    private boolean resumedOnce = false;

    private LinearLayout listTrips;
    private TextView tvCount;
    private TextView tvShowMore;
    private TextView btnFilter;
    private View groupEmpty;
    private TextView chipDeparture;
    private TextView chipPrice;
    private TextView chipRating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_trip_list);

        listTrips = findViewById(R.id.list_trips);
        tvCount = findViewById(R.id.tv_result_count);
        tvShowMore = findViewById(R.id.tv_show_more);
        btnFilter = findViewById(R.id.btn_filter);
        groupEmpty = findViewById(R.id.group_empty);
        chipDeparture = findViewById(R.id.chip_sort_departure);
        chipPrice = findViewById(R.id.chip_sort_price);
        chipRating = findViewById(R.id.chip_sort_rating);

        setupBackButton(this::finish);
        btnFilter.setOnClickListener(v -> open(TripFilterActivity.class));
        chipDeparture.setOnClickListener(v -> toggleSort(Sort.DEPARTURE));
        chipPrice.setOnClickListener(v -> toggleSort(Sort.PRICE));
        chipRating.setOnClickListener(v -> toggleSort(Sort.RATING));
        tvShowMore.setOnClickListener(v -> {
            showAll = true;
            render(false);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Vẽ lại mỗi lần quay về (vd sau khi đổi Bộ lọc)
        ((TextView) findViewById(R.id.tv_route)).setText(draft.origin + " → " + draft.destination);
        ((TextView) findViewById(R.id.tv_route_subtitle))
                .setText(getString(R.string.list_subtitle, Format.date(draft.date)));
        render(true);
        // Lần đầu mở đã có kết quả vừa tìm; quay lại từ màn khác thì lấy số ghế mới nhất
        if (resumedOnce) refresh();
        resumedOnce = true;
    }

    /** Lấy lại số ghế trống mới nhất (người khác có thể vừa đặt) rồi vẽ lại không hiệu ứng. */
    private void refresh() {
        ApiClient.api().searchTrips(draft.origin, draft.destination, UserSession.toIsoDate(draft.date))
                .enqueue(new ApiCallback<ApiModels.TripSearch>(this) {
                    @Override
                    protected void onSuccess(@Nullable ApiModels.TripSearch result) {
                        if (result == null || !result.routeFound) return;
                        TripRepository.setResults(result);
                        render(false);
                    }

                    @Override
                    protected void onError(ApiModels.ApiError error) {
                        // Vẫn hiện kết quả đã tải trước đó
                    }
                });
    }

    /** Bấm chip đang chọn lần nữa thì bỏ sắp xếp, về thứ tự đề xuất. */
    private void toggleSort(Sort target) {
        sort = (sort == target) ? Sort.RECOMMENDED : target;
        render(true);
    }

    private void render(boolean animate) {
        chipDeparture.setSelected(sort == Sort.DEPARTURE);
        chipPrice.setSelected(sort == Sort.PRICE);
        chipRating.setSelected(sort == Sort.RATING);

        TripFilter filter = draft.filter;
        btnFilter.setText(filter == null ? getString(R.string.list_filter)
                : getString(R.string.list_filter_active, activeCriteria(filter)));

        List<Trip> trips = new ArrayList<>(TripRepository.search(filter));
        switch (sort) {
            case DEPARTURE: trips.sort(Comparator.comparingInt(t -> t.departMinutes)); break;
            case PRICE: trips.sort(Comparator.comparingInt(t -> t.price)); break;
            case RATING: trips.sort((a, b) -> Double.compare(b.rating, a.rating)); break;
            default:
                // Đề xuất: chuyến đi qua bến đón đã chọn ở màn Tìm chuyến lên trước
                if (draft.preferredPickup != null) {
                    String preferred = draft.preferredPickup;
                    trips.sort((a, b) -> Boolean.compare(!preferred.equals(a.pickupStation),
                            !preferred.equals(b.pickupStation)));
                }
                break;
        }

        tvCount.setText(getString(R.string.list_count, trips.size()));
        groupEmpty.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);

        int visible = showAll ? trips.size() : Math.min(PAGE_SIZE, trips.size());
        listTrips.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        quickButton = (TextView) inflater.inflate(R.layout.item_primary_button, listTrips, false);
        View selectedCard = null;
        Trip selectedTrip = null;
        for (int i = 0; i < visible; i++) {
            Trip trip = trips.get(i);
            View card = inflater.inflate(R.layout.view_trip_card, listTrips, false);
            TripCardBinder.bindWithSeatsLeft(card, trip, draft.date);
            card.setBackgroundResource(R.drawable.bg_card_selectable);
            card.setOnClickListener(v -> onCardTapped(card, trip));
            listTrips.addView(card);
            if (trip.id.equals(selectedCode)) {
                selectedCard = card;
                selectedTrip = trip;
            }
        }
        // Giữ lựa chọn khi đổi sắp xếp / xem thêm, nếu chuyến đó vẫn còn trong danh sách
        if (selectedCard != null) {
            showQuickButton(selectedCard, selectedTrip, false);
        } else {
            selectedCode = null;
        }

        if (trips.size() > visible) {
            tvShowMore.setVisibility(View.VISIBLE);
            tvShowMore.setText(getString(R.string.list_show_more, visible, trips.size()));
            tvShowMore.setClickable(true);
        } else if (!trips.isEmpty()) {
            tvShowMore.setVisibility(View.VISIBLE);
            tvShowMore.setText(getString(R.string.list_showing_all, trips.size()));
            tvShowMore.setClickable(false);
        } else {
            tvShowMore.setVisibility(View.GONE);
        }

        if (animate) animateCards();
    }

    /**
     * Chạm vào thẻ: chọn chuyến đó, nút "Chọn chuyến HH:MM" hiện ngay bên dưới.
     * Chạm lại đúng thẻ đang chọn thì bỏ chọn.
     */
    private void onCardTapped(View card, Trip trip) {
        if (trip.id.equals(selectedCode)) {
            selectedCode = null;
            card.setSelected(false);
            hideQuickButton();
            return;
        }
        selectedCode = trip.id;
        for (int i = 0; i < listTrips.getChildCount(); i++) {
            View child = listTrips.getChildAt(i);
            if (child != quickButton) child.setSelected(false);
        }
        showQuickButton(card, trip, true);
    }

    private void showQuickButton(View card, Trip trip, boolean animate) {
        card.setSelected(true);
        quickButton.animate().cancel();
        if (quickButton.getParent() != null) listTrips.removeView(quickButton);
        quickButton.setText(getString(R.string.list_choose_trip, trip.departTime()));
        quickButton.setOnClickListener(v -> openTrip(trip));
        listTrips.addView(quickButton, listTrips.indexOfChild(card) + 1);
        if (animate) {
            quickButton.setAlpha(0f);
            quickButton.setTranslationY(-12 * getResources().getDisplayMetrics().density);
            quickButton.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(200).start();
        } else {
            quickButton.setAlpha(1f);
            quickButton.setTranslationY(0f);
        }
    }

    private void hideQuickButton() {
        TextView button = quickButton;
        if (button.getParent() == null) return;
        button.animate().alpha(0f).setStartDelay(0).setDuration(150)
                .withEndAction(() -> listTrips.removeView(button)).start();
    }

    /** Các thẻ lần lượt trượt lên và hiện dần. */
    private void animateCards() {
        float offset = 24 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < listTrips.getChildCount(); i++) {
            View child = listTrips.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(offset);
            child.animate().alpha(1f).translationY(0f)
                    .setStartDelay(60L * i).setDuration(260).start();
        }
    }

    private static int activeCriteria(TripFilter f) {
        int n = 0;
        if (!f.timeSlots.isEmpty()) n++;
        if (!f.operators.isEmpty()) n++;
        if (!f.busTypes.isEmpty()) n++;
        if (f.minPrice != TripFilter.PRICE_MIN || f.maxPrice != TripFilter.PRICE_MAX) n++;
        return n;
    }

    private void openTrip(Trip trip) {
        draft.selectTrip(trip);
        open(TripDetailActivity.class);
    }
}
