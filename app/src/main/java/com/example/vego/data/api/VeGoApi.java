package com.example.vego.data.api;

import com.example.vego.data.api.ApiModels.AuthResponse;
import com.example.vego.data.api.ApiModels.ChangePasswordRequest;
import com.example.vego.data.api.ApiModels.ForgotPasswordRequest;
import com.example.vego.data.api.ApiModels.Home;
import com.example.vego.data.api.ApiModels.LoginRequest;
import com.example.vego.data.api.ApiModels.MessageResponse;
import com.example.vego.data.api.ApiModels.RefreshRequest;
import com.example.vego.data.api.ApiModels.RegisterRequest;
import com.example.vego.data.api.ApiModels.UpdateProfileRequest;
import com.example.vego.data.api.ApiModels.User;

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
}
