# Cổng kiểm soát phát hành BlueCruise

Tài liệu này định nghĩa các điều kiện cần đạt trước khi phân phối BlueCruise ra ngoài môi trường phát triển cục bộ. Nội dung dựa trên manifest, cấu hình build, service, quyền Android và bộ test hiện có.

## Cổng 0 - Quyết định phạm vi phân phối

Trước khi phát hành cần chọn rõ kênh phân phối:

- APK debug dùng nội bộ.
- APK release dùng nội bộ.
- Phân phối trực tiếp hoặc theo mô hình doanh nghiệp.
- Google Play, gồm public track hoặc internal testing.

Đường Google Play có tiêu chí chặn nghiêm ngặt hơn vì BlueCruise dùng thực thi nền, overlay và foreground service loại đặc biệt.

## Cổng 1 - Sẵn sàng build

Các lệnh bắt buộc:

```powershell
.\gradlew.bat --no-daemon :domain:test --console=plain
.\gradlew.bat --no-daemon :data:testDebugUnitTest --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --console=plain
.\gradlew.bat --no-daemon :app:assembleRelease --console=plain
```

Kỳ vọng:

- Tất cả lệnh thoát với mã `0`.
- APK release được tạo với định dạng tên `BlueCruise-v<versionName>-<yyyyMMdd_HHmm>.apk`.
- Không còn thay đổi source chưa được duyệt nằm ngoài phạm vi release.

## Cổng 2 - Kiểm thử khói lúc chạy

Kiểm thử khói tối thiểu trên thiết bị hoặc emulator:

- Ứng dụng mở tới màn đăng nhập khi chưa có session.
- Đăng nhập thành công lưu session và mở màn chính.
- Màn chính liệt kê thiết bị Bluetooth đã ghép đôi sau khi quyền được cấp.
- Người dùng chọn được thiết bị mục tiêu.
- Người dùng chọn được file âm thanh local cho slot 1 và slot 2.
- Play thủ công khởi động notification phát media của foreground service.
- Stop dừng hoặc tạm dừng playback đúng kỳ vọng.
- Đăng xuất xóa session và quay về màn đăng nhập.

Lệnh instrumentation:

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --console=plain
```

Không dùng riêng test instrumentation làm bằng chứng cho hành vi production của Bluetooth hoặc Android Auto.

## Cổng 3 - Xác thực Bluetooth và Android Auto

Các kịch bản thiết bị thật bắt buộc trước khi tuyên bố ổn định trên xe:

| Kịch bản | Tín hiệu đạt |
| --- | --- |
| Mục tiêu Bluetooth-only kết nối | Thiết bị không phải mục tiêu bị bỏ qua; mục tiêu chỉ start khi autoplay bật. |
| Mục tiêu ngắt kết nối | Start đang chờ bị hủy; playback dừng hoặc stop verification chạy đúng. |
| Dự phòng khi Bluetooth adapter về `STATE_ON` | Mục tiêu A2DP đã connected vẫn có thể trigger đúng. |
| Mục tiêu Android Auto | Playback chờ readiness hoặc đi đường dự phòng theo chính sách. |
| Mục tiêu aftermarket/OXPRO | Giai đoạn chờ chuẩn bị và dự phòng không treo vô hạn. |
| Reconnect trong stop verification | Stop bị hủy nếu target connected lại. |

Kiểm thử tập trung khuyến nghị:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.receiver.BluetoothConnectionReceiverAndroidAutoTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.receiver.AndroidAutoReadinessProbeTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.receiver.AndroidAutoHandoffSessionStoreTest" --console=plain
```

## Cổng 4 - Xác thực quyền và OEM

Cần kiểm tra trên nhóm thiết bị đại diện:

- Android 12+: hộp thoại quyền Bluetooth và danh sách thiết bị đã ghép đôi.
- Android 13+: quyền âm thanh và quyền notification.
- Android 14: foreground service type cho media playback và special-use service.
- Xiaomi/Redmi/Poco: đường dẫn cài đặt auto-start và tín hiệu sau khi cấp.
- Samsung: fallback cài đặt pin.
- Quyền overlay: floating bubble không được lưu là enabled trước khi quyền được cấp.
- Khôi phục sau boot: keep-alive chỉ restore khi đã bật, có mục tiêu và Bluetooth đang bật.

