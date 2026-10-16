package com.example.vego.tickets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.NotificationStore;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;

/** Figma: "Vé & sau chuyến · Thông báo". */
public class NotificationsActivity extends BaseActivity {

    private final NotificationStore store = NotificationStore.get();
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_notifications);
        list = findViewById(R.id.list_notifications);

        setupBackButton(this::finish);
        onClick(R.id.btn_mark_all, v -> {
            if (store.unreadCount() == 0) return;
            store.markAllRead();
            render();
            VeGoBanner.show(this, R.string.notifications_marked, VeGoBanner.Type.SUCCESS);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        int unread = store.unreadCount();
        TextView tvUnread = findViewById(R.id.tv_unread);
        tvUnread.setText(unread > 0 ? getString(R.string.notifications_unread, unread)
                : getString(R.string.notifications_all_read));
        View markAll = findViewById(R.id.btn_mark_all);
        markAll.setEnabled(unread > 0);
        markAll.setAlpha(unread > 0 ? 1f : 0.5f);

        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (NotificationStore.Item item : store.all()) {
            View row = inflater.inflate(R.layout.item_notification, list, false);
            ((ImageView) row.findViewById(R.id.iv_notification_icon)).setImageResource(item.icon());
            ((TextView) row.findViewById(R.id.tv_notification_title)).setText(item.title);
            ((TextView) row.findViewById(R.id.tv_notification_body)).setText(item.body);
            ((TextView) row.findViewById(R.id.tv_notification_time)).setText(item.time);

            TextView state = row.findViewById(R.id.tv_notification_state);
            row.findViewById(R.id.dot_unread).setVisibility(item.read ? View.GONE : View.VISIBLE);
            if (item.read) {
                row.setBackgroundResource(R.drawable.bg_card_clickable);
                state.setText(R.string.notification_read);
                state.setBackgroundResource(R.drawable.bg_chip);
                state.setTextColor(ContextCompat.getColor(this, R.color.vego_text_secondary));
            } else {
                state.setText(R.string.notification_unread);
            }
            row.setOnClickListener(v -> open(item));
            list.addView(row);
        }
    }

    /** Chạm vào thông báo: đánh dấu đã đọc và mở nội dung liên quan. */
    private void open(NotificationStore.Item item) {
        item.read = true;
        if (item.kind == NotificationStore.Kind.PROMO) {
            render();
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.INFO)
                    .title(R.string.notifications_promo_title)
                    .message(R.string.notifications_promo_message)
                    .primary(R.string.action_ok, null)
                    .show();
            return;
        }
        Ticket ticket = item.ticketCode != null ? TicketStore.get().find(item.ticketCode) : null;
        if (ticket == null) {
            render();
            return;
        }
        if (item.kind == NotificationStore.Kind.REVIEW && ticket.status == Ticket.Status.COMPLETED && ticket.rating == 0) {
            startActivity(ReviewActivity.intent(this, ticket.code));
        } else {
            startActivity(ETicketActivity.intent(this, ticket.code));
        }
    }
}
