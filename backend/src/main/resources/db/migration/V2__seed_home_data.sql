-- Dữ liệu mẫu cho trang chủ (khớp với dữ liệu mẫu trong app / Figma)

INSERT INTO operators (code, name, hotline) VALUES
    ('PT', 'Phương Trang', '1900 6067'),
    ('TB', 'Thành Bưởi',   '1900 6079'),
    ('DL', 'Điền Linh',    '1900 6787'),
    ('HL', 'Hoa Lan',      '0258 3888 888');

INSERT INTO stations (city, name, address) VALUES
    ('TP. Hồ Chí Minh', 'Bến xe Miền Đông mới',          '501 Hoàng Hữu Nam, TP. Thủ Đức'),
    ('TP. Hồ Chí Minh', 'Văn phòng Thành Bưởi',          '266 Lê Hồng Phong, Quận 5'),
    ('TP. Hồ Chí Minh', 'Bến xe An Sương',               'Quốc lộ 22, Hóc Môn'),
    ('TP. Hồ Chí Minh', 'Văn phòng Phương Trang',        '272 Đề Thám, Quận 1'),
    ('TP. Hồ Chí Minh', 'Bến xe Miền Tây',               '395 Kinh Dương Vương, Bình Tân'),
    ('Đà Lạt',          'Bến xe Đà Lạt',                 '01 Tô Hiến Thành, TP. Đà Lạt'),
    ('Đà Lạt',          'Văn phòng Phương Trang Đà Lạt', '11A Lê Quý Đôn, TP. Đà Lạt'),
    ('Nha Trang',       'Bến xe phía Nam Nha Trang',     '58 Đường 23/10, TP. Nha Trang'),
    ('Vũng Tàu',        'Bến xe Vũng Tàu',               '192 Nam Kỳ Khởi Nghĩa, TP. Vũng Tàu'),
    ('Cần Thơ',         'Bến xe Trung tâm Cần Thơ',      'Quốc lộ 1A, Cái Răng, TP. Cần Thơ');

INSERT INTO routes (origin_city, destination_city, distance_km, popular_rank, image_key) VALUES
    ('TP. Hồ Chí Minh', 'Đà Lạt',          300, 1, 'img_dalat_suong'),
    ('TP. Hồ Chí Minh', 'Nha Trang',       430, 2, NULL),
    ('TP. Hồ Chí Minh', 'Vũng Tàu',        100, 3, NULL),
    ('TP. Hồ Chí Minh', 'Cần Thơ',         170, 4, NULL),
    ('Đà Lạt',          'TP. Hồ Chí Minh', 300, NULL, NULL);

-- Hàm phụ cho dễ đọc: id của tuyến / bến / nhà xe theo tên
CREATE TEMPORARY TABLE _ids AS
SELECT
    (SELECT id FROM routes   WHERE origin_city = 'TP. Hồ Chí Minh' AND destination_city = 'Đà Lạt')    AS r_dl,
    (SELECT id FROM routes   WHERE origin_city = 'TP. Hồ Chí Minh' AND destination_city = 'Nha Trang') AS r_nt,
    (SELECT id FROM routes   WHERE origin_city = 'TP. Hồ Chí Minh' AND destination_city = 'Vũng Tàu')  AS r_vt,
    (SELECT id FROM routes   WHERE origin_city = 'TP. Hồ Chí Minh' AND destination_city = 'Cần Thơ')   AS r_ct,
    (SELECT id FROM routes   WHERE origin_city = 'Đà Lạt' AND destination_city = 'TP. Hồ Chí Minh')    AS r_dl_hcm,
    (SELECT id FROM operators WHERE code = 'PT') AS o_pt,
    (SELECT id FROM operators WHERE code = 'TB') AS o_tb,
    (SELECT id FROM operators WHERE code = 'DL') AS o_dl,
    (SELECT id FROM operators WHERE code = 'HL') AS o_hl,
    (SELECT id FROM stations WHERE name = 'Bến xe Miền Đông mới')          AS s_mien_dong,
    (SELECT id FROM stations WHERE name = 'Văn phòng Thành Bưởi')          AS s_vp_tb,
    (SELECT id FROM stations WHERE name = 'Bến xe An Sương')               AS s_an_suong,
    (SELECT id FROM stations WHERE name = 'Bến xe Miền Tây')               AS s_mien_tay,
    (SELECT id FROM stations WHERE name = 'Bến xe Đà Lạt')                 AS s_dalat,
    (SELECT id FROM stations WHERE name = 'Bến xe phía Nam Nha Trang')     AS s_nhatrang,
    (SELECT id FROM stations WHERE name = 'Bến xe Vũng Tàu')               AS s_vungtau,
    (SELECT id FROM stations WHERE name = 'Bến xe Trung tâm Cần Thơ')      AS s_cantho;

