# FBResume

Android companion app + Termux helper để lưu và khôi phục vị trí xem video Facebook.

## Đã hỗ trợ
- Facebook app và Facebook Lite.
- Nhận timestamp dạng MM:SS và HH:MM:SS, phù hợp video rất dài, kể cả khoảng 11 giờ.
- Lưu vị trí cục bộ theo dấu vết tiêu đề hiển thị trên màn hình.
- Tự động thử resume khi video được mở lại và đang ở gần đầu video.
- AccessibilityService, không đọc API nội bộ của Facebook.
- Termux hỗ trợ xem và sao lưu dữ liệu.

## Cách dùng
1. Build APK từ GitHub Actions hoặc Android Studio.
2. Cài APK.
3. Mở FBResume → Mở Accessibility → bật FBResume.
4. Mở video Facebook và để chạy ít nhất vài giây.
5. Thoát Facebook rồi mở lại cùng video.
6. App sẽ thử seek về vị trí đã lưu nếu Facebook expose thanh tiến trình qua Accessibility.

## Giới hạn
Facebook có nhiều UI tùy phiên bản. Nếu thanh tiến trình là custom view và không cung cấp ACTION_SET_PROGRESS, Android Accessibility có thể đọc timestamp nhưng không thể điều khiển seek bằng API công khai. App không dùng root, không vượt DRM và không truy cập dữ liệu nội bộ Facebook.

Identity hiện chủ yếu dựa trên text tiêu đề nhìn thấy được. Nếu nhiều video có tiêu đề giống nhau, có thể cần lớp nhận diện bổ sung.

## Build
GitHub Actions tự chạy khi push vào main hoặc master và tạo artifact FBResume-debug.

Build local cần Android SDK + JDK 17 + Gradle tương thích AGP 8.7.3:
    gradle assembleDebug

APK:
    app/build/outputs/apk/debug/app-debug.apk

## Termux
Trình cài đặt sẽ tự kiểm tra và cài các gói bắt buộc còn thiếu (hiện tại: `git`, `jq`).

    cd ~/fbresume
    bash termux/install.sh

Sau khi cài, có thể kiểm tra toàn bộ môi trường:

    fbresume doctor

Nếu thiếu quyền bộ nhớ, công cụ sẽ hướng dẫn chạy:

    termux-setup-storage

Lệnh `open URL` không bắt buộc. Nếu muốn mở URL Facebook trực tiếp từ Termux, có thể cài Termux:API và gói `termux-api`; chương trình sẽ nhận biết nếu thành phần này chưa có và đưa ra hướng dẫn thay vì tự coi đó là lỗi bắt buộc.

    fbresume
    fbresume doctor
    fbresume list
    fbresume backup
    fbresume open "https://www.facebook.com/"
