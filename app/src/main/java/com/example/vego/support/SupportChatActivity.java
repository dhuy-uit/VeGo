package com.example.vego.support;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.vego.R;
import com.example.vego.common.BaseActivity;
import com.example.vego.common.UserSession;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoDialog;
import com.example.vego.data.Format;
import com.example.vego.data.Ticket;
import com.example.vego.data.support.SupportAssistant;
import com.example.vego.tickets.ETicketActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Figma nhóm 07 · Hỗ trợ AI. Một màn duy nhất đổi theo nội dung trò chuyện:
 * màn chào + câu hỏi nhanh → hỏi đáp có ghi nguồn → tra cứu vé ngay trong chat → chuyển nhân viên.
 */
public class SupportChatActivity extends BaseActivity {

    /** Câu hỏi nhanh ở màn chào: icon + nội dung (gửi đi đúng như chữ trên chip). */
    private static final int[][] QUICK = {
            { R.drawable.ic_receipt_text, R.string.chat_quick_cancel },
            { R.drawable.ic_luggage, R.string.chat_quick_luggage },
            { R.drawable.ic_ticket, R.string.chat_quick_ticket },
            { R.drawable.ic_calendar_clock, R.string.chat_quick_change },
    };

    private ScrollView scroll;
    private LinearLayout content;
    private View groupWelcome;
    private EditText etMessage;
    private View btnSend;
    private TextView tvStatus;
    private LayoutInflater inflater;

    private boolean started = false;
    private boolean waiting = false;
    private View typingView;
    private final List<ValueAnimator> typingAnims = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setScreenContent(R.layout.activity_support_chat);

        inflater = LayoutInflater.from(this);
        scroll = findViewById(R.id.scroll_chat);
        content = findViewById(R.id.chat_content);
        groupWelcome = findViewById(R.id.group_welcome);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        tvStatus = findViewById(R.id.tv_chat_status);

        buildQuickQuestions();

