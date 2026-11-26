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
│   ├── payment/    VNPay sandbox, IPN có kiểm chữ ký, chế độ mô phỏng
│   ├── ticket/     Vé của tôi: hủy vé hoàn tiền, đánh giá, QR có chữ ký, job nhắc lịch / mời đánh giá
│   ├── notification/  Thông báo trong app
│   ├── wallet/     Ví xu: sổ xu, điểm danh 7 ngày, game, đổi voucher, quà chào mừng
│   ├── security/   JWT filter, cấu hình Spring Security
│   ├── admin/      API web admin: xe, tài xế, tuyến / chuyến, lập lịch, thống kê, dữ liệu minh họa
│   ├── common/     Định dạng lỗi chung, tiện ích
│   └── seed/       Tài khoản mẫu + lịch sử mẫu (vé, xu, thông báo) cho tài khoản demo
├── src/main/resources/
│   ├── db/migration/   V1 tạo bảng, V2 dữ liệu mẫu, V3 đặt vé + thanh toán, V4 vé / thông báo / Ví xu,
│   │                   V5 xe / tài xế / phân công / lượt xem đề xuất
│   └── static/         Trang đặt lại mật khẩu; static/admin = web admin (HTML/CSS/JS thuần)
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
| POST | `/bookings/{code}/payments` | ✔ | `{method: VNPAY}` (CARD vẫn nhận, cũng đi qua VNPay) → `payUrl` để app mở |
| GET | `/vouchers` | tùy chọn | Mã giảm giá đang có (đăng nhập thì kèm voucher Ví xu của mình, `source: WALLET`) |
| GET | `/trips/search?origin=&destination=&date=` | – | Tìm chuyến; gõ không dấu / "HCM" vẫn khớp. Lọc tùy chọn: `operator`, `busType`, `slot` (0–3), `minPrice`, `maxPrice`; `sort` = RECOMMENDED \| DEPARTURE \| PRICE \| RATING |
| GET | `/trips/{id}?date=` | – | Chi tiết chuyến, số ghế trống theo ngày |
| GET | `/trips/{id}/seats?date=` | – | Sơ đồ ghế: `decks[].rows[][]{code, available}`, `taken`, `seatsLeft` |
| GET | `/trips/{id}/stops` | – | Điểm đón / trả (bến chính của chuyến đứng đầu) |
| GET | `/stations?city=`, `/cities`, `/routes` | – | Bến xe theo thành phố, các thành phố có tuyến, các cặp điểm đi → điểm đến đang bán (app cho chọn, không cho gõ) |
| GET | `/suggestions?date=` | tùy chọn | Gợi ý theo chuyến đã đi gần nhất (cùng tuyến, nhà xe, buổi); khách: chuyến đánh giá cao |
| GET | `/tickets`, `/tickets/{code}` | ✔ | Vé đã thanh toán: `status` UPCOMING / COMPLETED / CANCELLED, số tiền hoàn nếu hủy lúc này, `qrData` |
| POST | `/tickets/{code}/cancel` | ✔ | Hủy vé: trước giờ đi ≥ 24 giờ hoàn 80%, sát giờ 50%; thu hồi xu đã cộng |
| GET | `/tickets/{code}/exchange/trips?date=` | ✔ | Chuyến cùng tuyến, cùng giá vé trong ngày để đổi vé (409 nếu vé không còn đổi được) |
| POST | `/tickets/{code}/exchange` | ✔ | `{tripId, travelDate, seat}` đổi sang chuyến/ngày/ghế khác: miễn phí, mỗi vé 1 lần, còn ≥ 6 giờ trước giờ đi; QR đổi theo |
| POST | `/tickets/{code}/review` | ✔ | `{rating 1–5, tags[CLEAN, ONTIME, STAFF], comment?}` sau khi xe đã chạy |
| GET | `/notifications`, `/notifications/unread-count` | ✔ | Thông báo (mới nhất trước) + số chưa đọc |
| POST | `/notifications/{id}/read`, `/notifications/read-all` | ✔ | Đánh dấu đã đọc |
| GET | `/wallet` | ✔ | Số dư xu, lượt chơi còn lại, kỷ lục, lịch sử giao dịch |
| GET / POST | `/wallet/check-in` | ✔ | Chu kỳ điểm danh 7 ngày (20 xu/ngày, ngày 7: 50 xu) / điểm danh hôm nay |
| POST | `/wallet/game/start`, `/wallet/game/{playId}/finish` | ✔ | 2 lượt/ngày; `{score}` → xu (10 điểm = 1 xu, tối đa 100) |
| GET | `/wallet/vouchers` | ✔ | Voucher đổi được (V20 / V50 / V100) + voucher của tôi |
| POST | `/wallet/vouchers/{offerId}/redeem` | ✔ | Đổi xu lấy mã `XU20K-xxxx`, nhập ở bước thanh toán |
| GET | `/payments/vnpay/ipn`, `/payments/vnpay/return` | – | VNPay gọi về (kiểm tra chữ ký HMAC-SHA512) |

