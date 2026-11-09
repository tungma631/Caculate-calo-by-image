# Kết quả kiểm tra — 09/11/2026

## Đã thực hiện

- `:app:assembleDebug`: thành công, tạo APK debug khoảng 7,7 MB.
- `:app:lintDebug`: thành công, không có lỗi; còn cảnh báo có phiên bản thư viện mới hơn.
- `node --test`: 12/12 kiểm thử đạt. Bao gồm kiểm tra ảnh, hồ sơ, dữ liệu AI, ngày qua năm mới, tổng kcal, lỗi upstream, timeout, xác thực và giới hạn lượt HTTP.
- Gọi gateway thực với model cấu hình: phản hồi Chat Completions khi đặt `stream: false`.
- Tạo thực đơn thử nghiệm 1 ngày: đủ 4 bữa, tổng 1.910 kcal với mục tiêu giả lập 2.000 kcal.
- Tạo thực đơn thử nghiệm 7 ngày: mỗi ngày đủ 4 bữa; tổng từng ngày 1.950, 1.980, 1.990, 1.960, 1.970, 1.970 và 2.000 kcal. Đây là giá trị AI tự ước tính, chỉ dùng để kiểm tra định dạng/ràng buộc.
- Phân tích [ảnh món ăn mẫu của Google](https://storage.googleapis.com/cloud-samples-data/generative-ai/image/scones.jpg): nhận diện được món và trả số dinh dưỡng hợp lệ. Không dùng kết quả này làm phép đo độ chính xác.
- Quét tệp mã nguồn được chia sẻ và các thành phần bên trong APK: không có chuỗi khóa API AI được cấu hình. Khóa chỉ nằm trong `backend/.env` bị Git bỏ qua.
- Backend phát triển được khởi chạy trên `127.0.0.1:8787`.

## Chưa xác nhận

- Chưa hoàn tất kiểm thử giao diện trên thiết bị. Máy ảo sẵn có dùng Android 37.1 khởi động không tăng tốc vì không có `/dev/kvm`; lúc thử cài APK dịch vụ package chưa sẵn sàng.
- Chưa chạy tự động luồng camera, bộ chọn ảnh, xoay màn hình và khôi phục dữ liệu qua giao diện.
- Chưa chạy toàn bộ yêu cầu AI tạo tháng 28–31 ngày; đã kiểm tra nhóm tối đa 7 ngày dùng trong luồng này và kiểm tra ngày/ràng buộc ở backend.
- Chưa đánh giá sai số dinh dưỡng trên bộ dữ liệu độc lập, độ tuân thủ dị ứng hoặc kiểm thử tải.
- Chưa ký/phát hành bản release hoặc triển khai backend công khai.

Danh sách kiểm thử thủ công: [TESTING.md](TESTING.md).
