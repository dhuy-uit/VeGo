# VéGo Backend

REST API cho app đặt vé xe khách VéGo: **xác thực**, **hồ sơ người dùng**, **trang chủ**.

- Spring Boot 3.5 · Java 21 · PostgreSQL 16 (tự host trong Docker) · Flyway · JWT
- Tài liệu API tự sinh: `/swagger-ui.html`
- Bộ test Postman: `postman/` (35 request, ~130 assertion), chạy tự động trên GitHub Actions mỗi lần push

## Cấu trúc

```
backend/
├── src/main/java/com/vego/backend/
│   ├── auth/       Đăng ký, đăng nhập, refresh token xoay vòng, đăng xuất, quên/đặt lại mật khẩu
│   ├── user/       Hồ sơ /users/me, ảnh đại diện, đổi mật khẩu
│   ├── home/       Trang chủ: khuyến mãi, tuyến phổ biến, chuyến gợi ý
│   ├── security/   JWT filter, cấu hình Spring Security
│   ├── common/     Định dạng lỗi chung, tiện ích
│   └── seed/       Tạo tài khoản mẫu
├── src/main/resources/
│   ├── db/migration/   V1 tạo bảng, V2 dữ liệu mẫu (nhà xe, bến, tuyến, chuyến, khuyến mãi)
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

Token: `Authorization: Bearer <accessToken>`. Access token sống 15 phút, refresh token 30 ngày.
Bảo mật: mật khẩu BCrypt; chỉ lưu SHA-256 của refresh token / mã khôi phục; sai mật khẩu 5 lần khóa đăng nhập 15 phút;
ảnh tải lên kiểm tra theo nội dung tệp; quên mật khẩu không tiết lộ tài khoản có tồn tại hay không.

Tài khoản mẫu: **0901234567 / vego123456** (Nguyễn Minh Anh).

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