Token: `Authorization: Bearer <accessToken>`. Access token sống 15 phút, refresh token 30 ngày.
Bảo mật: mật khẩu BCrypt; chỉ lưu SHA-256 của refresh token / mã khôi phục; sai mật khẩu 5 lần khóa đăng nhập 15 phút;
ảnh tải lên kiểm tra theo nội dung tệp; quên mật khẩu không tiết lộ tài khoản có tồn tại hay không.

Tài khoản mẫu: **0901234567 / vego123456** (Nguyễn Minh Anh) — có sẵn 1 vé đã đi (chưa đánh giá), 1 vé đã hủy,
1 vé sắp đi, 808 xu, chuỗi điểm danh 3 ngày, 1 voucher và thông báo mẫu.

Nghiệp vụ tự động:
- Thanh toán thành công → cộng xu (1.000đ = 1 xu), đánh dấu voucher Ví xu đã dùng, thông báo "Vé đã được xác nhận".
- Tài khoản mới → tặng 500 xu + thông báo mã CHAOBANMOI.
- Mỗi 5 phút: nhắc lịch các vé còn dưới 3 giờ nữa chạy; mời đánh giá khi chuyến đã tới nơi.
- Sơ đồ ghế giả lập ghế đã bán ở quầy (`total_seats - seats_left`, cố định theo chuyến + ngày);
  tắt bằng `BOOKING_PRESOLD_SEATS=false`.

## Trợ lý hỗ trợ (RAG + Gemini)

`POST /api/v1/support/chat` (cần token) `{message, history?: [{role: user|bot, text}]}` →
`{title?, paragraphs[], source?, contactButton, escalate, tickets[], mode, action?, suggestions[]}`.

1. Câu hỏi về vé / số xu của người dùng và việc cần nhân viên được xử lý ngay trên server, không gửi cho mô hình.
2. Câu hỏi còn lại: tìm đoạn liên quan trong `src/main/resources/knowledge/*.md` + bến xe và mã giảm giá trong CSDL,
   kết hợp BM25 (bỏ dấu) và embedding `gemini-embedding-2` (lưu ở bảng `kb_embeddings`, so cosine trong server).
3. `gemini-3.5-flash-lite` chỉ được trả lời từ các đoạn đó và phải chỉ ra nguồn; không có nguồn thì chuyển nhân viên.
4. Không có khóa / Gemini lỗi → trả nguyên đoạn tài liệu khớp (`mode: DOCS`).

### Trợ lý thao tác (đặt / hủy / đổi vé qua chat)

Khi có khóa Gemini, câu có ý muốn tìm chuyến, đặt, hủy hoặc đổi vé (hoặc đang giữa một cuộc đặt vé dở) được đưa cho
`SupportAgent` dùng function calling của Gemini, trước khi rơi về tài liệu (nếu mô hình thấy chỉ là câu hỏi chính sách
thì gọi `defer_to_knowledge`):

