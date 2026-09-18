# FBResume

Android companion app cho việc lưu và khôi phục vị trí xem video Facebook.

## Kiến trúc
- Android AccessibilityService quan sát UI Facebook.
- ResumeStore lưu timestamp cục bộ.
- Termux có thể quản lý và sao lưu dữ liệu ở lớp riêng.
- Shizuku là tùy chọn.

## Giới hạn
Facebook không cung cấp API công khai để đọc currentTime của video. App chỉ sử dụng dữ liệu UI mà AccessibilityService được phép quan sát. Giao diện Facebook thay đổi có thể làm nhận diện timestamp hoặc thanh tiến trình không hoạt động.

## Build
Yêu cầu Android SDK và Gradle phù hợp với Android Gradle Plugin 8.7.3.
Lệnh build: ./gradlew assembleDebug
APK: app/build/outputs/apk/debug/app-debug.apk

## Cài đặt
1. Cài APK.
2. Mở FBResume.
3. Mở Accessibility.
4. Bật dịch vụ FBResume.
5. Mở Facebook và kiểm tra hoạt động.
