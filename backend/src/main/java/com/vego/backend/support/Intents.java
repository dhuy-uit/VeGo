package com.vego.backend.support;

import java.util.regex.Pattern;

/**
 * Những câu hỏi KHÔNG đưa cho mô hình: dữ liệu riêng của người dùng (vé, xu) lấy thẳng từ CSDL nên luôn đúng
 * và không gửi ra ngoài; việc cần người xác minh thì chuyển nhân viên.
 */
public final class Intents {

    public enum Intent { GREETING, THANKS, ESCALATE, MY_TICKETS, MY_REFUND, MY_COINS, KNOWLEDGE }

    private static final Pattern GREETING = Pattern.compile(
            "^(xin chao|chao|chao ban|chao shop|hello|hi|alo|hey)( ban| ad| shop| vego| a| nhe)?$");
    private static final Pattern THANKS = Pattern.compile(
            "^(ok |oke |da |vang )?(cam on|thank|thanks|thank you)( ban| ad| shop| vego| nhe| nha| nhieu| a)*$");
    private static final Pattern ESCALATE = words("xac nhan hoan|hoan tien giup|duoc hoan chua|chua nhan duoc tien|"
            + "chua duoc hoan|chua thay tien hoan|bi tru tien|gap nhan vien|noi chuyen voi nhan vien|nguoi that|"
            + "khieu nai|de quen do|quen do tren xe");
    private static final Pattern OWN_TICKET = words("ve cua toi|ve cua minh|ve cua em|ve toi dat|ve minh dat|"
            + "chuyen cua toi|chuyen cua minh|kiem tra ve|ve sap di|chuyen sap toi|chuyen sap di|ma ve cua");
    private static final Pattern REFUND = words("huy|hoan");
    /** Có các từ này thì là hỏi chính sách / cách làm, không phải xem vé. */
    private static final Pattern POLICY = words("doi gio|doi chuyen|doi ve|doi ngay|doi ghe|hanh ly|thanh toan|"
            + "qr|danh gia|chinh sach|quy dinh|cach|lam sao|the nao");
    private static final Pattern MY_COINS = words("con bao nhieu xu|dang co bao nhieu xu|toi co bao nhieu xu|"
            + "minh co bao nhieu xu|em co bao nhieu xu|so du xu|xu cua toi|xu cua minh|so xu hien tai");

    private Intents() { }

    public static Intent detect(String question) {
        String q = TextSearch.normalize(question);
        if (GREETING.matcher(q).matches()) return Intent.GREETING;
        if (THANKS.matcher(q).matches()) return Intent.THANKS;
        if (ESCALATE.matcher(q).find()) return Intent.ESCALATE;
        if (OWN_TICKET.matcher(q).find()) {
            if (REFUND.matcher(q).find()) return Intent.MY_REFUND;
            if (!POLICY.matcher(q).find()) return Intent.MY_TICKETS;
        }
        if (MY_COINS.matcher(q).find()) return Intent.MY_COINS;
        return Intent.KNOWLEDGE;
    }

    private static Pattern words(String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b");
    }
}