| Công cụ | Việc làm | Thay đổi dữ liệu? |
|---|---|---|
| `search_trips`, `get_available_seats`, `list_my_tickets`, `list_exchange_options` | Tra cứu qua `TripService` / `TicketService` | Không (chỉ vé của đúng người đang chat) |
| `propose_booking`, `propose_cancel`, `propose_exchange` | Kiểm tra điều kiện rồi tạo **đề xuất chờ xác nhận** | Không |

- Mô hình không tự đặt / hủy / đổi được gì. Câu trả lời kèm `action {token, kind, title, summary, confirmLabel}`; app hiện
  nút **Xác nhận** và chỉ khi khách bấm, app gọi `POST /api/v1/support/actions/{token}/confirm` thì
  `BookingService.create` / `TicketService.cancel` / `TicketService.exchange` mới chạy (đúng các quy tắc nghiệp vụ cũ).
  `DELETE /api/v1/support/actions/{token}` là nút Bỏ qua.
- Token gắn với một người dùng, dùng một lần, hết hạn sau 10 phút (lỗi `410 ACTION_EXPIRED`).
- Đặt vé chỉ **giữ ghế 5 phút** (`kind = BOOK` trả `booking`); app mở màn Thanh toán, khách tự trả qua VNPay.
- `suggestions[]` là các gợi ý bấm để gửi (chọn chuyến); `history[].mode = "AGENT"` báo câu trả lời trước thuộc một cuộc
  thao tác đang dở để các câu ngắn kế tiếp ("ngày mai", "ghế A05") vẫn tới trợ lý.
- Mỗi tin nhắn tối đa 6 vòng gọi công cụ. Không có khóa / Gemini lỗi thì bỏ qua trợ lý thao tác và trả lời theo tài liệu.

`mode`: `RULE`, `AI`, `DOCS`, `NONE`, `AGENT`. Đổi chính sách thì sửa tệp trong `knowledge/` (và code nghiệp vụ tương ứng).
Bật Gemini: thêm `GEMINI_API_KEY=...` vào `.env` rồi chạy lại `update.sh`.
**Máy chủ phải nằm ở vùng Gemini API hỗ trợ** (Hong Kong / Azure East Asia không được hỗ trợ).

## Web admin

Web admin là trang HTML/CSS/JavaScript thuần (không cần build) nằm trong `src/main/resources/static/admin`,
chạy cùng server API: **https://vego-api-dhfhi.indonesiacentral.cloudapp.azure.com/admin**.
Giao diện theo Figma (section 08–14): đăng nhập / quên mật khẩu, Tổng quan doanh thu, Quản lý xe, Tuyến & chuyến,
Lập lịch chuyến, Tài xế, Top tuyến bán chạy, Báo cáo đề xuất, Đổi mật khẩu.

- Đăng nhập bằng tài khoản `ADMIN_EMAIL` / `ADMIN_PASSWORD` trong `.env` trên server (tạo một lần khi server khởi động
  và chưa có admin). Mọi API `/api/v1/admin/**` chỉ tài khoản role `ADMIN` gọi được.
- Chuyến cố định chạy mỗi ngày; "Lập lịch chuyến" phân công xe + tài xế cho một chuyến trong một ngày,
  hoặc tạo **chuyến tăng cường** chỉ chạy ngày đó (`trips.service_date`). App tìm thấy chuyến mới ngay.
- Kiểm tra khi phân công: xe bảo trì, sai loại xe / số chỗ / nhà xe, tài xế tạm nghỉ, GPLX hết hạn hoặc không đủ hạng
  (D1 ≤ 16 chỗ, D2 ≤ 29 chỗ, D > 29 chỗ), trùng lịch xe / tài xế (nghỉ tối thiểu 30 phút giữa hai chuyến).
