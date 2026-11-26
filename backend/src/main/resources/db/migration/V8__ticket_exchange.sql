-- Đổi vé: chuyển vé đã thanh toán sang chuyến / ngày / ghế khác cùng tuyến, cùng giá (mỗi vé đổi 1 lần)
ALTER TABLE bookings ADD COLUMN exchange_count INT NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN exchanged_at TIMESTAMPTZ;
