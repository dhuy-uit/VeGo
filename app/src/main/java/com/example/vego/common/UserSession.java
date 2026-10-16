package com.example.vego.common;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.Locale;

/**
 * Thông tin người dùng đang đăng nhập, lưu tạm trong bộ nhớ.
 * Bước prototype chưa có backend nên dữ liệu mẫu lấy theo Figma;
 * khi nối REST API, lớp này sẽ được thay bằng dữ liệu từ server.
 */
public final class UserSession {

    private static final UserSession INSTANCE = new UserSession();

    public static UserSession get() {
        return INSTANCE;
    }

    private String fullName = "Nguyễn Minh Anh";
    private String phone = "090 123 4567";
    private String email = "minhanh@gmail.com";
    private final Calendar birthDate = Calendar.getInstance();
    @Nullable private Uri avatarUri;
    /** Mật khẩu mẫu để thử chức năng Đổi mật khẩu (prototype, chưa mã hóa). */
    private String password = "vego123456";

    private UserSession() {
        birthDate.clear();
        birthDate.set(1998, Calendar.MAY, 12);
    }

    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Calendar getBirthDate() { return (Calendar) birthDate.clone(); }
    @Nullable public Uri getAvatarUri() { return avatarUri; }
    public String getPassword() { return password; }

    public void update(String fullName, String phone, String email, Calendar birthDate, @Nullable Uri avatarUri) {
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.birthDate.setTimeInMillis(birthDate.getTimeInMillis());
        this.avatarUri = avatarUri;
    }

    public void setPassword(String password) { this.password = password; }

    /** Tên gọi: bỏ họ, vd "Nguyễn Minh Anh" -> "Minh Anh". */
    public String getGivenName() {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 1) return fullName.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** Chữ viết tắt cho ảnh đại diện, vd "Nguyễn Minh Anh" -> "MA". */
    public String getInitials() {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String first = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
        String last = parts[parts.length - 1];
        String initials = parts.length >= 2
                ? first.substring(0, 1) + last.substring(0, 1)
                : last.substring(0, 1);
        return initials.toUpperCase(new Locale("vi", "VN"));
    }

    public static String formatDate(Calendar date) {
        return String.format(Locale.ROOT, "%02d/%02d/%d",
                date.get(Calendar.DAY_OF_MONTH),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.YEAR));
    }
}
