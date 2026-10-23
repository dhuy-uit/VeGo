# VéGo Backend

REST API cho app đặt vé xe khách VéGo: **xác thực**, **hồ sơ người dùng**, **trang chủ**.

- Spring Boot 3.5 · Java 21 · PostgreSQL 16 (tự host trong Docker) · Flyway · JWT
- Tài liệu API tự sinh: `/swagger-ui.html`
- Bộ test Postman: `postman/` (54 request, ~200 assertion), chạy tự động trên GitHub Actions mỗi lần push

## Cấu trúc

```
backend/
├── src/main/java/com/vego/backend/
│   ├── auth/       Đăng ký, đăng nhập, refresh token xoay vòng, đăng xuất, quên/đặt lại mật khẩu
│   ├── user/       Hồ sơ /users/me, ảnh đại diện, đổi mật khẩu
│   ├── home/       Trang chủ: khuyến mãi, tuyến phổ biến, chuyến gợi ý
│   ├── trip/       Tìm chuyến, sơ đồ ghế (SeatInventory), bến xe, gợi ý "Dành riêng cho bạn"
│   ├── booking/    Giữ ghế, mã giảm giá (khuyến mãi + voucher Ví xu)
│   ├── payment/    MoMo / VNPay sandbox, IPN có kiểm chữ ký, chế độ mô phỏng
│   ├── ticket/     Vé của tôi: hủy vé hoàn tiền, đánh giá, QR có chữ ký, job nhắc lịch / mời đánh giá
│   ├── notification/  Thông báo trong app
│   ├── wallet/     Ví xu: sổ xu, điểm danh 7 ngày, game, đổi voucher, quà chào mừng
│   ├── security/   JWT filter, cấu hình Spring Security
│   ├── common/     Định dạng lỗi chung, tiện ích
│   └── seed/       Tài khoản mẫu + lịch sử mẫu (vé, xu, thông báo) cho tài khoản demo
├── src/main/resources/
│   ├── db/migration/   V1 tạo bảng, V2 dữ liệu mẫu, V3 đặt vé + thanh toán, V4 vé / thông báo / Ví xu
│   └── static/         Trang đặt lại mật khẩu (mở từ link trong email)
├── postman/        Collection + environment Local/Azure + ảnh test
├── deploy/         Script tạo máy ảo Azure và cài server
├── Dockerfile, docker-compose.yml, Caddyfile
```

## API

Tiền tố `/api/v1`. Lỗi luôn có dạng:

```json
{ "status": 409, "code": "ACCOUNT_EXISTS", "message": "Số điện thoại đã được đăng ký",
  "fieldErrors": { "phone": "Số điện thoại đã được đăng ký" }, "path": "/api/v1/auth/register" }
```

