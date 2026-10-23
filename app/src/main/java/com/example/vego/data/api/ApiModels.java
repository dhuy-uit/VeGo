package com.example.vego.data.api;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Các lớp dữ liệu gửi / nhận với REST API (Gson tự chuyển JSON <-> đối tượng).
 * Tên trường trùng với tên trong JSON của server.
 */
public final class ApiModels {

    private ApiModels() { }

    // ---------- Gửi lên ----------

    public static class LoginRequest {
        public final String account, password, device;

        public LoginRequest(String account, String password, String device) {
            this.account = account;
            this.password = password;
            this.device = device;
        }
    }

    public static class RegisterRequest {
        public final String fullName, phone, email, password;

        public RegisterRequest(String fullName, String phone, String email, String password) {
            this.fullName = fullName;
            this.phone = phone;
            this.email = email;
            this.password = password;
        }
    }

    public static class RefreshRequest {
        public final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class ForgotPasswordRequest {
        public final String account;

        public ForgotPasswordRequest(String account) {
            this.account = account;
        }
    }

    public static class UpdateProfileRequest {
        public final String fullName, phone, email;
        /** yyyy-MM-dd hoặc null. */
        @Nullable public final String birthDate;

        public UpdateProfileRequest(String fullName, String phone, String email, @Nullable String birthDate) {
            this.fullName = fullName;
            this.phone = phone;
            this.email = email;
            this.birthDate = birthDate;
        }
    }

    public static class ChangePasswordRequest {
        public final String currentPassword, newPassword, device;

        public ChangePasswordRequest(String currentPassword, String newPassword, String device) {
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
            this.device = device;
        }
    }

    public static class CreateBookingRequest {
        public final String tripId, travelDate, seat, passengerName, passengerPhone, pickupLabel, dropoffLabel;

        public CreateBookingRequest(String tripId, String travelDate, String seat, String passengerName,
                                    String passengerPhone, String pickupLabel, String dropoffLabel) {
            this.tripId = tripId;
            this.travelDate = travelDate;
            this.seat = seat;
            this.passengerName = passengerName;
            this.passengerPhone = passengerPhone;
            this.pickupLabel = pickupLabel;
            this.dropoffLabel = dropoffLabel;
        }
    }

    public static class CodeRequest {
        public final String code;

        public CodeRequest(String code) {
            this.code = code;
        }
    }

    public static class CreatePaymentRequest {
        /** MOMO | VNPAY | CARD */
        public final String method;

        public CreatePaymentRequest(String method) {
            this.method = method;
        }
    }

    // ---------- Nhận về ----------

    public static class User {
        public long id;
        public String fullName;
        public String givenName;
        /** 10 chữ số, vd "0901234567". */
        public String phone;
        public String email;
        /** yyyy-MM-dd, có thể null. */
        @Nullable public String birthDate;
        @Nullable public String avatarUrl;
        public String role;
    }

    public static class AuthResponse {
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public long expiresIn;
        public User user;
    }

    public static class MessageResponse {
        public String message;
    }

    /** Lỗi server trả về (xem ApiError ở backend). */
    public static class ApiError {
        public int status;
        public String code;
        public String message;
        @Nullable public Map<String, String> fieldErrors;

        public static ApiError of(int status, String code, String message) {
            ApiError e = new ApiError();
            e.status = status;
            e.code = code;
            e.message = message;
            return e;
        }

        public boolean is(String code) {
            return code.equals(this.code);
        }

        @Nullable
        public String field(String name) {
            return fieldErrors == null ? null : fieldErrors.get(name);
        }
    }

    public static class Home {
        public boolean loggedIn;
        @Nullable public String greetingName;
        public SearchDefaults searchDefaults;
        public List<Promotion> promotions;
        public List<PopularRoute> popularRoutes;
        @Nullable public SuggestedTrip suggestedTrip;
    }

    public static class SearchDefaults {
        public String origin;
        public String destination;
        /** yyyy-MM-dd */
        public String date;
    }

    public static class Promotion {
        public String code, badge, title, description;
        @Nullable public String note;
        @Nullable public String imageKey;
        public int discountPercent;
    }

    public static class PopularRoute {
        public int routeId;
        public String origin, destination;
        public int fromPrice;
        public int tripsPerDay;
    }

    public static class SuggestedTrip {
        public String reason;
        public TripDto trip;
    }

    public static class TripDto {
        public String id, operator, busName, busType;
        public double rating;
        /** yyyy-MM-dd */
        public String departureDate;
        public String departTime, arriveTime;
        public int arriveDayOffset, departMinutes, durationMinutes, price, seatsLeft, totalSeats;
        public String origin, destination;
        public String pickupStation, pickupAddress, dropoffStation, dropoffAddress;
    }

    /** Đơn đặt vé. status: HOLDING | PAID | EXPIRED | CANCELLED. */
    public static class Booking {
        public String code;
        public String status;
        public TripDto trip;
        public String travelDate;
        public String seat;
        public String passengerName, passengerPhone, pickupLabel, dropoffLabel;
        public int fare, discount, total;
        @Nullable public String voucherCode;
        @Nullable public String voucherTitle;
        @Nullable public String paymentMethod;
        public long holdRemainingSeconds;
        @Nullable public LastPayment lastPayment;

        public boolean is(String s) {
            return s.equals(status);
        }
    }

    public static class LastPayment {
        public String txnRef, provider, method, status;
        @Nullable public String message;
    }

    public static class Payment {
        public String txnRef, bookingCode, provider, method;
        public int amount;
        public String payUrl;
        /** WebView chuyển tới địa chỉ bắt đầu bằng resultUrl = cổng đã trả kết quả. */
        public String resultUrl;
    }

    public static class Voucher {
        public String code, title, description;
        @Nullable public String origin, destination;
    }
}
