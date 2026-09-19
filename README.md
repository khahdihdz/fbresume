# FBResume

Android companion app + Termux helper để **lưu và khôi phục vị trí đang xem video Facebook**.

FBResume sử dụng **Android AccessibilityService** để quan sát các thông tin mà Facebook hiển thị trên giao diện, phát hiện timestamp video và lưu tiến độ cục bộ trên thiết bị. Khi mở lại video, app có thể thử đưa video về vị trí đã lưu nếu Facebook cung cấp thanh tiến trình có thể điều khiển qua Accessibility.

> **Lưu ý:** FBResume không sử dụng API nội bộ của Facebook, không cần root và không truy cập cơ sở dữ liệu/dữ liệu nội bộ của Facebook.

## ✨ Tính năng

- 📱 Hỗ trợ **Facebook** và **Facebook Lite**.
- ⏱️ Nhận timestamp dạng `MM:SS` và `HH:MM:SS`.
- 💾 Lưu tiến độ xem video **cục bộ trên thiết bị**.
- ▶️ Tự động thử **resume/seek** khi mở lại video.
- ♿ Sử dụng **AccessibilityService** để đọc thông tin hiển thị trên màn hình.
- 🧠 Tự động nhận diện tiêu đề video từ Accessibility tree, ưu tiên text/contentDescription gần khu vực phát video và thanh tiến trình.
- 🧹 Lọc timestamp, nút điều khiển và chuỗi kỹ thuật/accessibility label để hạn chế lưu nhầm tên component làm tiêu đề video.
- 🆔 Tạo khóa ổn định từ tiêu đề để nhận diện video đã lưu.
- 🛠️ Có helper **Termux** để kiểm tra môi trường, liệt kê và sao lưu dữ liệu.
- 🔐 Không yêu cầu root.
- 🚫 Không vượt DRM và không sử dụng API nội bộ của Facebook.

## 📸 Giao diện

Ứng dụng cung cấp màn hình tổng quan gồm:

- Trạng thái AccessibilityService.
- Số lượng video đã lưu.
- Vị trí xem gần nhất.
- Danh sách video gần đây.
- Nút mở nhanh phần cài đặt Accessibility.

## 🔄 Cách hoạt động

Luồng cơ bản:

```text
Facebook
   │
   ▼
AccessibilityService
   │
   ├── Đọc text / contentDescription
   ├── Phát hiện timestamp
   └── Tìm seek/progress bar
           │
           ▼
       FBResume
           │
           ├── Lưu position + duration + title
           └── Khi mở lại → thử seek về vị trí đã lưu
```

FBResume chỉ có thể điều khiển vị trí phát nếu giao diện Facebook cung cấp một node Accessibility phù hợp, chẳng hạn seek bar/progress bar hỗ trợ `ACTION_SET_PROGRESS`.

## 📋 Định dạng thời gian

Ứng dụng hỗ trợ:

| Dạng | Ví dụ |
|---|---|
| `MM:SS` | `03:29` |
| `HH:MM:SS` | `03:29:44` |

Các timestamp không hợp lệ sẽ được bỏ qua.

## 🗂️ Dữ liệu

Tiến độ được lưu **cục bộ trên thiết bị**.

Thông tin phục vụ resume chủ yếu gồm:

- Tiêu đề/text nhận diện video.
- Vị trí hiện tại.
- Thời lượng video.
- Khóa nhận diện ổn định.

FBResume không yêu cầu tài khoản riêng và không cần máy chủ backend để lưu tiến độ.

## ⚠️ Giới hạn

Facebook có nhiều phiên bản giao diện và có thể thay đổi Accessibility tree.

Một số trường hợp:

- Facebook chỉ expose timestamp nhưng không expose seek bar.
- Seek bar là custom view và không hỗ trợ `ACTION_SET_PROGRESS`.
- Nhiều video có cùng tiêu đề nên khóa nhận diện có thể trùng.
- Accessibility có thể trả về các label kỹ thuật thay vì tiêu đề video thực tế; detector sẽ lọc và chấm điểm nhiều ứng viên nhưng không thể đảm bảo chính xác với mọi phiên bản Facebook.
- Nếu tiêu đề xuất hiện muộn sau khi video tải, detector sẽ quét lại định kỳ và cập nhật bản ghi theo tiêu đề mới.
- Hành vi có thể khác nhau giữa Facebook và Facebook Lite.

