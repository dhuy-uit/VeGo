package com.example.vego.tickets;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;

import java.util.List;

/** Figma: "Vé & sau chuyến · Vé của tôi" — tab "Vé của tôi". */
public class MyTicketsActivity extends BaseActivity {

    public static final String EXTRA_TAB = "tab";

    private Ticket.Status tab = Ticket.Status.UPCOMING;
    private final TicketStore store = TicketStore.get();

    private TextView tabUpcoming;
    private TextView tabCompleted;
    private TextView tabCancelled;
    private LinearLayout listMain;
    private LinearLayout listHistory;
    private View historyTitle;
    private View groupEmpty;
    private TextView tvEmpty;

    /** Mở thẳng vào một tab (vd sau khi hủy vé thì mở tab Đã hủy). */
    public static Intent intent(Context context, Ticket.Status tab) {
        Intent i = new Intent(context, MyTicketsActivity.class);
        i.putExtra(EXTRA_TAB, tab.name());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return i;
    }

    @Override
    protected Transition transition() {
        return Transition.FADE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_my_tickets);

        tabUpcoming = findViewById(R.id.tab_upcoming);
        tabCompleted = findViewById(R.id.tab_completed);
        tabCancelled = findViewById(R.id.tab_cancelled);
        listMain = findViewById(R.id.list_main);
        listHistory = findViewById(R.id.list_history);
        historyTitle = findViewById(R.id.tv_history_title);
        groupEmpty = findViewById(R.id.group_empty);
        tvEmpty = findViewById(R.id.tv_empty);

        readTab(getIntent());
        setupBottomNav(NavTab.TICKETS);
        setupBackButton(this::goHome);
        tabUpcoming.setOnClickListener(v -> selectTab(Ticket.Status.UPCOMING));
        tabCompleted.setOnClickListener(v -> selectTab(Ticket.Status.COMPLETED));
        tabCancelled.setOnClickListener(v -> selectTab(Ticket.Status.CANCELLED));
        onClick(R.id.btn_book_now, v -> goHome());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readTab(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void readTab(Intent intent) {
        String name = intent.getStringExtra(EXTRA_TAB);
        if (name != null) tab = Ticket.Status.valueOf(name);
    }

    private void selectTab(Ticket.Status status) {
        if (tab == status) return;
        tab = status;
        render();
    }

    private void render() {
        tabUpcoming.setText(getString(R.string.tab_upcoming, store.byStatus(Ticket.Status.UPCOMING).size()));
        tabCompleted.setText(getString(R.string.tab_completed, store.byStatus(Ticket.Status.COMPLETED).size()));
        tabCancelled.setText(getString(R.string.tab_cancelled, store.byStatus(Ticket.Status.CANCELLED).size()));
        tabUpcoming.setSelected(tab == Ticket.Status.UPCOMING);
        tabCompleted.setSelected(tab == Ticket.Status.COMPLETED);
        tabCancelled.setSelected(tab == Ticket.Status.CANCELLED);

        listMain.removeAllViews();
        listHistory.removeAllViews();
        List<Ticket> main = store.byStatus(tab);
        for (Ticket t : main) {
            listMain.addView(tab == Ticket.Status.UPCOMING ? upcomingCard(t) : historyCard(t));
        }

        groupEmpty.setVisibility(main.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_book_now).setVisibility(tab == Ticket.Status.UPCOMING ? View.VISIBLE : View.GONE);
        tvEmpty.setText(tab == Ticket.Status.UPCOMING ? R.string.my_tickets_empty_upcoming
                : tab == Ticket.Status.COMPLETED ? R.string.my_tickets_empty_completed
                : R.string.my_tickets_empty_cancelled);

        // Tab "Sắp đi" có thêm phần "Lịch sử gần đây" như Figma
        boolean showHistory = tab == Ticket.Status.UPCOMING && !store.history().isEmpty();
        historyTitle.setVisibility(showHistory ? View.VISIBLE : View.GONE);
        listHistory.setVisibility(showHistory ? View.VISIBLE : View.GONE);
        if (showHistory) {
            for (Ticket t : store.history()) listHistory.addView(historyCard(t));
        }
        animateIn(listMain);
    }

    private View upcomingCard(Ticket t) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_ticket_upcoming, listMain, false);
        ((TextView) card.findViewById(R.id.tv_ticket_route)).setText(t.route());
        ((TextView) card.findViewById(R.id.tv_ticket_operator)).setText(t.operator + " · " + t.shortBusName());
        ((InfoRowView) card.findViewById(R.id.row_ticket_depart)).setValue(t.departTime() + " · " + Format.date(t.date));
        ((InfoRowView) card.findViewById(R.id.row_ticket_code)).setValue(t.code + " · " + t.seat);
        ((InfoRowView) card.findViewById(R.id.row_ticket_amount)).setValue(Format.money(t.price));
        card.findViewById(R.id.btn_view_eticket).setOnClickListener(v -> openTicket(t));
        return card;
    }

    private View historyCard(Ticket t) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_ticket_history, listMain, false);
        TextView badge = card.findViewById(R.id.tv_ticket_badge);
        TextView review = card.findViewById(R.id.btn_review);
        if (t.status == Ticket.Status.CANCELLED) {
            badge.setText(getString(R.string.ticket_badge_cancelled, Format.money(t.refundAmount)));
            badge.setBackgroundResource(R.drawable.bg_badge_danger);
            badge.setTextColor(ContextCompat.getColor(this, R.color.vego_danger));
        } else if (t.rating > 0) {
            badge.setText(getString(R.string.ticket_badge_reviewed, t.rating));
        } else {
            badge.setText(R.string.ticket_badge_completed);
            // Chuyến đã đi, chưa đánh giá -> hiện nút "Đánh giá nhà xe"
            review.setVisibility(View.VISIBLE);
            review.setOnClickListener(v -> startActivity(ReviewActivity.intent(this, t.code)));
        }
        ((TextView) card.findViewById(R.id.tv_ticket_route)).setText(t.route());
        ((TextView) card.findViewById(R.id.tv_ticket_meta)).setText(
                t.operator + " · " + t.departTime() + " · " + Format.date(t.date));
        ((InfoRowView) card.findViewById(R.id.row_ticket_code)).setValue(t.code + " · " + t.seat);
        card.setOnClickListener(v -> openTicket(t));
        return card;
    }

    private void openTicket(Ticket t) {
        startActivity(ETicketActivity.intent(this, t.code));
    }

    private void animateIn(LinearLayout list) {
        float offset = 20 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(offset);
            child.animate().alpha(1f).translationY(0f).setStartDelay(50L * i).setDuration(240).start();
        }
    }
}