        // Ô nhập xuống dòng khi dài nhưng bàn phím vẫn hiện nút "Gửi"
        etMessage.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etMessage.setHorizontallyScrolling(false);
        etMessage.setMaxLines(4);
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });
        btnSend.setOnClickListener(v -> sendTyped());

        // Theo prototype: quay lại -> Thông tin cá nhân
        setupBackButton(this::leave);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });

        // Ảnh đại diện trợ lý "nảy" nhẹ khi mở màn
        View avatar = findViewById(R.id.iv_bot_avatar);
        avatar.setScaleX(0.6f);
        avatar.setScaleY(0.6f);
        avatar.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
    }

    @Override
    protected void onDestroy() {
        stopTyping();
        super.onDestroy();
    }

    private void buildQuickQuestions() {
        LinearLayout list = findViewById(R.id.list_quick);
        for (int[] q : QUICK) {
            View chip = inflater.inflate(R.layout.item_quick_question, list, false);
            ((ImageView) chip.findViewById(R.id.iv_quick_icon)).setImageResource(q[0]);
            String text = getString(q[1]);
            ((TextView) chip.findViewById(R.id.tv_quick_text)).setText(text);
            chip.setOnClickListener(v -> send(text));
            list.addView(chip);
        }
    }

    // ---------- Gửi & nhận ----------

    private void sendTyped() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            VeGoBanner.show(this, R.string.chat_empty_message, VeGoBanner.Type.INFO);
            return;
        }
        etMessage.setText("");
        send(text);
    }

    private void send(String text) {
        if (waiting) return;
        if (!started) {
            started = true;
            groupWelcome.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> groupWelcome.setVisibility(View.GONE)).start();
            TextView time = (TextView) inflater.inflate(R.layout.item_chat_time, content, false);
            time.setText(getString(R.string.chat_today, now()));
            content.addView(time);
        }
        addUserMessage(text);

        waiting = true;
        btnSend.setAlpha(0.5f);
        showTyping();
        // Trả lời ngay trên máy (offline). Hơi chờ một nhịp cho tự nhiên, câu dài chờ lâu hơn chút.
        long delay = Math.min(1400, 650 + text.length() * 8L);
        content.postDelayed(() -> {
            if (isFinishing()) return;
            hideTyping();
            addBotReply(SupportAssistant.reply(text));
            waiting = false;
            btnSend.setAlpha(1f);
        }, delay);
    }

    private void addUserMessage(String text) {
        View row = inflater.inflate(R.layout.item_chat_user, content, false);
        ((TextView) row.findViewById(R.id.tv_user_message)).setText(text);
        ((TextView) row.findViewById(R.id.tv_user_time)).setText(getString(R.string.chat_you_time, now()));
        appendAnimated(row);
    }

    private void addBotReply(SupportAssistant.Reply reply) {
        View row = inflater.inflate(R.layout.item_chat_bot, content, false);
        LinearLayout bubble = row.findViewById(R.id.bubble);

        if (reply.title != null) {
            TextView title = (TextView) inflater.inflate(R.layout.item_chat_title, bubble, false);
            title.setText(reply.title);
            bubble.addView(title);
        }
        for (String p : reply.paragraphs) {
            TextView para = (TextView) inflater.inflate(R.layout.item_chat_paragraph, bubble, false);
            para.setText(p);
            bubble.addView(para);
        }
        for (Ticket t : reply.tickets) bubble.addView(miniTicket(bubble, t));
        if (reply.contactButton) {
            View contact = inflater.inflate(R.layout.item_chat_contact, bubble, false);
            contact.setOnClickListener(v -> {
                contact.setEnabled(false);
                contact.setAlpha(0.6f);
                addEscalationCard();
            });
            bubble.addView(contact);
        }

        TextView source = row.findViewById(R.id.tv_source);
        if (reply.source != null) {
            source.setText(reply.source);
            source.setVisibility(View.VISIBLE);
        }
        setupFeedback(row);
        appendAnimated(row);

        if (reply.escalate) addEscalationCard();
    }

    /** Figma "Vé thu nhỏ": tóm tắt vé ngay trong chat, bấm để mở Vé điện tử. */
    private View miniTicket(LinearLayout parent, Ticket t) {
        View card = inflater.inflate(R.layout.view_chat_ticket, parent, false);
        ((TextView) card.findViewById(R.id.tv_mini_operator)).setText(t.operator);
        ((TextView) card.findViewById(R.id.tv_mini_route)).setText(t.route());
        // Figma: giờ đi 22sp đậm, ngày 12sp thường màu xám
        String time = t.departTime();
        android.text.SpannableString depart = new android.text.SpannableString(time + " · " + Format.date(t.date));
        int from = time.length();
        int end = depart.length();
        int flags = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        depart.setSpan(new android.text.style.RelativeSizeSpan(12f / 22f), from, end, flags);
        depart.setSpan(new android.text.style.ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.vego_text_secondary)), from, end, flags);
        android.graphics.Typeface regular = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.inter_regular);
        if (regular != null) depart.setSpan(new android.text.style.TypefaceSpan(regular), from, end, flags);
        ((TextView) card.findViewById(R.id.tv_mini_depart)).setText(depart);
        int offset = t.arriveDayOffset();
        ((TextView) card.findViewById(R.id.tv_mini_arrive)).setText(getString(R.string.chat_ticket_arrive,
                t.arriveTime() + (offset > 0 ? " +" + offset + " ngày" : "")));
        ((TextView) card.findViewById(R.id.tv_mini_code)).setText(t.code);
        ((TextView) card.findViewById(R.id.tv_mini_seat)).setText("Ghế " + t.seat);
        ((TextView) card.findViewById(R.id.tv_mini_passenger)).setText(
                getString(R.string.chat_ticket_passenger, t.passengerName));
        // Theo prototype: "Xem vé điện tử" -> Vé điện tử
        card.findViewById(R.id.btn_mini_view).setOnClickListener(v ->
                startActivity(ETicketActivity.intent(this, t.code)));
        return card;
    }

    /** Hữu ích / Không hữu ích: chọn một trong hai, chọn lại thì bỏ chọn. */
    private void setupFeedback(View row) {
        View up = row.findViewById(R.id.btn_helpful);
        View down = row.findViewById(R.id.btn_not_helpful);
        up.setOnClickListener(v -> {
            boolean select = !up.isSelected();
            styleFeedback(row, true, select);
            styleFeedback(row, false, false);
            if (select) VeGoBanner.show(this, R.string.chat_thanks_helpful, VeGoBanner.Type.SUCCESS);
        });
        down.setOnClickListener(v -> {
            boolean select = !down.isSelected();
            styleFeedback(row, false, select);
            styleFeedback(row, true, false);
            if (select) VeGoBanner.show(this, R.string.chat_thanks_not_helpful, VeGoBanner.Type.INFO);
        });
    }

    private void styleFeedback(View row, boolean helpful, boolean selected) {
        View button = row.findViewById(helpful ? R.id.btn_helpful : R.id.btn_not_helpful);
        ImageView icon = row.findViewById(helpful ? R.id.iv_helpful : R.id.iv_not_helpful);
        TextView label = row.findViewById(helpful ? R.id.tv_helpful : R.id.tv_not_helpful);
        int color = ContextCompat.getColor(this, selected ? R.color.vego_primary : R.color.vego_text_secondary);
        button.setSelected(selected);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        label.setTextColor(color);
    }

    // ---------- Chuyển nhân viên ----------

    private void addEscalationCard() {
        View card = inflater.inflate(R.layout.view_chat_escalation, content, false);
        card.findViewById(R.id.btn_call_hotline).setOnClickListener(v -> confirmCall());
        View request = card.findViewById(R.id.btn_send_request);
        request.setOnClickListener(v -> confirmRequest(card));
        appendAnimated(card);
    }

    private void confirmCall() {
        String number = getString(R.string.chat_hotline_number);
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(R.string.chat_hotline_title)
                .message(getString(R.string.chat_hotline_message, number))
                .primary(R.string.chat_hotline_call, () -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.replace(" ", ""))));
                    } catch (ActivityNotFoundException e) {
                        VeGoBanner.show(this, getString(R.string.chat_no_dialer, number), VeGoBanner.Type.WARNING);
                    }
                })
                .secondary(R.string.action_cancel, null)
                .show();
    }

    private void confirmRequest(View card) {
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.INFO)
                .title(R.string.chat_request_title)
                .message(getString(R.string.chat_request_message, UserSession.get().getPhone()))
                .primary(R.string.chat_request_confirm, () -> {
                    String code = String.format(Locale.ROOT, "HT%04d", (int) (System.currentTimeMillis() % 10000));
                    markRequestSent(card);
                    VeGoDialog.with(this)
                            .type(VeGoDialog.Type.SUCCESS)
                            .title(R.string.chat_request_done_title)
                            .message(getString(R.string.chat_request_done_message, code))
                            .primary(R.string.action_ok, null)
                            .show();
                })
                .secondary(R.string.action_cancel, null)
                .show();
    }

    private void markRequestSent(View card) {
        View request = card.findViewById(R.id.btn_send_request);
        request.setEnabled(false);
        request.setClickable(false);
        ((TextView) card.findViewById(R.id.tv_send_request)).setText(R.string.chat_request_sent);
        setIcon(card.findViewById(R.id.iv_send_request), R.drawable.ic_circle_check);
    }

    private static void setIcon(ImageView view, @DrawableRes int icon) {
        view.setImageResource(icon);
        ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(0xFFFFFFFF));
    }

    // ---------- Hiệu ứng "đang trả lời" ----------

    private void showTyping() {
        tvStatus.setText(R.string.chat_subtitle_typing);
        typingView = inflater.inflate(R.layout.item_chat_typing, content, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        content.addView(typingView, lp);
        int[] dots = { R.id.dot_1, R.id.dot_2, R.id.dot_3 };
        for (int i = 0; i < dots.length; i++) {
            ObjectAnimator a = ObjectAnimator.ofFloat(typingView.findViewById(dots[i]), View.ALPHA, 0.25f, 1f);
            a.setDuration(420);
            a.setStartDelay(140L * i);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.start();
            typingAnims.add(a);
        }
        scrollToBottom();
    }

    private void hideTyping() {
        stopTyping();
        if (typingView != null) content.removeView(typingView);
        typingView = null;
        tvStatus.setText(R.string.chat_subtitle);
    }

    private void stopTyping() {
        for (ValueAnimator a : typingAnims) a.cancel();
        typingAnims.clear();
    }

    // ---------- Tiện ích ----------

    private void appendAnimated(View view) {
        content.addView(view);
        view.setAlpha(0f);
        view.setTranslationY(16 * getResources().getDisplayMetrics().density);
        view.animate().alpha(1f).translationY(0f).setDuration(240).start();
        scrollToBottom();
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.smoothScrollTo(0, content.getBottom()));
    }

    private static String now() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** Đã trò chuyện thì hỏi lại trước khi rời (nội dung chat không được lưu). */
    private void leave() {
        if (!started) {
            finish();
            return;
        }
        VeGoDialog.with(this)
                .type(VeGoDialog.Type.WARNING)
                .title(R.string.chat_leave_title)
                .message(R.string.chat_leave_message)
                .primary(R.string.chat_leave_confirm, this::finish)
                .secondary(R.string.chat_leave_stay, null)
                .show();
    }
}