| Phương thức | Đường dẫn | Token | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | – | Đăng ký `{fullName, phone, email, password}` → 201 hồ sơ |
| POST | `/auth/login` | – | `{account (SĐT hoặc email), password, device?}` → `{accessToken, refreshToken, expiresIn, user}` |
| POST | `/auth/refresh` | – | `{refreshToken}` → cặp token mới; token cũ bị vô hiệu, dùng lại token cũ thì cả phiên bị thu hồi |
| POST | `/auth/logout` | – | `{refreshToken}` → 204 |
| POST | `/auth/forgot-password` | – | `{account}` → 202, gửi link đặt lại mật khẩu qua email (hiệu lực 15 phút) |
| POST | `/auth/reset-password` | – | `{token, newPassword}` (trang `/reset-password.html` gọi API này) |
| GET | `/users/me` | ✔ | Hồ sơ của tôi |
| PUT | `/users/me` | ✔ | `{fullName, phone, email, birthDate?}` |
| POST | `/users/me/avatar` | ✔ | multipart, trường `file`, JPG/PNG ≤ 5 MB |
| DELETE | `/users/me/avatar` | ✔ | Xóa ảnh đại diện |
| PUT | `/users/me/password` | ✔ | `{currentPassword, newPassword}` → cặp token mới, các máy khác bị đăng xuất |
| GET | `/home` | tùy chọn | Lời chào, ô tìm kiếm mặc định, khuyến mãi, tuyến phổ biến, chuyến gợi ý |
| GET | `/files/avatars/{name}` | – | Ảnh đại diện |
| POST | `/bookings` | ✔ | Giữ ghế 5 phút `{tripId, travelDate, seat, passengerName, passengerPhone, pickupLabel?, dropoffLabel?}` |
| GET | `/bookings`, `/bookings/{code}` | ✔ | Đơn của tôi / một đơn (trạng thái HOLDING, PAID, EXPIRED, CANCELLED) |
| POST / DELETE | `/bookings/{code}/voucher` | ✔ | Áp / bỏ mã giảm giá `{code}` |
| POST | `/bookings/{code}/cancel` | ✔ | Hủy giữ chỗ |
| POST | `/bookings/{code}/payments` | ✔ | `{method: MOMO \| VNPAY \| CARD}` → `payUrl` để app mở |
| GET | `/vouchers` | tùy chọn | Mã giảm giá đang có (đăng nhập thì kèm voucher Ví xu của mình, `source: WALLET`) |
| GET | `/trips/search?origin=&destination=&date=` | – | Tìm chuyến; gõ không dấu / "HCM" vẫn khớp. Lọc tùy chọn: `operator`, `busType`, `slot` (0–3), `minPrice`, `maxPrice`; `sort` = RECOMMENDED \| DEPARTURE \| PRICE \| RATING |
| GET | `/trips/{id}?date=` | – | Chi tiết chuyến, số ghế trống theo ngày |
| GET | `/trips/{id}/seats?date=` | – | Sơ đồ ghế: `decks[].rows[][]{code, available}`, `taken`, `seatsLeft` |
| GET | `/trips/{id}/stops` | – | Điểm đón / trả (bến chính của chuyến đứng đầu) |
| GET | `/stations?city=`, `/cities` | – | Bến xe theo thành phố, các thành phố có tuyến |
| GET | `/suggestions?date=` | tùy chọn | Gợi ý theo chuyến đã đi gần nhất (cùng tuyến, nhà xe, buổi); khách: chuyến đánh giá cao |
| GET | `/tickets`, `/tickets/{code}` | ✔ | Vé đã thanh toán: `status` UPCOMING / COMPLETED / CANCELLED, số tiền hoàn nếu hủy lúc này, `qrData` |
| POST | `/tickets/{code}/cancel` | ✔ | Hủy vé: trước giờ đi ≥ 24 giờ hoàn 80%, sát giờ 50%; thu hồi xu đã cộng |
| POST | `/tickets/{code}/review` | ✔ | `{rating 1–5, tags[CLEAN, ONTIME, STAFF], comment?}` sau khi xe đã chạy |
| GET | `/notifications`, `/notifications/unread-count` | ✔ | Thông báo (mới nhất trước) + số chưa đọc |
| POST | `/notifications/{id}/read`, `/notifications/read-all` | ✔ | Đánh dấu đã đọc |
| GET | `/wallet` | ✔ | Số dư xu, lượt chơi còn lại, kỷ lục, lịch sử giao dịch |
| GET / POST | `/wallet/check-in` | ✔ | Chu kỳ điểm danh 7 ngày (20 xu/ngày, ngày 7: 50 xu) / điểm danh hôm nay |
| POST | `/wallet/game/start`, `/wallet/game/{playId}/finish` | ✔ | 2 lượt/ngày; `{score}` → xu (10 điểm = 1 xu, tối đa 100) |
| GET | `/wallet/vouchers` | ✔ | Voucher đổi được (V20 / V50 / V100) + voucher của tôi |
| POST | `/wallet/vouchers/{offerId}/redeem` | ✔ | Đổi xu lấy mã `XU20K-xxxx`, nhập ở bước thanh toán |
| GET | `/payments/vnpay/ipn`, `/payments/vnpay/return` | – | VNPay gọi về (kiểm tra chữ ký HMAC-SHA512) |
| POST / GET | `/payments/momo/ipn`, `/payments/momo/return` | – | MoMo gọi về (kiểm tra chữ ký HMAC-SHA256) |

Token: `Authorization: Bearer <accessToken>`. Access token sống 15 phút, refresh token 30 ngày.
Bảo mật: mật khẩu BCrypt; chỉ lưu SHA-256 của refresh token / mã khôi phục; sai mật khẩu 5 lần khóa đăng nhập 15 phút;
ảnh tải lên kiểm tra theo nội dung tệp; quên mật khẩu không tiết lộ tài khoản có tồn tại hay không.

Tài khoản mẫu: **0901234567 / vego123456** (Nguyễn Minh Anh) — có sẵn 1 vé đã đi (chưa đánh giá), 1 vé đã hủy,
1 vé sắp đi, 1.250 xu, chuỗi điểm danh 3 ngày, 1 voucher và thông báo mẫu.

Nghiệp vụ tự động:
- Thanh toán thành công → cộng xu (1.000đ = 1 xu), đánh dấu voucher Ví xu đã dùng, thông báo "Vé đã được xác nhận".
- Tài khoản mới → tặng 500 xu + thông báo mã CHAOBANMOI.
- Mỗi 5 phút: nhắc lịch các vé còn dưới 3 giờ nữa chạy; mời đánh giá khi chuyến đã tới nơi.
- Sơ đồ ghế giả lập ghế đã bán ở quầy (`total_seats - seats_left`, cố định theo chuyến + ngày);
  tắt bằng `BOOKING_PRESOLD_SEATS=false`.

## Server đang chạy (Azure)

Backend chạy trên máy ảo Azure, app Android gọi API qua Internet:

