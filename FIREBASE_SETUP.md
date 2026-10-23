# Kết nối Firebase cho APP

1. `APP/app/google-services.json` hiện đã có và package bên trong khớp `com.example.app`. Nếu đổi Firebase project, thay file này bằng file tải từ project mới.
2. Bật **Authentication → Sign-in method → Anonymous** và **Email/Password**. Email/Password được Firebase Auth dùng nội bộ với bí danh kỹ thuật; người dùng không cần nhập hoặc cung cấp email trong giao diện.
3. Tạo Cloud Firestore database và Firebase Storage bucket.
4. Dán nội dung `firestore.rules` vào Firestore Rules và `storage.rules` vào Storage Rules rồi publish. Quy tắc `authIdentifiers` cần được publish để đăng nhập bằng số điện thoại tra được bí danh tài khoản.
5. Sync Gradle và chạy app. Màn hình Trang chủ đọc realtime collection `places`; form Thêm/Chỉnh sửa ghi thông tin vào đó. Ảnh được chọn từ máy sẽ tải lên Storage, còn Firestore lưu URL ảnh. Hồ sơ ghi vào `users` theo Firebase UID; danh sách người nhận dùng `publicProfiles`; thao tác chia sẻ ghi vào `shares`.

Collection `places` có các trường `name`, `category`, `address`, `distance`, `imageUrl`, `description`, `tags`, `ownerUid` và `updatedAt`. Collection `users` lưu hồ sơ riêng theo ID người dùng Firebase (`name`, `username`, `phone`, `bio`, `imageUrl`, `updatedAt`); `publicProfiles` chứa thông tin dùng để hiển thị người nhận chia sẻ. Collection `shares` lưu các lượt chia sẻ. Collection `authIdentifiers` dùng mã SHA-256 của số điện thoại làm ID tài liệu để tìm bí danh đăng nhập; Firestore Rules chỉ cho phép đọc từng tài liệu, không cho liệt kê collection. Danh sách người nhận được tải từ `publicProfiles`; ứng dụng không còn dùng danh sách bạn bè mẫu viết cứng trong Java.
