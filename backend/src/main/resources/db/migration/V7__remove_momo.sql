-- Bỏ thanh toán MoMo: chỉ còn cổng VNPay (và trang mô phỏng khi demo / CI).
-- Các giao dịch MoMo đã có (dữ liệu mẫu) được ghi lại là VNPay để không còn giá trị không hợp lệ.
UPDATE payments SET provider = 'VNPAY' WHERE provider = 'MOMO';
UPDATE payments SET method = 'VNPAY' WHERE method = 'MOMO';
UPDATE bookings SET payment_method = 'VNPAY' WHERE payment_method = 'MOMO';

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_provider_check;
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_provider_check CHECK (provider IN ('VNPAY', 'SIMULATED'));
ALTER TABLE payments ADD CONSTRAINT payments_method_check CHECK (method IN ('VNPAY', 'CARD'));

-- Thông báo cũ nhắc tới MoMo
UPDATE notifications SET body = replace(body, 'MoMo', 'VNPay') WHERE body LIKE '%MoMo%';
