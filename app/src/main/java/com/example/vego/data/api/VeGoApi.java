package com.example.vego.data.api;

import com.example.vego.data.api.ApiModels.AuthResponse;
import com.example.vego.data.api.ApiModels.Booking;
import com.example.vego.data.api.ApiModels.CodeRequest;
import com.example.vego.data.api.ApiModels.CreateBookingRequest;
import com.example.vego.data.api.ApiModels.CreatePaymentRequest;
import com.example.vego.data.api.ApiModels.Payment;
import com.example.vego.data.api.ApiModels.Voucher;
import com.example.vego.data.api.ApiModels.ChangePasswordRequest;
import com.example.vego.data.api.ApiModels.ForgotPasswordRequest;
import com.example.vego.data.api.ApiModels.Home;
import com.example.vego.data.api.ApiModels.LoginRequest;
import com.example.vego.data.api.ApiModels.MessageResponse;
import com.example.vego.data.api.ApiModels.RefreshRequest;
import com.example.vego.data.api.ApiModels.RegisterRequest;
import com.example.vego.data.api.ApiModels.UpdateProfileRequest;
import com.example.vego.data.api.ApiModels.User;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Khai báo REST API của VéGo cho Retrofit. */
public interface VeGoApi {

    /** Header đánh dấu request không cần gắn token (AuthInterceptor sẽ bỏ header này đi). */
    String NO_AUTH = "X-No-Auth: true";

    // ---------- Xác thực ----------

    @Headers(NO_AUTH)
    @POST("api/v1/auth/register")
    Call<User> register(@Body RegisterRequest body);

    @Headers(NO_AUTH)
    @POST("api/v1/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @Headers(NO_AUTH)
    @POST("api/v1/auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest body);

    @Headers(NO_AUTH)
    @POST("api/v1/auth/logout")
    Call<Void> logout(@Body RefreshRequest body);

    @Headers(NO_AUTH)
    @POST("api/v1/auth/forgot-password")
    Call<MessageResponse> forgotPassword(@Body ForgotPasswordRequest body);

    // ---------- Hồ sơ ----------

    @GET("api/v1/users/me")
    Call<User> me();

    @PUT("api/v1/users/me")
    Call<User> updateProfile(@Body UpdateProfileRequest body);

    @Multipart
    @POST("api/v1/users/me/avatar")
    Call<User> uploadAvatar(@Part MultipartBody.Part file);

    @DELETE("api/v1/users/me/avatar")
    Call<User> deleteAvatar();

    @PUT("api/v1/users/me/password")
    Call<AuthResponse> changePassword(@Body ChangePasswordRequest body);

    // ---------- Trang chủ ----------

    /** Không bắt buộc đăng nhập; có token thì server chào theo tên. */
    @GET("api/v1/home")
    Call<Home> home();

    // ---------- Đặt vé & thanh toán ----------

    /** Giữ ghế 5 phút. */
    @POST("api/v1/bookings")
    Call<Booking> createBooking(@Body CreateBookingRequest body);

    @GET("api/v1/bookings/{code}")
    Call<Booking> getBooking(@Path("code") String code);

    @POST("api/v1/bookings/{code}/cancel")
    Call<Booking> cancelBooking(@Path("code") String code);

    @POST("api/v1/bookings/{code}/voucher")
    Call<Booking> applyVoucher(@Path("code") String code, @Body CodeRequest body);

    @DELETE("api/v1/bookings/{code}/voucher")
    Call<Booking> removeVoucher(@Path("code") String code);

    @POST("api/v1/bookings/{code}/payments")
    Call<Payment> createPayment(@Path("code") String code, @Body CreatePaymentRequest body);

    @GET("api/v1/vouchers")
    Call<List<Voucher>> vouchers();

    // ---------- Tìm chuyến (không cần đăng nhập) ----------

    @GET("api/v1/trips/search")
    Call<ApiModels.TripSearch> searchTrips(@Query("origin") String origin, @Query("destination") String destination,
                                           @Query("date") String date);

    @GET("api/v1/trips/{id}")
    Call<ApiModels.TripDto> trip(@Path("id") String tripId, @Query("date") String date);

    @GET("api/v1/trips/{id}/seats")
    Call<ApiModels.SeatMap> seatMap(@Path("id") String tripId, @Query("date") String date);

    @GET("api/v1/trips/{id}/stops")
    Call<ApiModels.TripStops> tripStops(@Path("id") String tripId);

    @GET("api/v1/stations")
    Call<List<ApiModels.Station>> stations(@Query("city") String city);

    /** 204 (body null) khi không có gợi ý. */
    @GET("api/v1/suggestions")
    Call<ApiModels.Suggestion> suggestions(@Query("date") String date);

    // ---------- Vé của tôi ----------

    @GET("api/v1/tickets")
    Call<List<ApiModels.Ticket>> tickets();

    @GET("api/v1/tickets/{code}")
    Call<ApiModels.Ticket> ticket(@Path("code") String code);

    @POST("api/v1/tickets/{code}/cancel")
    Call<ApiModels.Ticket> cancelTicket(@Path("code") String code);

    @POST("api/v1/tickets/{code}/review")
    Call<ApiModels.Ticket> reviewTicket(@Path("code") String code, @Body ApiModels.ReviewRequest body);

    // ---------- Thông báo ----------

    @GET("api/v1/notifications")
    Call<ApiModels.Inbox> notifications();

    @GET("api/v1/notifications/unread-count")
    Call<ApiModels.UnreadCount> unreadNotifications();

    @POST("api/v1/notifications/{id}/read")
    Call<ApiModels.NotificationItem> markNotificationRead(@Path("id") long id);

    @POST("api/v1/notifications/read-all")
    Call<Void> markAllNotificationsRead();

    // ---------- Ví xu ----------

    @GET("api/v1/wallet")
    Call<ApiModels.Wallet> wallet();

    @GET("api/v1/wallet/check-in")
    Call<ApiModels.CheckInStatus> checkInStatus();

    @POST("api/v1/wallet/check-in")
    Call<ApiModels.CheckInResult> checkIn();

    @POST("api/v1/wallet/game/start")
    Call<ApiModels.GameStart> startGame();

    @POST("api/v1/wallet/game/{playId}/finish")
    Call<ApiModels.GameResult> finishGame(@Path("playId") long playId, @Body ApiModels.ScoreRequest body);

    @GET("api/v1/wallet/vouchers")
    Call<ApiModels.WalletVouchers> walletVouchers();

    @POST("api/v1/wallet/vouchers/{offerId}/redeem")
    Call<ApiModels.Redeemed> redeemVoucher(@Path("offerId") String offerId);
}
