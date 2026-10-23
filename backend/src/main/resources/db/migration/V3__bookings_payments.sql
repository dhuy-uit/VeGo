-- Đặt vé, thanh toán (MoMo / VNPay sandbox) và mã giảm giá

-- ---------- Mã giảm giá: mở rộng bảng promotions ----------
-- discount_percent (giảm theo %) hoặc discount_amount (giảm số tiền cố định)
ALTER TABLE promotions ALTER COLUMN discount_percent DROP NOT NULL;
ALTER TABLE promotions ADD COLUMN discount_amount INT CHECK (discount_amount > 0);
ALTER TABLE promotions ADD COLUMN min_order INT NOT NULL DEFAULT 0;
ALTER TABLE promotions ADD COLUMN per_user_limit INT NOT NULL DEFAULT 1;
-- false = mã chỉ dùng khi thanh toán, không hiện banner ở Trang chủ
ALTER TABLE promotions ADD COLUMN show_on_home BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE promotions ADD CONSTRAINT promotions_discount_kind
    CHECK (discount_percent IS NOT NULL OR discount_amount IS NOT NULL);

INSERT INTO promotions (code, badge, title, description, note, discount_percent, discount_amount, max_discount,
                        min_order, per_user_limit, show_on_home, starts_at, ends_at, priority)
VALUES
    ('VEGO10', 'ƯU ĐÃI', 'Giảm 10% mọi tuyến', 'Mã VEGO10 · Tối đa 30.000đ', 'Dùng tối đa 3 lần',
     10, NULL, 30000, 0, 3, FALSE, TIMESTAMPTZ '2026-10-01 00:00+07', TIMESTAMPTZ '2027-06-30 23:59+07', 0),
    ('CHAOBANMOI', 'BẠN MỚI', 'Giảm 50.000đ chuyến đầu', 'Mã CHAOBANMOI · Đơn từ 200.000đ', 'Mỗi tài khoản 1 lần',
     NULL, 50000, NULL, 200000, 1, FALSE, TIMESTAMPTZ '2026-10-01 00:00+07', TIMESTAMPTZ '2027-06-30 23:59+07', 0);

-- ---------- Đơn đặt vé ----------
CREATE TABLE bookings (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL UNIQUE,         -- mã vé, vd VG241026K7QM
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    trip_id         VARCHAR(20)  NOT NULL REFERENCES trips (id),
    travel_date     DATE         NOT NULL,
    seat_code       VARCHAR(10)  NOT NULL,
    passenger_name  VARCHAR(100) NOT NULL,
    passenger_phone VARCHAR(15)  NOT NULL,
    pickup_label    VARCHAR(200) NOT NULL,
    dropoff_label   VARCHAR(200) NOT NULL,
    fare            INT          NOT NULL CHECK (fare > 0),
    discount        INT          NOT NULL DEFAULT 0 CHECK (discount >= 0),
    total           INT          NOT NULL CHECK (total > 0),
    voucher_code    VARCHAR(30),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('HOLDING', 'PAID', 'EXPIRED', 'CANCELLED')),
    payment_method  VARCHAR(20),
    hold_expires_at TIMESTAMPTZ  NOT NULL,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_user ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_hold ON bookings (status, hold_expires_at);
-- Một ghế của một chuyến trong một ngày chỉ có một đơn đang giữ hoặc đã thanh toán
CREATE UNIQUE INDEX ux_bookings_active_seat ON bookings (trip_id, travel_date, seat_code)
    WHERE status IN ('HOLDING', 'PAID');

-- ---------- Giao dịch thanh toán ----------
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT       NOT NULL REFERENCES bookings (id),
    txn_ref         VARCHAR(40)  NOT NULL UNIQUE,         -- mã giao dịch gửi sang cổng (orderId / vnp_TxnRef)
    provider        VARCHAR(20)  NOT NULL CHECK (provider IN ('MOMO', 'VNPAY', 'SIMULATED')),
    method          VARCHAR(20)  NOT NULL CHECK (method IN ('MOMO', 'VNPAY', 'CARD')),
    amount          INT          NOT NULL CHECK (amount > 0),
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    provider_txn_id VARCHAR(50),
    response_code   VARCHAR(20),
    message         VARCHAR(255),
    pay_url         TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_booking ON payments (booking_id, created_at DESC);
