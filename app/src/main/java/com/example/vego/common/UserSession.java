package com.example.vego.common;

import androidx.annotation.Nullable;

import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.Calendar;
import java.util.Locale;

/**
 * Người dùng đang đăng nhập. Dữ liệu lấy từ server (GET /api/v1/users/me hoặc kết quả đăng nhập)
 * và được lưu tạm trong máy để mở app là hiện ngay, sau đó làm mới từ server.
 */
public final class UserSession {

    private static final UserSession INSTANCE = new UserSession();

    public static UserSession get() {
        return INSTANCE;
    }

    @Nullable private ApiModels.User user;

    private UserSession() { }

    /** Nạp lại hồ sơ đã lưu trong máy (gọi khi mở app). */
    public void restore() {
        user = ApiClient.tokens().user();
    }

    /** Cập nhật hồ sơ mới nhận từ server và lưu lại. */
    public void set(@Nullable ApiModels.User user) {
        // Đổi sang tài khoản khác: bỏ vé / thông báo / ví của tài khoản cũ
        if (this.user != null && user != null && this.user.id != user.id) clearCaches();
        this.user = user;
        ApiClient.tokens().saveUser(user);
    }

    /** Đăng xuất / phiên hết hạn: xóa token và hồ sơ. */
    public void clear() {
        user = null;
        ApiClient.tokens().clear();
        clearCaches();
    }

    private static void clearCaches() {
        com.example.vego.data.TicketStore.get().clear();
        com.example.vego.data.NotificationStore.get().clear();
        com.example.vego.data.CoinWallet.get().clear();
    }

    public boolean isLoggedIn() {
        return ApiClient.tokens().isLoggedIn();
    }

    @Nullable
    public ApiModels.User user() {
        return user;
    }

    public String getFullName() {
        return user == null || user.fullName == null ? "" : user.fullName;
    }

    /** Số điện thoại hiển thị, vd "090 123 4567". */
    public String getPhone() {
        return formatPhone(getPhoneDigits());
    }

    public String getPhoneDigits() {
        return user == null || user.phone == null ? "" : user.phone;
    }

    public String getEmail() {
        return user == null || user.email == null ? "" : user.email;
    }

    /** Ngày sinh, null nếu chưa cập nhật. */
    @Nullable
    public Calendar getBirthDate() {
        return user == null ? null : parseIsoDate(user.birthDate);
    }

    @Nullable
    public String getAvatarUrl() {
        return user == null ? null : user.avatarUrl;
    }

    /** Tên gọi: bỏ họ, vd "Nguyễn Minh Anh" -> "Minh Anh". */
    public String getGivenName() {
        if (user != null && user.givenName != null && !user.givenName.isEmpty()) return user.givenName;
        return givenNameOf(getFullName());
    }

    /** Chữ viết tắt cho ảnh đại diện, vd "Nguyễn Minh Anh" -> "MA". */
    public String getInitials() {
        return initialsOf(getFullName());
    }

    // ---------- Tiện ích ----------

    public static String givenNameOf(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 1) return fullName.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    public static String initialsOf(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        String last = parts[parts.length - 1];
        String initials = parts.length >= 2
                ? parts[parts.length - 2].substring(0, 1) + last.substring(0, 1)
                : last.substring(0, 1);
        return initials.toUpperCase(new Locale("vi", "VN"));
    }

    /** "0901234567" -> "090 123 4567". */
    public static String formatPhone(String digits) {
        if (digits == null || digits.length() != 10) return digits == null ? "" : digits;
        return digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6);
    }

    public static String formatDate(Calendar date) {
        return String.format(Locale.ROOT, "%02d/%02d/%d",
                date.get(Calendar.DAY_OF_MONTH),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.YEAR));
    }

    /** Calendar -> "yyyy-MM-dd" (định dạng ngày của API). */
    public static String toIsoDate(Calendar date) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    /** "yyyy-MM-dd" -> Calendar, null nếu rỗng hoặc sai định dạng. */
    @Nullable
    public static Calendar parseIsoDate(@Nullable String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1,
                Integer.parseInt(iso.substring(8, 10)));
        return c;
    }
}
