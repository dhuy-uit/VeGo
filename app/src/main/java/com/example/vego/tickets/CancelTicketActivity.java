package com.example.vego.tickets;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.common.widget.InfoRowView;
import com.example.vego.data.Format;
import com.example.vego.data.NotificationStore;
import com.example.vego.data.Ticket;
import com.example.vego.data.TicketStore;

/** Figma: "Vé & sau chuyến · Xác nhận hủy vé". */
public class CancelTicketActivity extends BaseActivity {

    private static final String EXTRA_CODE = "ticket_code";

    private Ticket ticket;

    public static Intent intent(Context context, String ticketCode) {
        return new Intent(context, CancelTicketActivity.class).putExtra(EXTRA_CODE, ticketCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_cancel_ticket);

        String code = getIntent().getStringExtra(EXTRA_CODE);
        ticket = code != null ? TicketStore.get().find(code) : null;
        if (ticket == null) {
            finish();
            return;
        }

        int percent = TicketStore.refundPercent(ticket);
        int refund = TicketStore.refundAmount(ticket);
        ((TextView) findViewById(R.id.tv_route)).setText(ticket.route());
        ((InfoRowView) findViewById(R.id.row_code)).setValue(ticket.code);
        ((InfoRowView) findViewById(R.id.row_operator)).setValue(ticket.operator);
        ((InfoRowView) findViewById(R.id.row_depart)).setValue(ticket.departTime() + " · " + Format.date(ticket.date));
        ((InfoRowView) findViewById(R.id.row_passenger)).setValue(ticket.passengerName);
        ((InfoRowView) findViewById(R.id.row_seat)).setValue(ticket.seat);
        ((InfoRowView) findViewById(R.id.row_paid_fare)).setValue(Format.money(ticket.price));
        InfoRowView fee = findViewById(R.id.row_cancel_fee);
        fee.setLabel(getString(R.string.label_cancel_fee, 100 - percent));
        fee.setValue(Format.money(ticket.price - refund));
        InfoRowView refundRow = findViewById(R.id.row_refund);
        refundRow.setLabel(getString(R.string.label_refund, percent));
        refundRow.setValue(Format.money(refund));
        ((TextView) findViewById(R.id.tv_refund_note)).setText(getString(R.string.cancel_refund_note, ticket.paymentMethod));

        // Theo prototype: quay lại / "Giữ vé của tôi" -> Vé điện tử
        setupBackButton(this::finish);
        onClick(R.id.btn_keep, v -> finish());
        onClick(R.id.btn_confirm_cancel, v -> confirm(refund));
    }

    /** Hỏi lại lần cuối trước khi hủy, vì không thể hoàn tác. */
    private void confirm(int refund) {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.DANGER)
                .title(getString(R.string.cancel_dialog_title, ticket.code))
                .message(getString(R.string.cancel_dialog_message, Format.money(refund)))
                .primary(R.string.cancel_dialog_confirm, this::cancelTicket)
                .secondary(R.string.cancel_dialog_keep, null)
                .show();
    }

    private void cancelTicket() {
        TicketStore.get().cancel(ticket);
        NotificationStore.get().push(NotificationStore.Kind.TICKET,
                getString(R.string.cancel_notify_title, ticket.code),
                getString(R.string.cancel_notify_body, Format.money(ticket.refundAmount),
                        ticket.paymentMethod, ticket.seat),
                ticket.code);

        VeGoDialog.with(this)
                .type(VeGoDialog.Type.SUCCESS)
                .title(R.string.cancel_done_title)
                .message(getString(R.string.cancel_done_message,
                        Format.money(ticket.refundAmount), ticket.paymentMethod))
                .cancelable(false)
                .primary(R.string.action_ok, () -> {
                    // Về "Vé của tôi", mở sẵn tab Đã hủy
                    startActivity(MyTicketsActivity.intent(this, Ticket.Status.CANCELLED));
                    finish();
                })
                .show();
    }
}
