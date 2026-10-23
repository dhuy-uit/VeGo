package com.example.vego.tickets;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.QrCode;
import com.example.vego.common.TripCardBinder;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.io.OutputStream;

/** Figma: "Vé & sau chuyến · Vé điện tử". */
public class ETicketActivity extends BaseActivity {

    private static final String EXTRA_CODE = "ticket_code";

    @Nullable private Ticket ticket;
    private String code;

    public static Intent intent(Context context, String ticketCode) {
        return new Intent(context, ETicketActivity.class).putExtra(EXTRA_CODE, ticketCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_eticket);

        code = getIntent().getStringExtra(EXTRA_CODE);
        if (code == null || !requireLogin()) {
            if (code == null) finish();
            return;
        }
        ticket = TicketStore.get().find(code);

        setupBackButton(this::finish);
        onClick(R.id.btn_save_ticket, v -> { if (ticket != null) saveToGallery(); });
        onClick(R.id.btn_cancel_ticket, v -> {
            if (ticket == null) return;
            if (!ticket.cancellable) {
                VeGoBanner.show(this, R.string.eticket_cannot_cancel, VeGoBanner.Type.WARNING);
                return;
            }
            startActivity(CancelTicketActivity.intent(this, ticket.code));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ticket != null) bind();
        // Lấy bản mới nhất (trạng thái, số tiền hoàn nếu hủy lúc này)
        ApiClient.api().ticket(code).enqueue(new ApiCallback<ApiModels.Ticket>(this) {
            @Override
            protected void onSuccess(@Nullable ApiModels.Ticket dto) {
                if (dto == null) return;
                ticket = TicketStore.get().put(dto);
                bind();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                showError(error);
                if (ticket == null && error.status == 404) finish();
            }
        });
    }

    private void bind() {
        Ticket t = ticket;
        if (t == null) return;
        TextView badge = findViewById(R.id.tv_eticket_badge);
        View cancelButton = findViewById(R.id.btn_cancel_ticket);
        View reminder = findViewById(R.id.group_reminder);
        ImageView qr = findViewById(R.id.iv_qr);

        if (t.status == Ticket.Status.CANCELLED) {
            badge.setText(R.string.eticket_badge_cancelled);
            badge.setBackgroundResource(R.drawable.bg_badge_danger);
            badge.setTextColor(ContextCompat.getColor(this, R.color.vego_danger));
            qr.setAlpha(0.25f);
        } else if (t.status == Ticket.Status.COMPLETED) {
            badge.setText(R.string.eticket_badge_completed);
            qr.setAlpha(0.5f);
        } else {
            badge.setText(getString(R.string.eticket_badge_paid, t.seat));
            qr.setAlpha(1f);
        }
        cancelButton.setVisibility(t.status == Ticket.Status.UPCOMING ? View.VISIBLE : View.GONE);
        reminder.setVisibility(t.status == Ticket.Status.UPCOMING ? View.VISIBLE : View.GONE);

        ((TextView) findViewById(R.id.tv_ticket_code)).setText(t.code);
        int size = Math.round(156 * getResources().getDisplayMetrics().density);
        // Nội dung QR do server ký để nhà xe soát vé
        qr.setImageBitmap(QrCode.create(t.qrData, size));

        TripCardBinder.bindTicket(findViewById(R.id.card_trip), t);
        ((TextView) findViewById(R.id.tv_passenger)).setText(t.passengerName + " · " + t.passengerPhone);
        ((TextView) findViewById(R.id.tv_seat_qty)).setText(getString(R.string.confirm_seat_qty_value, t.seat));
        ((TextView) findViewById(R.id.tv_stops)).setText(t.pickup + "\n→ " + t.dropoff);
        ((InfoRowView) findViewById(R.id.row_fare_fee)).setValue(Format.money(t.fare) + " / " + Format.money(0));
        ((InfoRowView) findViewById(R.id.row_paid)).setValue(Format.money(t.price));

        int meet = (t.departMinutes - 30 + 24 * 60) % (24 * 60);
        ((TextView) findViewById(R.id.tv_reminder_title)).setText(
                getString(R.string.eticket_info_title, Format.time(meet), Format.shortDate(t.date)));
        ((TextView) findViewById(R.id.tv_reminder_body)).setText(getString(R.string.eticket_info_body, t.pickup));
    }

    /** Chụp khối mã vé + QR thành ảnh PNG và lưu vào Pictures/VeGo (không cần xin quyền từ Android 10). */
    private void saveToGallery() {
        View card = findViewById(R.id.card_qr);
        Bitmap bitmap = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        card.draw(canvas);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "VeGo_" + ticket.code + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VeGo");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        boolean ok = false;
        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                ok = out != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception ignored) {
                ok = false;
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
        }
        VeGoBanner.show(this, ok ? R.string.eticket_saved : R.string.eticket_save_failed,
                ok ? VeGoBanner.Type.SUCCESS : VeGoBanner.Type.ERROR);
    }
}
