package com.example.vego.tickets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.NotificationStore;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Figma: "Vé & sau chuyến · Thông báo".
 * Danh sách lấy từ GET /api/v1/notifications; đánh dấu đã đọc gửi lên server.
 */
public class NotificationsActivity extends BaseActivity {

    private final NotificationStore store = NotificationStore.get();
    private LinearLayout list;
    private boolean loaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireLogin()) return;
        setScreenContent(R.layout.activity_notifications);
        list = findViewById(R.id.list_notifications);

        setupBackButton(this::finish);
        onClick(R.id.btn_mark_all, v -> markAllRead());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        load();
    }

    private void load() {
        ApiClient.api().notifications().enqueue(new ApiCallback<ApiModels.Inbox>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Inbox inbox) {
                store.setInbox(inbox);
                loaded = true;
                render();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
            }
        });
    }

    private void markAllRead() {
        if (store.unreadCount() == 0) return;
        store.markAllRead();
        render();
        ApiClient.api().markAllNotificationsRead().enqueue(new ApiCallback<Void>(this) {
            @Override
            protected void onSuccess(@Nullable Void body) {
                VeGoBanner.show(NotificationsActivity.this, R.string.notifications_marked, VeGoBanner.Type.SUCCESS);
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
                load();
            }
        });
    }

    private void render() {
        int unread = store.unreadCount();
        TextView tvUnread = findViewById(R.id.tv_unread);
        tvUnread.setText(!loaded && store.all().isEmpty() ? getString(R.string.notifications_loading)
                : unread > 0 ? getString(R.string.notifications_unread, unread)
                : getString(R.string.notifications_all_read));
        View markAll = findViewById(R.id.btn_mark_all);
        markAll.setEnabled(unread > 0);
        markAll.setAlpha(unread > 0 ? 1f : 0.5f);

        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (loaded && store.all().isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.notifications_empty);
            empty.setTextColor(ContextCompat.getColor(this, R.color.vego_text_secondary));
            empty.setPadding(0, 24, 0, 24);
            list.addView(empty);
            return;
        }
        for (NotificationStore.Item item : store.all()) {
            View row = inflater.inflate(R.layout.item_notification, list, false);
            ((ImageView) row.findViewById(R.id.iv_notification_icon)).setImageResource(item.icon());
            ((TextView) row.findViewById(R.id.tv_notification_title)).setText(item.title);
            ((TextView) row.findViewById(R.id.tv_notification_body)).setText(item.body);
            ((TextView) row.findViewById(R.id.tv_notification_time)).setText(item.time());

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

    /** Chạm vào thông báo: đánh dấu đã đọc (gửi server, không cần chờ) và mở nội dung liên quan. */
    private void open(NotificationStore.Item item) {
        if (!item.read) {
            store.markRead(item);
            ApiClient.api().markNotificationRead(item.id).enqueue(new Callback<ApiModels.NotificationItem>() {
                @Override
                public void onResponse(Call<ApiModels.NotificationItem> call, Response<ApiModels.NotificationItem> r) { }

                @Override
                public void onFailure(Call<ApiModels.NotificationItem> call, Throwable t) { }
            });
        }
        if (item.ticketCode == null) {
            render();
            VeGoDialog.with(this)
                    .type(VeGoDialog.Type.INFO)
                    .title(item.title)
                    .message(item.body)
                    .primary(R.string.action_ok, null)
                    .show();
            return;
        }
        com.example.vego.data.Ticket ticket = com.example.vego.data.TicketStore.get().find(item.ticketCode);
        boolean wantsReview = item.kind == NotificationStore.Kind.REVIEW
                && (ticket == null || (ticket.canReview && ticket.rating == 0));
        startActivity(wantsReview ? ReviewActivity.intent(this, item.ticketCode)
                : ETicketActivity.intent(this, item.ticketCode));
    }
}