- API: **https://vego-api-3g15i.eastasia.cloudapp.azure.com**
- Tài liệu API: https://vego-api-3g15i.eastasia.cloudapp.azure.com/swagger-ui.html
- Trạng thái: https://vego-api-3g15i.eastasia.cloudapp.azure.com/actuator/health

App lấy địa chỉ này từ khóa `vego.apiBaseUrl` trong `gradle.properties` (mặc định đang trỏ tới server Azure),
nên chỉ cần bấm Run trong Android Studio là app gọi server thật.

## (Tùy chọn) Chạy thử backend trên máy cá nhân khi phát triển

Chỉ dùng để sửa/thử code backend trước khi đưa lên server, **không phải cách app chạy khi nộp bài**.
Cần Docker Desktop:

```bash
cd backend
docker compose up -d --build        # API ở http://localhost:8080
docker compose logs -f api          # xem log (link quên mật khẩu in ở đây khi chưa cấu hình email)
```

Muốn app trên máy ảo gọi bản local này thì thêm vào `local.properties` (không đưa lên GitHub):
`vego.apiBaseUrl=http://10.0.2.2:8080/` — xóa dòng đó đi là app quay lại gọi server Azure.

## Thanh toán (sandbox)

- **MoMo**: dùng tài khoản test dùng chung của MoMo (`test-payment.momo.vn`), không cần đăng ký.
  Trang MoMo cho chọn quét QR bằng app MoMo test, thẻ ATM hoặc thẻ quốc tế test.
- **VNPay / Thẻ ngân hàng**: cần `VNPAY_TMN_CODE` + `VNPAY_HASH_SECRET` (đăng ký tại sandbox.vnpayment.vn/devreg)
  trong `.env` trên server. Thẻ test: NCB `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
  IPN URL khai báo trong trang quản lý merchant: `https://<server>/api/v1/payments/vnpay/ipn`.
- **Mô phỏng**: `PAYMENT_SIMULATE=true` cho mọi phương thức đi qua trang mô phỏng của server (dùng khi demo mà cổng sandbox lỗi, và trên CI).
- Mã giảm giá mẫu: `VEGO10` (10%, tối đa 30.000đ, 3 lần), `VEGIN15` (15% tuyến TP.HCM – Đà Lạt), `BIENXANH20` (20% tuyến TP.HCM – Nha Trang), `CHAOBANMOI` (50.000đ cho đơn từ 200.000đ, 1 lần).
- Kết quả thanh toán chỉ được ghi nhận khi chữ ký của cổng hợp lệ và đúng số tiền; IPN gửi lặp lại không bị xử lý 2 lần.

## Kiểm thử bằng Postman

1. Postman → **Import** 3 tệp trong `postman/`: collection `VeGo.postman_collection.json`,
   environment `VeGo-Local` và `VeGo-Azure`.
2. Chọn environment (góc phải trên). Với Azure, sửa `baseUrl` thành địa chỉ server.
3. Hai request tải ảnh dùng tệp `avatar.png` và `not-image.txt` trong thư mục `postman/`
   (Settings → General → Working directory trỏ tới thư mục này, hoặc chọn lại tệp trong tab Body).
4. Chuột phải collection → **Run collection** → Run. Chạy cả collection theo thứ tự vì các request truyền token cho nhau.

Hai request "Đặt lại mật khẩu" chỉ chạy khi server bật `EXPOSE_RESET_TOKEN=true` (máy dev/CI);
trên server thật chúng được bỏ qua vì mã khôi phục chỉ gửi qua email.

Chạy bằng dòng lệnh: `npx newman run postman/VeGo.postman_collection.json -e postman/VeGo-Local.postman_environment.json --working-dir postman`

## Triển khai lên Azure

Một máy ảo Ubuntu (Standard_B1s) chạy Docker Compose: PostgreSQL + API + Caddy (tự cấp HTTPS Let's Encrypt
cho tên miền `*.cloudapp.azure.com` miễn phí của Azure). CSDL không mở cổng ra ngoài.

Mở **Azure Cloud Shell** (biểu tượng `>_` trên portal.azure.com, chọn Bash) và chạy:

```bash
curl -fsSL https://raw.githubusercontent.com/dhuy-uit/VeGo/main/backend/deploy/azure-create-vm.sh | bash
```

Script tạo resource group `vego-rg`, máy ảo `vego-vm`, mở cổng 80/443; máy ảo tự cài Docker, clone repo,
sinh mật khẩu CSDL + JWT_SECRET ngẫu nhiên (`/opt/vego/backend/.env`) và chạy. Mất ~10–15 phút.

Cập nhật code mới: `ssh vego@<địa-chỉ> 'bash /opt/vego/backend/deploy/update.sh'`

Gửi email quên mật khẩu thật: thêm vào `/opt/vego/backend/.env` các biến `SPRING_MAIL_HOST=smtp.gmail.com`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (mật khẩu ứng dụng Gmail), `MAIL_FROM`, rồi chạy lại `update.sh`.
