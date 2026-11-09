# Kiểm thử bản Android

Các trường hợp dưới đây cần thiết bị hoặc emulator có camera/thư viện. Không đánh dấu đã chạy nếu chỉ build APK.

1. Mở app lần đầu: nhật ký rỗng, 2.000 kcal được giải thích là giá trị khởi tạo.
2. Lưu hồ sơ hợp lệ; thử tuổi dưới 18, số âm, số không hợp lệ và dấu phẩy thập phân.
3. Chụp ảnh món ăn, hủy camera, chọn ảnh thư viện, hủy bộ chọn. Kiểm tra xoay ảnh EXIF.
4. Từ chối đồng ý gửi AI: không gửi request. Đồng ý: phân tích được tiến hành. Thu hồi và kiểm tra hỏi lại.
5. Phân tích ảnh một món, nhiều món, ảnh mờ, ảnh không phải thức ăn.
6. Sửa gram: kcal và các chất đổi theo tỷ lệ; sửa tay các chất, thêm món, xóa món.
7. Lưu vào một ngày/bữa đã chọn: số liệu trang chủ/nhật ký khớp; kết quả không còn để bấm lưu lần hai.
8. Sửa món đã lưu và xóa bữa; đóng/mở lại app kiểm tra dữ liệu còn lưu.
9. Tạo kế hoạch 1/7/28–31 ngày; kiểm tra ngày, số bữa, tổng dinh dưỡng, khẩu vị và nguyên liệu cần tránh.
10. Cho backend lỗi giữa đợt tháng: kế hoạch cũ còn nguyên, không có kế hoạch tháng dở dang.
11. Ghi nhận bữa từ kế hoạch: có sửa khẩu phần/ngày thực tế, không tự động ghi món chưa ăn, chặn ghi lặp cùng bữa kế hoạch.
12. Tắt backend hoặc mạng: lỗi rõ, dữ liệu cục bộ vẫn xem/sửa được; không tự tạo kết quả AI giả.
13. Xoay màn hình khi tạo kế hoạch: công việc ở ViewModel tiếp tục. App bị hệ điều hành đóng: có thể cần chọn/phân tích lại ảnh; nhật ký/kế hoạch đã lưu còn nguyên.
14. Xóa dữ liệu: có xác nhận, nhật ký/hồ sơ/kế hoạch/quyền đồng ý và ảnh camera tạm được xóa.
15. Kiểm tra cỡ chữ lớn, màn hình nhỏ, bàn phím, nút điều hướng hệ thống và khả năng cuộn hộp thoại.

Ảnh và kết quả AI không phải dữ liệu chuẩn để đo sai số. Đánh giá chất lượng cần ảnh kèm khối lượng cân, thành phần và nguồn dinh dưỡng độc lập.