Trong các trường hợp này, FBResume có thể **lưu được timestamp nhưng không thể tự động seek**, hoặc nhận diện video chưa chính xác.

## ♿ Cấp quyền Accessibility

1. Mở **FBResume**.
2. Chọn **Mở cài đặt Accessibility**.
3. Tìm **FBResume**.
4. Bật AccessibilityService.
5. Quay lại FBResume.
6. Mở Facebook và xem video trong vài giây.

Khi AccessibilityService hoạt động, FBResume sẽ bắt đầu theo dõi thông tin cần thiết để lưu tiến độ.

## 📦 Build APK

### GitHub Actions

Workflow Android tự chạy khi có thay đổi trên branch `main` hoặc `master`.

Workflow thực hiện:

1. Thiết lập Java 17.
2. Thiết lập Gradle.
3. Thiết lập version CI.
4. Kiểm tra signing keystore.
5. Build signed debug APK.
6. Build signed release APK.
7. Kiểm tra chữ ký APK.
8. Upload artifact.
9. Tạo GitHub Release.

### Build local

Yêu cầu:

- Android SDK.
- JDK 17.
- Gradle tương thích với **AGP 8.7.3**.

Build debug:

```bash
gradle assembleDebug
```

APK debug:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 📱 Termux

FBResume có helper Termux để quản lý dữ liệu và kiểm tra môi trường.

Cài đặt:

```bash
cd ~/fbresume
bash termux/install.sh
```

Kiểm tra môi trường:

```bash
fbresume doctor
```

Nếu cần quyền truy cập bộ nhớ:

```bash
termux-setup-storage
```

Các lệnh chính:

```bash
fbresume
fbresume doctor
fbresume list
fbresume backup
fbresume open "https://www.facebook.com/"
```

Các package bắt buộc hiện được installer kiểm tra/cài đặt gồm:

- `git`
- `jq`

Lệnh `open URL` không bắt buộc. Nếu muốn mở URL Facebook trực tiếp từ Termux, có thể cài Termux:API và package `termux-api`. Công cụ sẽ phát hiện thành phần này và hướng dẫn nếu chưa có.

## 🔐 Quyền riêng tư

FBResume được thiết kế theo hướng xử lý dữ liệu **trên thiết bị**.

Ứng dụng:

- Không yêu cầu root.
- Không sử dụng API nội bộ Facebook.
- Không truy cập database riêng của Facebook.
- Không cần backend để lưu tiến độ.
- Sử dụng AccessibilityService để đọc thông tin được Facebook expose trên giao diện.

AccessibilityService là một quyền nhạy cảm của Android. Chỉ bật quyền này nếu bạn hiểu và chấp nhận cách ứng dụng sử dụng quyền đó.

## 🧩 Công nghệ

- **Kotlin**
- **Android**
- **AccessibilityService**
- **Gradle / Android Gradle Plugin**
- **Java 17**
- **Termux shell**
- Local storage

## 📄 License

FBResume được phát hành theo **MIT License**.

Xem đầy đủ tại [LICENSE](LICENSE).

## ⚠️ Disclaimer

FBResume là một dự án độc lập và **không liên kết, không được Meta/Facebook xác nhận hoặc tài trợ**.

Facebook và Facebook Lite là các sản phẩm/thương hiệu của Meta Platforms, Inc. FBResume chỉ tương tác với thông tin được hệ điều hành Android expose thông qua AccessibilityService.

## 🤝 Đóng góp

Issue và Pull Request được hoan nghênh.

Khi báo lỗi, nên cung cấp:

- Phiên bản Android.
- Phiên bản Facebook/Facebook Lite.
- Phiên bản FBResume.
- Mô tả bước tái hiện lỗi.
- Log lỗi liên quan nếu có.

Không gửi thông tin tài khoản Facebook, mật khẩu, token hoặc dữ liệu cá nhân vào issue/PR.

## 🔗 Repository

[GitHub — khahdihdz/fbresume](https://github.com/khahdihdz/fbresume)