- Thống kê tính theo ngày khởi hành: doanh thu / vé = vé đã thanh toán; lấp đầy = vé bán / ghế của các chuyến đã chạy.
- "Báo cáo đề xuất": app ghi lượt hiển thị (`GET /suggestions`) và lượt nhấp (`POST /suggestions/click`);
  vé từ đề xuất = vé cùng người, cùng chuyến, đặt trong 48 giờ sau lượt nhấp.

**Dữ liệu minh họa** (`ADMIN_DEMO=true`, mặc định bật): 6.000 khách mẫu (`users.demo = true`, không đăng nhập được),
vé đã đi của 365 ngày gần nhất (~120.000 vé), đánh giá, lượt xem / nhấp đề xuất 90 ngày, phân công tài xế 7 ngày tới.
Chỉ tạo ở các ngày đã qua nên không chiếm ghế khi khách đặt vé; mỗi đêm 00:20 tự bổ sung ngày hôm qua.
Dashboard hiện nhãn "Số liệu minh họa" khi kỳ đang xem có dữ liệu này.

| Phương thức | Đường dẫn `/api/v1/admin` | Mô tả |
|---|---|---|
| GET | `/stats/overview?mode=DAY\|MONTH\|YEAR&date=` | KPI, biểu đồ doanh thu, top tuyến trong kỳ |
| GET | `/stats/top-routes?from&to&sort=TICKETS\|REVENUE\|FILL` | Bảng xếp hạng tuyến, chuyến nổi bật |
| GET | `/stats/suggestions?from&to&group=` | Hiển thị / nhấp / vé từ đề xuất, 12 tuần, theo nhóm khách |
| GET | `/activity` | Hoạt động 7 ngày (chuông thông báo) |
| GET | `/options`, POST `/stations` | Nhà xe, bến xe, thành phố, tuyến; thêm bến |
| GET POST PUT DELETE | `/vehicles`, `/vehicles/{id}` | Quản lý xe (trạng thái Đang chạy tính theo lịch thật) |
| GET POST PUT DELETE | `/drivers`, `/drivers/{id}` | Quản lý tài xế |
| GET POST PUT DELETE | `/routes`, `/routes/{id}`, GET `/routes/{id}/runs?date=` | Tuyến và các chuyến chạy trong ngày |
| GET POST PUT DELETE | `/trips`, `/trips/{id}` | Chuyến cố định hằng ngày |
| GET | `/schedules?from&days&routeId&vehicleId&driverId` | Lịch chạy |
| POST | `/schedules/preview`, `/schedules` | Kiểm tra / lưu lịch chuyến |
| PUT | `/schedules/assign` | Phân công xe / tài xế cho một chuyến chạy |
| DELETE | `/schedules/{tripId}/{date}`, `/schedules/{tripId}/{date}/driver` | Xóa lịch, bỏ tài xế |

## Server đang chạy (Azure)

Backend chạy trên máy ảo Azure, app Android gọi API qua Internet:

- API: **https://vego-api-dhfhi.indonesiacentral.cloudapp.azure.com**
- Tài liệu API: https://vego-api-dhfhi.indonesiacentral.cloudapp.azure.com/swagger-ui.html
- Trạng thái: https://vego-api-dhfhi.indonesiacentral.cloudapp.azure.com/actuator/health

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

- Chỉ còn cổng **VNPay** (đã bỏ MoMo; migration `V7` chuyển giao dịch MoMo cũ sang VNPay).
  Trên trang VNPay người dùng chọn thẻ nội địa hoặc quét QR bằng app ngân hàng.
- **VNPay**: cần `VNPAY_TMN_CODE` + `VNPAY_HASH_SECRET` (đăng ký tại sandbox.vnpayment.vn/devreg)
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

Thư mục "9. Quản trị (web admin)" chỉ chạy khi environment có `adminEmail` / `adminPassword`
(Local đã có sẵn tài khoản CI; với Azure tự thêm 2 biến này, không lưu vào Git).

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