Kiểm thử tập trung:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.common.AudioPermissionRulesTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.ui.BluetoothFragmentTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.receiver.KeepAliveServiceRestorerTest" --console=plain
```

## Cổng 5 - Xác thực playback

Cần kiểm tra:

- Slot 1 fallback về bundled greeting khi chưa có file custom/server.
- Slot 2 fallback về bundled goodbye khi chưa có file custom/server.
- File local bị mất phải hủy trước khi route exploit hoặc cấu hình player.
- URI Drive/cloud bị từ chối.
- Duplicate start không tạo playback trùng.
- Passive system resume bị chặn khi resume không hợp lệ.
- Resume/play thủ công từ ứng dụng vẫn hoạt động khi người dùng chủ động yêu cầu.
- Audio focus loss/recovery không làm runtime state kẹt.

Kiểm thử tập trung:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.service.AutoPlayMusicServiceTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.service.PlaybackOrchestratorTest" --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.vibegravity.bluecruise.service.PlaybackRuntimeStateStoreTest" --console=plain
```

## Cổng 6 - Xác thực API và dữ liệu

Cần kiểm tra:

- Đăng nhập xử lý input rỗng, 401, lỗi mạng, thiếu token/user ID và thành công.
- Session được lưu và xóa đúng.
- Đăng xuất xóa setting scoped theo user và cache customer-song.
- Đồng bộ customer-song:
  - Tải `hello` và `goodbye`.
  - Ghi cache file an toàn.
  - Đồng bộ thủ công cập nhật active slot.
  - Đồng bộ sau đăng nhập giữ nguyên slot manual.
  - Partial failure được phản hồi.

Kiểm thử tập trung:

```powershell
.\gradlew.bat --no-daemon :data:testDebugUnitTest --tests "com.vibegravity.bluecruise.data.auth.*" --console=plain
.\gradlew.bat --no-daemon :data:testDebugUnitTest --tests "com.vibegravity.bluecruise.data.customer.*" --console=plain
```

## Cổng 7 - Rà soát rủi ro Google Play

Các bề mặt nhạy cảm release hiện tại:

| Bề mặt | Trạng thái hiện tại | Quyết định release cần có |
| --- | --- | --- |
| Cleartext network | `http://103.118.28.117/api` và network config cho phép cleartext tới host này | Ưu tiên HTTPS hoặc chỉ phát hành nội bộ/trực tiếp nếu chưa đổi được. |
| Special-use foreground service | `KeepAliveService` và `FloatingBubbleService` dùng `specialUse` | Cần lý do policy rõ ràng và giá trị hiển thị cho user. |
| Overlay | `SYSTEM_ALERT_WINDOW` cho floating bubble | Cần UX rõ ràng và lý do store review. |
| Miễn tối ưu pin | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Cần giải thích vì sao độ tin cậy Bluetooth nền cần exemption này. |
| Boot receiver | Restore keep-alive sau reboot khi user đã bật | Cần bảo đảm đây là opt-in rõ ràng. |
| Token storage | Access token lưu trong DataStore preferences | Cần duyệt threat model và mức rủi ro chấp nhận. |
| Ghi log | Log debug có thể chứa MAC/runtime state | Cần posture logging phù hợp cho release. |

Quyết định release:

- APK nội bộ: có thể đi tiếp sau khi có bằng chứng build/test/smoke runtime.
- Phân phối trực tiếp/doanh nghiệp: chỉ đi tiếp khi có device matrix và kỳ vọng hỗ trợ rõ ràng.
- Google Play: chưa nên submit cho tới khi các bề mặt rủi ro trên được duyệt/chấp nhận bằng lý do policy-compatible hoặc được thay đổi trong code/config.

## Cổng 8 - Đồng bộ tài liệu

Trước release, cập nhật:

- `README.md`
- `docs/TECHNICAL_ARCHITECTURE.md`
- `docs/MAIN_FLOW.md`
- `docs/DEVICE_OPTIMIZATION.md`
- `PLAN.md`
- `RELEASE_GATE.md`

Docs phải phản ánh:

- Chính sách API base URL hiện tại.
- Quyền Android hiện tại.
- Hành vi foreground service hiện tại.
- Lệnh test hiện tại.
- Rủi ro release hiện tại.

## Quy tắc đóng release

Không đánh dấu release là sẵn sàng nếu thiếu:

- Bằng chứng output command cho build/test.
- Bằng chứng runtime/device cho core flow.
- Ghi chú residual risk rõ ràng cho phần chưa test.
- Product chấp nhận kênh phát hành và posture policy.
