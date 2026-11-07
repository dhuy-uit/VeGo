-- Web admin: quản lý xe, tài xế, tuyến / chuyến, lập lịch chuyến và số liệu "Báo cáo đề xuất"

-- ---------- Tài khoản mẫu (dữ liệu minh họa cho dashboard) ----------
ALTER TABLE users ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------- Tuyến đường ----------
ALTER TABLE routes ADD COLUMN code VARCHAR(10);
ALTER TABLE routes ADD COLUMN duration_minutes INT CHECK (duration_minutes > 0);
ALTER TABLE routes ADD COLUMN origin_station_id INT REFERENCES stations (id);
ALTER TABLE routes ADD COLUMN destination_station_id INT REFERENCES stations (id);
ALTER TABLE routes ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE routes SET code = 'T' || lpad(id::text, 3, '0');
UPDATE routes r SET
    duration_minutes       = (SELECT min(t.duration_minutes) FROM trips t WHERE t.route_id = r.id),
    origin_station_id      = (SELECT t.pickup_station_id FROM trips t WHERE t.route_id = r.id
                              GROUP BY t.pickup_station_id ORDER BY count(*) DESC, t.pickup_station_id LIMIT 1),
    destination_station_id = (SELECT t.dropoff_station_id FROM trips t WHERE t.route_id = r.id
                              GROUP BY t.dropoff_station_id ORDER BY count(*) DESC, t.dropoff_station_id LIMIT 1);
ALTER TABLE routes ALTER COLUMN code SET NOT NULL;
ALTER TABLE routes ADD CONSTRAINT routes_code_unique UNIQUE (code);

-- ---------- Xe ----------
-- Sơ đồ ghế suy ra từ loại xe + số chỗ (giống SeatInventory): giường nằm / limousine từ 20 chỗ có 2 tầng.
CREATE TABLE vehicles (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(10)  NOT NULL UNIQUE,            -- vd XE001
    plate       VARCHAR(15)  NOT NULL UNIQUE,            -- vd 51B-123.45
    operator_id INT          NOT NULL REFERENCES operators (id),
    bus_type    VARCHAR(20)  NOT NULL CHECK (bus_type IN ('LIMOUSINE', 'SLEEPER', 'SEATER')),
    total_seats INT          NOT NULL CHECK (total_seats BETWEEN 4 AND 60),
    status      VARCHAR(20)  NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'MAINTENANCE')),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Tài xế ----------
-- Hạng GPLX theo Luật TTATGTĐB 2024: D1 (xe đến 16 chỗ), D2 (đến 29 chỗ), D (trên 29 chỗ); hạng *E kéo rơ moóc.
CREATE TABLE drivers (
    id               SERIAL PRIMARY KEY,
    code             VARCHAR(10)  NOT NULL UNIQUE,       -- vd TX001
    full_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(15)  NOT NULL UNIQUE,
    license_no       VARCHAR(20)  NOT NULL UNIQUE,
    license_class    VARCHAR(5)   NOT NULL CHECK (license_class IN ('D1', 'D2', 'D', 'D1E', 'D2E', 'DE')),
    license_expiry   DATE         NOT NULL,
    experience_years INT          NOT NULL DEFAULT 0 CHECK (experience_years BETWEEN 0 AND 60),
    status           VARCHAR(20)  NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'OFF')),
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Chuyến ----------
-- service_date NULL = chạy hằng ngày; có giá trị = chuyến tăng cường chỉ chạy đúng ngày đó (tạo từ "Lập lịch chuyến").
ALTER TABLE trips ADD COLUMN service_date DATE;
-- Xe chạy mặc định của chuyến (ngày nào không phân công riêng thì dùng xe này)
ALTER TABLE trips ADD COLUMN vehicle_id INT REFERENCES vehicles (id);
ALTER TABLE trips ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
-- Chuyến có sẵn từ trước: tính ghế mở bán từ đầu (dashboard chỉ tính ngày chuyến đã tồn tại)
UPDATE trips SET created_at = TIMESTAMPTZ '2025-01-01 00:00+07';
CREATE INDEX idx_trips_service_date ON trips (service_date) WHERE service_date IS NOT NULL;

-- Phân công xe + tài xế cho một chuyến trong một ngày
CREATE TABLE trip_assignments (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     VARCHAR(20)  NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    travel_date DATE         NOT NULL,
    vehicle_id  INT          REFERENCES vehicles (id),
    driver_id   INT          REFERENCES drivers (id),
    note        VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (trip_id, travel_date)
);
CREATE INDEX idx_assign_date ON trip_assignments (travel_date);
CREATE INDEX idx_assign_driver ON trip_assignments (driver_id, travel_date);
CREATE INDEX idx_assign_vehicle ON trip_assignments (vehicle_id, travel_date);