INSERT INTO trips (id, route_id, operator_id, bus_name, bus_type, rating, depart_time, duration_minutes,
                   price, total_seats, seats_left, pickup_station_id, dropoff_station_id)
SELECT v.id, v.route_id, v.operator_id, v.bus_name, v.bus_type, v.rating, v.depart_time::time, v.duration,
       v.price, v.total_seats, v.seats_left, v.pickup, v.dropoff
FROM _ids i, LATERAL (VALUES
    -- TP.HCM -> Đà Lạt (giống TripRepository trong app)
    ('PT2200', i.r_dl, i.o_pt, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.8, '22:00', 420, 320000, 34, 12, i.s_mien_dong, i.s_dalat),
    ('TB2100', i.r_dl, i.o_tb, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.8, '21:00', 420, 350000, 34,  8, i.s_vp_tb,     i.s_dalat),
    ('PT2300', i.r_dl, i.o_pt, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.8, '23:00', 420, 320000, 34, 16, i.s_mien_dong, i.s_dalat),
    ('DL2000', i.r_dl, i.o_dl, 'Giường nằm 40 chỗ',           'SLEEPER',   4.6, '20:00', 450, 280000, 40, 20, i.s_mien_dong, i.s_dalat),
    ('PT0800', i.r_dl, i.o_pt, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.9, '08:00', 420, 320000, 34,  5, i.s_mien_dong, i.s_dalat),
    ('TB1330', i.r_dl, i.o_tb, 'Ghế ngồi 29 chỗ',             'SEATER',    4.5, '13:30', 450, 220000, 29, 18, i.s_an_suong,  i.s_dalat),
    -- TP.HCM -> Nha Trang
    ('PT2130', i.r_nt, i.o_pt, 'Giường nằm 40 chỗ',           'SLEEPER',   4.7, '21:30', 510, 280000, 40, 15, i.s_mien_dong, i.s_nhatrang),
    ('HL2000', i.r_nt, i.o_hl, 'Limousine giường nằm 22 chỗ', 'LIMOUSINE', 4.8, '20:00', 480, 390000, 22,  6, i.s_mien_dong, i.s_nhatrang),
    ('PT0900', i.r_nt, i.o_pt, 'Giường nằm 40 chỗ',           'SLEEPER',   4.6, '09:00', 510, 280000, 40, 22, i.s_mien_dong, i.s_nhatrang),
    -- TP.HCM -> Vũng Tàu
    ('PT0700', i.r_vt, i.o_pt, 'Ghế ngồi 29 chỗ',             'SEATER',    4.6, '07:00', 150, 160000, 29, 14, i.s_mien_dong, i.s_vungtau),
    ('PT1500', i.r_vt, i.o_pt, 'Limousine 9 chỗ',             'LIMOUSINE', 4.8, '15:00', 130, 200000,  9,  3, i.s_mien_dong, i.s_vungtau),
    -- TP.HCM -> Cần Thơ
    ('PT0630', i.r_ct, i.o_pt, 'Giường nằm 40 chỗ',           'SLEEPER',   4.7, '06:30', 210, 165000, 40, 25, i.s_mien_tay,  i.s_cantho),
    ('TB1700', i.r_ct, i.o_tb, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.7, '17:00', 200, 210000, 34, 10, i.s_mien_tay,  i.s_cantho),
    -- Đà Lạt -> TP.HCM
    ('PT2215', i.r_dl_hcm, i.o_pt, 'Limousine giường nằm 34 chỗ', 'LIMOUSINE', 4.8, '22:15', 420, 320000, 34, 19, i.s_dalat, i.s_mien_dong)
) AS v(id, route_id, operator_id, bus_name, bus_type, rating, depart_time, duration, price, total_seats, seats_left, pickup, dropoff);

INSERT INTO promotions (code, badge, title, description, note, discount_percent, max_discount, image_key,
                        route_id, starts_at, ends_at, priority)
SELECT 'VEGIN15', 'MÙA THU THẢNH THƠI', 'Đà Lạt gọi, giảm 15%', 'Mã VEGIN15 · Tối đa 50.000đ',
       'Áp dụng tuyến TP. Hồ Chí Minh – Đà Lạt', 15, 50000, 'img_dalat_suong',
       i.r_dl, TIMESTAMPTZ '2026-09-01 00:00+07', TIMESTAMPTZ '2027-01-31 23:59+07', 10
FROM _ids i
UNION ALL
SELECT 'BIENXANH20', 'BIỂN GỌI', 'Nha Trang, giảm 20%', 'Mã BIENXANH20 · Tối đa 60.000đ',
       'Áp dụng tuyến TP. Hồ Chí Minh – Nha Trang', 20, 60000, NULL,
       i.r_nt, TIMESTAMPTZ '2026-10-01 00:00+07', TIMESTAMPTZ '2027-03-31 23:59+07', 5
FROM _ids i;

DROP TABLE _ids;
