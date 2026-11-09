# CaloLens — Android calorie diary

Ứng dụng Android tiếng Việt: chụp/chọn ảnh → Gemini ước tính món và khẩu phần → người dùng chỉnh sửa, xác nhận → nhật ký. Có hồ sơ cá nhân và tạo kế hoạch ngày, tuần, tháng.

## Chạy bản phát triển

Yêu cầu: Node.js 20.12+, JDK 17, Android SDK 34, Android Studio hoặc Gradle Wrapper kèm dự án. Gradle cần Internet trong lần tải thư viện đầu tiên.

### 1. Backend

Đã tạo `backend/.env` cục bộ bằng thông tin API được cung cấp. File này được bỏ qua bởi Git; không đưa lên kho mã hoặc gửi kèm APK.

```bash
cd backend
npm start
```

Backend mặc định lắng nghe tại `127.0.0.1:8787`. Không cần `npm install` vì dùng thư viện có sẵn của Node.js.

Khi thiết lập trên máy khác, sao chép `.env.example` thành `.env`, điền `AI_API_KEY` và tạo `APP_TOKEN` ngẫu nhiên ít nhất 24 ký tự. `AI_BASE_URL` và `AI_MODEL` đã có trong mẫu; AI dùng giao thức Chat Completions tương thích OpenAI, bao gồm `image_url` dạng base64. Khóa AI chỉ có ở backend.

### 2. Android

Mở thư mục `android` bằng Android Studio, hoặc:

```bash
cd android
./gradlew :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

File `android/local.properties` đã được tạo cho máy hiện tại. Trên máy khác, tạo file với:

```properties
sdk.dir=/duong/dan/Android/Sdk
api.url=http://10.0.2.2:8787
api.token=gia-tri-APP_TOKEN-tu-backend
```

`10.0.2.2` là địa chỉ máy chủ từ Android Emulator. Với điện thoại kết nối USB, giữ backend trên localhost và sử dụng:

```bash
adb reverse tcp:8787 tcp:8787
```

Đổi `api.url=http://127.0.0.1:8787`, build lại và cài APK lên điện thoại. Nếu dùng mạng LAN, backend phải bind địa chỉ phù hợp, cấu hình firewall và đổi `api.url`; tránh mở cổng ra Internet khi chưa có xác thực người dùng thực sự.

### 3. Kiểm thử

```bash
cd backend
npm test
# Tùy chọn: thực sự gọi AI, có thể phát sinh phí. Chỉ dùng hồ sơ giả.
node --env-file=.env smoke.mjs
# Phân tích một ảnh JPEG thử nghiệm do bạn chọn:
node --env-file=.env smoke.mjs /duong/dan/mon-an.jpg
```

```bash
cd android
./gradlew :app:lintDebug
```

## Có gì trong MVP

- Android native Kotlin + Jetpack Compose; giao diện màu xanh lá và nền kem.
- Camera ảnh đầy đủ qua URI riêng, Android Photo Picker; không xin quyền đọc toàn bộ thư viện.
- Nén/thu nhỏ ảnh, xử lý hướng EXIF trước khi gửi.
- Nhận diện nhiều món; kết quả không phải thức ăn; thông báo lỗi, thời gian chờ, giới hạn lượt.
- Sửa tên món, khẩu phần và kcal/protein/carbs/fat. Thay khối lượng tính lại theo tỷ lệ; có thể nhập thủ công.
- Nhật ký theo ngày, sửa từng món, xóa bữa có xác nhận. Không lưu hai lần cùng kết quả phân tích.
- Hồ sơ người trưởng thành: tuổi, chiều cao, cân nặng, mục tiêu kcal tự đặt, sở thích, thực phẩm cần tránh, ngân sách.
- Thực đơn 1 ngày, 7 ngày, hoặc từ ngày bắt đầu đến trước cùng ngày tháng sau (28–31 ngày).
- Thực đơn tháng tạo tuần tự từng nhóm tối đa 7 ngày; chỉ lưu khi toàn bộ thành công. Ngày ngoài khoảng được giữ lại.
- Kiểm tra đủ số ngày, 4 bữa/ngày, số dinh dưỡng hữu hạn, tổng kcal ngày trong ±15% mục tiêu. Kcal do backend tự cộng.
- Xác nhận món thực tế và ngày thực tế khi đưa kế hoạch vào nhật ký; có thể chỉnh món và khẩu phần trước khi lưu.
- Dữ liệu lưu cục bộ trong bộ nhớ riêng của app; backend không lưu ảnh, hồ sơ hoặc lịch sử yêu cầu.
- Đồng ý trước khi gửi AI, thu hồi quyền và xóa dữ liệu trên thiết bị.

## Giới hạn cần hiểu

- Dinh dưỡng hiện do AI ước tính trực tiếp, **chưa đối chiếu cơ sở dữ liệu thực phẩm**. Chưa có đánh giá sai số trên bộ ảnh chuẩn; không công bố độ chính xác.
- Thực đơn dựa trên hồ sơ và mục tiêu người dùng đặt. Chưa tự tính nhu cầu năng lượng, chưa tự điều chỉnh theo cân nặng hoặc lượng đã ăn.
- AI được yêu cầu tránh dị ứng nhưng chưa có bộ kiểm tra thành phần đáng tin cậy. Người dùng phải kiểm tra nguyên liệu; không dùng app làm công cụ quản lý dị ứng hoặc điều trị.
- Chưa có tài khoản, đồng bộ cloud, khôi phục dữ liệu, đổi riêng một món bằng AI, danh sách mua sắm, nhắc giờ hoặc biểu đồ cân nặng.
- Mục tiêu kcal mặc định 2.000 là giá trị khởi tạo, không phải khuyến nghị cá nhân. Phạm vi nhập là giới hạn sản phẩm thử nghiệm.
- `APP_TOKEN` là khóa truy cập **môi trường phát triển**, được đóng gói trong APK; không thay thế đăng nhập và xác thực người dùng trong sản phẩm công khai.
- Endpoint AI được cung cấp hiện dùng HTTP: khóa AI và nội dung yêu cầu không được mã hóa trên chặng backend → AI. Dùng dữ liệu thử nghiệm cho đến khi có HTTPS.
- Bản debug cho phép HTTP để kết nối localhost. Bản release chặn HTTP; cần chuyển cả backend công khai và AI upstream sang HTTPS.
- Toolchain hiện dùng SDK/target 34 để build bằng môi trường có sẵn; cần cập nhật SDK/target theo yêu cầu Google Play tại thời điểm phát hành. Chưa có ký release hoặc phát hành lên cửa hàng.
- SharedPreferences JSON phù hợp bản thử nghiệm nhỏ; nên chuyển Room khi lịch sử tăng hoặc cần truy vấn/phục hồi phức tạp.

## Cấu trúc

```text
android/        Android app, Gradle Wrapper
backend/src/    HTTP API, kiểm tra dữ liệu, adapter AI
backend/test/   Kiểm thử bằng node:test và HTTP localhost
backend/smoke.mjs  Kiểm tra kết nối thật, không chạy trong bộ test
docs/           Kiến trúc và hướng dẫn kiểm thử thủ công
```

Tham khảo: [Gemini OpenAI compatibility](https://ai.google.dev/gemini-api/docs/openai), [Android Gradle plugin](https://developer.android.com/build/releases/about-agp).
# Caculate-calo-by-image
