-- VéGo: cấu trúc CSDL ban đầu (tài khoản + dữ liệu trang chủ)

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    full_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(15)  NOT NULL UNIQUE,   -- chỉ lưu chữ số, vd 0901234567
    email               VARCHAR(255) NOT NULL UNIQUE,   -- luôn viết thường
    password_hash       VARCHAR(100) NOT NULL,          -- BCrypt
    birth_date          DATE,
    avatar_file         VARCHAR(100),
    role                VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'   CHECK (status IN ('ACTIVE', 'LOCKED')),
    password_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Refresh token: chỉ lưu SHA-256, xoay vòng mỗi lần làm mới.
-- family_id gom các token sinh ra từ cùng một lần đăng nhập để thu hồi cả chuỗi khi phát hiện dùng lại.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    device      VARCHAR(200),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

-- ---------- Dữ liệu trang chủ ----------

CREATE TABLE operators (
    id      SERIAL PRIMARY KEY,
    code    VARCHAR(10)  NOT NULL UNIQUE,
    name    VARCHAR(100) NOT NULL,
    hotline VARCHAR(20)
);

CREATE TABLE stations (
    id      SERIAL PRIMARY KEY,
    city    VARCHAR(100) NOT NULL,
    name    VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL
);

CREATE TABLE routes (
    id               SERIAL PRIMARY KEY,
    origin_city      VARCHAR(100) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    distance_km      INT,
    popular_rank     INT,             -- NULL = không hiện ở "Tuyến phổ biến"
    image_key        VARCHAR(50),
    UNIQUE (origin_city, destination_city)
);

-- Lịch chạy cố định hằng ngày (giờ khởi hành theo giờ Việt Nam)
CREATE TABLE trips (
    id                 VARCHAR(20) PRIMARY KEY,   -- vd PT2200
    route_id           INT          NOT NULL REFERENCES routes (id),
    operator_id        INT          NOT NULL REFERENCES operators (id),
    bus_name           VARCHAR(100) NOT NULL,
    bus_type           VARCHAR(20)  NOT NULL CHECK (bus_type IN ('LIMOUSINE', 'SLEEPER', 'SEATER')),
    rating             NUMERIC(2, 1) NOT NULL DEFAULT 4.5,
    depart_time        TIME         NOT NULL,
    duration_minutes   INT          NOT NULL CHECK (duration_minutes > 0),
    price              INT          NOT NULL CHECK (price > 0),
    total_seats        INT          NOT NULL,
    seats_left         INT          NOT NULL,
    pickup_station_id  INT          NOT NULL REFERENCES stations (id),
    dropoff_station_id INT          NOT NULL REFERENCES stations (id),
    active             BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_trips_route ON trips (route_id);

CREATE TABLE promotions (
    id               SERIAL PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL UNIQUE,
    badge            VARCHAR(60)  NOT NULL,
    title            VARCHAR(120) NOT NULL,
    description      VARCHAR(200) NOT NULL,
    note             VARCHAR(200),
    discount_percent INT          NOT NULL CHECK (discount_percent BETWEEN 1 AND 100),
    max_discount     INT,
    image_key        VARCHAR(50),
    route_id         INT REFERENCES routes (id),
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    priority         INT          NOT NULL DEFAULT 0,
    active           BOOLEAN      NOT NULL DEFAULT TRUE
);
