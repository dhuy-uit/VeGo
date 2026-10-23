-- Vé của tôi (hủy vé / hoàn tiền, đánh giá), thông báo, Ví xu (điểm danh, game, đổi voucher)

-- ---------- Vé đã đặt ----------
ALTER TABLE bookings ADD COLUMN cancelled_at   TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN refund_amount  INT CHECK (refund_amount >= 0);
-- Số xu đã cộng khi thanh toán (hủy vé thì thu hồi lại)
ALTER TABLE bookings ADD COLUMN coins_earned   INT NOT NULL DEFAULT 0;
-- Đã gửi thông báo nhắc lịch / mời đánh giá chưa (job chạy định kỳ)
ALTER TABLE bookings ADD COLUMN reminder_sent  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN review_invited BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_bookings_paid_date ON bookings (travel_date) WHERE status = 'PAID';

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT        NOT NULL UNIQUE REFERENCES bookings (id),
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    trip_id     VARCHAR(20)   NOT NULL REFERENCES trips (id),
    operator_id INT           NOT NULL REFERENCES operators (id),
    rating      SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tags        VARCHAR(200),                       -- vd "CLEAN,ONTIME,STAFF"
    comment     VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_operator ON reviews (operator_id);

-- ---------- Thông báo trong app ----------
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(20)  NOT NULL CHECK (kind IN ('REMINDER', 'TICKET', 'PROMO', 'REVIEW', 'WALLET')),
    title       VARCHAR(150) NOT NULL,
    body        VARCHAR(500) NOT NULL,
    ticket_code VARCHAR(20),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- ---------- Ví xu ----------
ALTER TABLE users ADD COLUMN coin_balance INT NOT NULL DEFAULT 0 CHECK (coin_balance >= 0);

CREATE TABLE coin_transactions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source     VARCHAR(20) NOT NULL CHECK (source IN ('WELCOME', 'CHECK_IN', 'GAME', 'BOOKING', 'VOUCHER', 'REFUND')),
    amount     INT         NOT NULL CHECK (amount <> 0),
    note       VARCHAR(150),
    ref        VARCHAR(40),                         -- mã vé / mã voucher liên quan
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_coin_tx_user ON coin_transactions (user_id, created_at DESC);

-- Điểm danh: mỗi người mỗi ngày một lần; streak = số ngày liên tiếp, cycle_day = ngày thứ mấy trong chu kỳ 7 ngày
CREATE TABLE check_ins (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day        DATE        NOT NULL,
    streak     INT         NOT NULL CHECK (streak >= 1),
    cycle_day  INT         NOT NULL CHECK (cycle_day BETWEEN 1 AND 7),
    reward     INT         NOT NULL CHECK (reward > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, day)
);

-- Lượt chơi game "Khủng long phiêu lưu" (2 lượt / ngày)
CREATE TABLE game_plays (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day         DATE        NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    score       INT CHECK (score >= 0),
    coins       INT CHECK (coins >= 0)
);
CREATE INDEX idx_game_plays_user_day ON game_plays (user_id, day);

-- Voucher đổi bằng xu
CREATE TABLE voucher_offers (
    id         VARCHAR(10) PRIMARY KEY,
    discount   INT     NOT NULL CHECK (discount > 0),
    min_order  INT     NOT NULL DEFAULT 0,
    cost       INT     NOT NULL CHECK (cost > 0),
    valid_days INT     NOT NULL DEFAULT 30,
    sort_order INT     NOT NULL DEFAULT 0,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO voucher_offers (id, discount, min_order, cost, valid_days, sort_order) VALUES
    ('V20',   20000, 200000,  500, 30, 1),
    ('V50',   50000, 300000, 1000, 30, 2),
    ('V100', 100000, 500000, 2000, 30, 3);

CREATE TABLE user_vouchers (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL UNIQUE,         -- vd XU20K-7F3Q
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    offer_id   VARCHAR(10)  NOT NULL REFERENCES voucher_offers (id),
    discount   INT          NOT NULL CHECK (discount > 0),
    min_order  INT          NOT NULL DEFAULT 0,
    status     VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'USED')),
    booking_id BIGINT REFERENCES bookings (id),
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_vouchers_user ON user_vouchers (user_id, status);