-- ---------- Báo cáo đề xuất ----------
-- IMPRESSION: chuyến được hiện trong "Dành riêng cho bạn"; CLICK: người dùng bấm vào chuyến được đề xuất.
CREATE TABLE suggestion_events (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       REFERENCES users (id) ON DELETE CASCADE,
    trip_id    VARCHAR(20)  NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    kind       VARCHAR(12)  NOT NULL CHECK (kind IN ('IMPRESSION', 'CLICK')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_suggestion_events_time ON suggestion_events (created_at);
CREATE INDEX idx_suggestion_events_user ON suggestion_events (user_id, trip_id, created_at);

-- Báo cáo theo ngày đi / ngày thanh toán
CREATE INDEX idx_bookings_paid_at ON bookings (paid_at) WHERE status = 'PAID';
CREATE INDEX idx_bookings_trip_date ON bookings (trip_id, travel_date);

-- ---------- Dữ liệu mẫu: đội xe ----------
-- Mỗi chuyến cố định có 1 xe mặc định đúng loại / số chỗ, thêm xe dự phòng và xe đang bảo trì.
INSERT INTO vehicles (code, plate, operator_id, bus_type, total_seats, status, note)
SELECT v.code, v.plate, o.id, v.bus_type, v.seats, v.status, v.note
FROM (VALUES
    ('XE001', '51B-123.45', 'PT', 'LIMOUSINE', 34, 'READY',       'Xe phục vụ tuyến TP. Hồ Chí Minh – Đà Lạt. Wi-Fi, USB, chăn và nước uống.'),
    ('XE002', '51B-234.56', 'TB', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE003', '51B-345.67', 'PT', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE004', '51B-456.78', 'DL', 'SLEEPER',   40, 'READY',       NULL),
    ('XE005', '51B-567.89', 'PT', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE006', '51B-678.90', 'TB', 'SEATER',    29, 'READY',       NULL),
    ('XE007', '51B-712.34', 'PT', 'SLEEPER',   40, 'READY',       NULL),
    ('XE008', '79B-023.45', 'HL', 'LIMOUSINE', 22, 'READY',       'Limousine cabin đôi, rèm riêng từng giường.'),
    ('XE009', '51B-789.01', 'PT', 'SLEEPER',   40, 'READY',       NULL),
    ('XE010', '51B-890.12', 'PT', 'SEATER',    29, 'READY',       NULL),
    ('XE011', '51B-901.23', 'PT', 'LIMOUSINE',  9, 'READY',       'Limousine 9 chỗ, ghế massage.'),
    ('XE012', '65B-012.34', 'PT', 'SLEEPER',   40, 'READY',       NULL),
    ('XE013', '65B-123.45', 'TB', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE014', '49B-234.56', 'PT', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE015', '51B-345.12', 'PT', 'LIMOUSINE', 34, 'READY',       'Xe dự phòng tuyến Đà Lạt.'),
    ('XE016', '51B-456.23', 'PT', 'SLEEPER',   40, 'READY',       'Xe dự phòng.'),
    ('XE017', '51B-567.34', 'TB', 'LIMOUSINE', 34, 'MAINTENANCE', 'Bảo dưỡng định kỳ 40.000 km, dự kiến xong trong tuần.'),
    ('XE018', '51B-678.45', 'PT', 'SEATER',    29, 'MAINTENANCE', 'Thay lốp và kiểm tra hệ thống phanh.'),
    ('XE019', '51B-789.56', 'DL', 'SLEEPER',   40, 'READY',       NULL),
    ('XE020', '79B-890.67', 'HL', 'LIMOUSINE', 22, 'READY',       NULL),
    ('XE021', '51B-901.78', 'PT', 'LIMOUSINE',  9, 'READY',       NULL),
    ('XE022', '51B-012.89', 'TB', 'SEATER',    29, 'READY',       NULL),
    ('XE023', '49B-123.90', 'PT', 'LIMOUSINE', 34, 'READY',       NULL),
    ('XE024', '65B-234.01', 'PT', 'SLEEPER',   40, 'READY',       NULL)
) AS v(code, plate, op, bus_type, seats, status, note)
JOIN operators o ON o.code = v.op
ORDER BY v.code;

UPDATE trips t SET vehicle_id = v.id
FROM (VALUES
    ('PT2200', 'XE001'), ('TB2100', 'XE002'), ('PT2300', 'XE003'), ('DL2000', 'XE004'), ('PT0800', 'XE005'),
    ('TB1330', 'XE006'), ('PT2130', 'XE007'), ('HL2000', 'XE008'), ('PT0900', 'XE009'), ('PT0700', 'XE010'),
    ('PT1500', 'XE011'), ('PT0630', 'XE012'), ('TB1700', 'XE013'), ('PT2215', 'XE014')
) AS m(trip_id, vehicle_code)
JOIN vehicles v ON v.code = m.vehicle_code
WHERE t.id = m.trip_id;

-- ---------- Dữ liệu mẫu: tài xế ----------
INSERT INTO drivers (code, full_name, phone, license_no, license_class, license_expiry, experience_years, status, note) VALUES
    ('TX001', 'Nguyễn Văn Hùng',   '0912345678', '790123456789', 'D',  DATE '2030-08-25', 8,  'READY', 'Kinh nghiệm tuyến Đà Lạt 8 năm. Ưu tiên lịch chạy buổi tối.'),
    ('TX002', 'Trần Quốc Bảo',     '0923456789', '790234567890', 'D',  DATE '2029-04-12', 6,  'READY', NULL),
    ('TX003', 'Lê Thanh Sơn',      '0934567890', '790345678901', 'DE', DATE '2031-01-30', 12, 'READY', NULL),
    ('TX004', 'Phạm Minh Đức',     '0945678901', '790456789012', 'D',  DATE '2028-11-05', 5,  'OFF',   'Nghỉ phép đến hết tuần.'),
    ('TX005', 'Võ Hoàng Phúc',     '0956789012', '790567890123', 'D',  DATE '2030-06-18', 9,  'READY', NULL),
    ('TX006', 'Đặng Quốc Việt',    '0967890123', '790678901234', 'D2', DATE '2029-09-09', 4,  'READY', 'Chạy tốt xe ghế ngồi 29 chỗ.'),
    ('TX007', 'Bùi Văn Tài',       '0978901234', '790789012345', 'D',  DATE '2030-02-14', 7,  'READY', NULL),
    ('TX008', 'Huỳnh Tấn Lộc',     '0989012345', '790890123456', 'D',  DATE '2031-07-01', 10, 'READY', NULL),
    ('TX009', 'Ngô Thành Nhân',    '0990123456', '790901234567', 'D',  DATE '2029-12-20', 6,  'READY', NULL),
    ('TX010', 'Dương Minh Khoa',   '0901122334', '791012345678', 'D1', DATE '2030-10-10', 3,  'READY', 'Chuyên xe limousine 9 chỗ.'),
    ('TX011', 'Lý Hoàng Nam',      '0902233445', '791123456789', 'D',  DATE '2028-05-22', 11, 'READY', NULL),
    ('TX012', 'Trương Văn Kiệt',   '0903344556', '791234567890', 'D',  DATE '2030-03-03', 8,  'READY', NULL),
    ('TX013', 'Đỗ Thanh Tùng',     '0904455667', '791345678901', 'D2', DATE '2029-08-08', 5,  'READY', NULL),
    ('TX014', 'Phan Quốc Thịnh',   '0905566778', '791456789012', 'D',  DATE '2031-11-11', 13, 'READY', NULL),
    ('TX015', 'Hồ Văn Lực',        '0906677889', '791567890123', 'D',  DATE '2030-12-01', 7,  'READY', NULL),
    ('TX016', 'Mai Xuân Trường',   '0907788990', '791678901234', 'D',  DATE '2029-06-30', 6,  'OFF',   'Tạm nghỉ chờ khám sức khỏe định kỳ.'),
    ('TX017', 'Châu Minh Quân',    '0908899001', '791789012345', 'D',  DATE '2030-09-15', 9,  'READY', NULL),
    ('TX018', 'Tạ Đình Phong',     '0909900112', '791890123456', 'D1', DATE '2031-04-04', 4,  'READY', NULL),
    ('TX019', 'Lâm Chí Thanh',     '0911002233', '791901234567', 'D',  DATE '2027-01-15', 15, 'READY', 'GPLX sắp hết hạn, nhắc gia hạn.'),
    ('TX020', 'Kiều Văn Hậu',      '0913113344', '792012345678', 'D',  DATE '2030-07-07', 5,  'READY', NULL);

-- Nhóm khách (chuyến đã đi gần nhất của một người) cho "Báo cáo đề xuất"
CREATE INDEX idx_bookings_user_travel ON bookings (user_id, travel_date) WHERE status = 'PAID';
