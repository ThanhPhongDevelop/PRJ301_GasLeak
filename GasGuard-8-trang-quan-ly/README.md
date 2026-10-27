# GasGuard – PRJ301 Final Project 1

Phát hiện rò rỉ khí gas trong bếp gia đình, có bù ảnh hưởng của nhiệt độ và độ ẩm lên cảm biến. Dự án Java Web (Servlet/JSP, Tomcat 9, SQL Server, NetBeans 13 Ant, JDK 8).

## Trạng thái

- **Giai đoạn 1 (nền tảng)**: CSDL, bảo mật, CRUD mẫu khu bếp, quản lý người dùng, nhật ký kiểm toán.
- **Giai đoạn 2 (luồng dữ liệu và vận hành)**: API nhận phiên từ thiết bị, bù nhiệt độ – độ ẩm, luật cho 6 nhãn, mô hình Weka, cảnh báo hai tầng có bằng chứng, trang phiên, cảnh báo, phiếu bảo trì, điều khiển thiết bị và dashboard tự cập nhật.
- **Giai đoạn 3, phần backend và bảo mật (xong)**: procedure, DAO và kiểm tra form cho nhà, cảm biến khí, ngưỡng theo khu, liên hệ khẩn cấp, thiết bị (kèm cấp khoá API), và ba thực thể dùng chung còn thiếu: loại cảm biến, hiệu chuẩn, hồ sơ bù; tạo tài khoản và đặt lại mật khẩu ở trang Users; trang My account để đổi mật khẩu; tạm khoá đăng nhập sau 5 lần sai; login SQL quyền tối thiểu; khoá ngoại `Alert → GasEvent`.
- **Giai đoạn 3, phần giao diện (xong)**: 8 controller và 16 trang JSP cho 8 thực thể trên (danh sách có tìm kiếm, lọc, phân trang, xuất CSV; thêm, sửa, xoá), menu gom thành Homes, Devices và Admin. Xem mục [Trang quản lý của giai đoạn 3](#trang-quản-lý-của-giai-đoạn-3).
- **Mô hình IoT (giảng viên yêu cầu)**: firmware ESP32 và hướng dẫn lắp đã có trong `firmware/`; còn mua linh kiện, lắp, nạp và chạy thử. Xem mục [Mô hình IoT](#mô-hình-iot-esp32).

| Quy mô tối thiểu của đề | Yêu cầu | Hiện có |
|---|---|---|
| Bảng | 15 | 25 |
| Servlet làm Controller | 12 | 20 |
| Trang JSP | 12 | 32 |
| Stored procedure | 10 | 40 |
| View | 2 | 14 |
| API | 2 | 3 |
| Ca kiểm thử TestNG | 10 | 74 |

## Cài đặt và chạy

1. **CSDL**: trong SSMS chạy `sql/00_reset.sql` → `01_schema.sql` → `02_programmability.sql` → `03_seed.sql`. Giai đoạn 3 đổi cả `01` (chỉ mục, khoá ngoại) và `02`, nên ai đã có CSDL cũ phải chạy lại từ `00_reset.sql`, rồi làm lại các bước nạp dữ liệu công khai bên dưới. Cần SQL Server 2016 SP1 trở lên.
2. **Thư viện**: đã có sẵn trong project và đã khai báo trong `nbproject/project.properties`, không phải tải hay thêm tay. `web/WEB-INF/lib` gồm mssql-jdbc 12.8.1 bản jre8, jstl 1.2, jbcrypt 0.4, gson 2.10.1 và weka 3.8.6; `lib/test` gồm testng 6.9.12 và jcommander 1.71, chỉ dùng khi chạy test, không đóng vào file war. Đề ghi gson 2.8.9 và testng 7.5: hai bản trong project dùng cùng API, đã chạy đủ 74 ca kiểm thử bằng JDK 8. Muốn đổi sang đúng bản của đề thì thay jar và sửa tên trong `project.properties`. Không dùng Maven: đề cấm, và máy phòng thi không có mạng để tải thư viện.
3. **Mở bằng NetBeans 13**: File → Open Project → chọn thư mục `PRJ301_GasLeak`. Nếu báo thiếu server, chọn Resolve và trỏ tới Tomcat 9.
4. **Kiểm tra thư viện**: chuột phải project → Properties → Libraries: tab Compile có 5 jar, tab Compile Tests có thêm testng và jcommander. Nếu NetBeans báo Reference Problems, bấm Resolve và chọn lại đúng file trong project.
5. **Kết nối**: chép `src/java/db.properties.example` thành `src/java/db.properties` rồi sửa cho đúng máy mình (file này không đưa lên GitHub).
6. **Chạy**: Run Project → `http://localhost:8080/PRJ301/`.
7. **Login quyền tối thiểu** (làm sau khi mọi thứ đã chạy được bằng `sa`): đổi mật khẩu trong `sql/05_app_login.sql`, chạy script, kiểm tra hai dòng kết quả cuối (một dòng `OK, error 229`, một dòng nhật ký mới), rồi sửa `db.properties` thành `db.user=gasguard_app` và mật khẩu vừa đặt. Các công cụ ở mục dưới (`GasToCsv`, `CompensationFitter`, `ModelTrainer`, `DataReplayer`) vẫn chạy được bằng login này. Riêng 3 ca kiểm thử giả mạo nhật ký cần `sa`, xem mục Kiểm thử.

Nếu NetBeans không nhận thư mục là project: tạo project mới Java Web → Web Application (Ant, Tomcat 9, Java EE 8, context path `/PRJ301`), rồi chép đè `src/java`, `web`, `test`, `sql`, `scripts` vào.

### Kiểm tra nhanh API (chưa cần dữ liệu UCI)

Khi ứng dụng đang chạy và CSDL vừa tạo từ `00`–`03`, chạy `scripts\api-smoke-test.cmd` (Windows 10 trở lên có sẵn `curl.exe`). Script gửi 4 phiên trong `scripts/api-sample.json`: an toàn, rò nhỏ, rò lớn, thiếu độ ẩm. Kết quả đúng:

| Bước | Kết quả đúng |
|---|---|
| `health` | `{"status":"ok","device_id":1,...}` |
| Gửi lần 1 | `"saved":4`, `"alerts":3` (MINOR_LEAK, MAJOR_LEAK, SENSOR_FAULT) |
| Gửi lần 2, cùng số thứ tự | `"saved":0`, `"duplicates":4` |
| Khoá sai | `401` |

Sau đó trang Alerts có 3 cảnh báo, trang Sessions có 4 phiên. Chạy script sau bước 4 của bảng dưới thì số cảnh báo có thể khác, vì mức cảnh báo đã được học lại từ dữ liệu.

### Nạp dữ liệu công khai, huấn luyện và phát lại (làm đúng thứ tự)

| Bước | Việc | Kết quả |
|---|---|---|
| 1 | Tải bộ dữ liệu UCI mã 362, giải nén `HT_Sensor_dataset.dat` và `HT_Sensor_metadata.dat` vào thư mục project | |
| 2 | Run File `tool/GasToCsv` | `gas_dataset.csv` có thêm cột `phase` (trước, trong, sau lúc đặt mẫu khí) |
| 3 | Chép CSV vào `C:\data\`, chạy `sql/04_public_data.sql` | `PublicSession`: 1.239 phiên có nhãn (885 SAFE, 151 COOKING_FUMES, 203 MINOR_LEAK), mỗi phiên là một cửa sổ 10 phút |
| 4 | Run File `tool/CompensationFitter` | Bảng báo nhầm trước và sau khi bù; lưu hệ số bù cho thiết bị 1; ghi mức cảnh báo cho khu của thiết bị 1. Số để đối chiếu: báo nhầm SAFE 501 → 516, AUC 0,786 → 0,794 |
| 5 | Run File `tool/ModelTrainer` | Bảng 4 thuật toán (hai cách kiểm định chéo), `web/WEB-INF/model/gas_model.bin` và `gas_header.arff`, `model_results.csv`. Số để đối chiếu: theo bản ghi, IBk F1 0,683 |
| 6 | Run Project (deploy lại để nạp mô hình), rồi Run File `tool/DataReplayer` | Dữ liệu chạy qua API như thiết bị thật; dashboard và cảnh báo cập nhật |

Bước 4 phải chạy trước bước 5 vì nó ghi lại cột `compensated_score`. Nếu phát lại trước bước 4, các mức cảnh báo mẫu (1500/2500) không hợp với thang dữ liệu nên sẽ ra rất nhiều cảnh báo. Muốn phát lại từ đầu: `UPDATE PublicSession SET sent = 0;`.

Kiểm tra khoá thiết bị: `curl -H "X-API-Key: device-1-api-key" http://localhost:8080/PRJ301/api/health`

## Tài khoản demo (mật khẩu chung `Demo@123`)

| Username | Vai trò (mã) | Tên hiển thị |
|---|---|---|
| admin | ADMIN | Administrator (Quản trị viên) |
| owner | OPERATOR | Homeowner (Chủ nhà) |
| tech | TECHNICIAN | Technician (Kỹ thuật viên) |
| auditor | AUDITOR | Auditor (Kiểm toán viên) |
| guest | END_USER | Guest viewer (Khách xem) |

- Đổi mật khẩu của mình: bấm tên mình ở góc trên (trang My account).
- Sai mật khẩu 5 lần trong 15 phút thì tài khoản đó tạm bị khoá; thử lại sau 15 phút. Đừng demo việc này bằng `admin` ngay trước lúc quay video.
- Admin tạo tài khoản ở trang Users ("Add a user"). Để trống ô mật khẩu thì hệ thống sinh mật khẩu tạm 12 ký tự và hiện **một lần** trong thông báo. "Reset password" cũng vậy.

Thiết bị: `ESP32-KITCHEN-01` (khoá `device-1-api-key`) là thiết bị phát lại dữ liệu công khai; `ESP32-REAL-01` (khoá `device-2-api-key`) là mô hình IoT thật, để dữ liệu thật không lẫn với dữ liệu phát lại. Thiết bị đăng ký mới (Devices → Register device) nhận khoá dạng `gg_` + 40 ký tự hex, chỉ hiện một lần; CSDL chỉ giữ SHA-256 của khoá. Bấm "New key" ở trang Devices thì khoá cũ hết hiệu lực ngay, nhớ nạp lại firmware với khoá mới.

## Ma trận phân quyền

R = chỉ đọc, RW = đọc và ghi, – = không có. **Cần đối chiếu với bảng trong tệp Quy định chung**; nếu khác, chỉ sửa khối `VALUES` trong `03_seed.sql`.

| Tài nguyên | ADMIN | OPERATOR | TECHNICIAN | AUDITOR | END_USER |
|---|---|---|---|---|---|
| USER, ROLE, AUDIT | RW | – | – | R | – |
| DEVICE, SENSOR_TYPE, PROFILE | RW | R | RW | R | – |
| CALIBRATION | RW | – | RW | R | – |
| MAINTENANCE | RW | R | RW | R | – |
| GASEVENT, ALERT | RW | RW | RW | R | R |
| KITCHENZONE, HOME | RW | RW | R | R | R |
| GAS_SENSOR | RW | R | RW | R | R |
| ZONE_THRESHOLD, EMERGENCY_CONTACT | RW | RW | R | R | – |
| CONTROL (lệnh điều khiển) | RW | RW | RW | R | – |

## Luồng xử lý một phiên

```
ESP32 / DataReplayer ── POST /api/gas/batch (X-API-Key) ──> GasEventApi
   BatchParser      kiểm tra từng phiên; phiên hỏng bị từ chối riêng, phần còn lại vẫn lưu
   CompensationModel   bù nhiệt độ – độ ẩm theo hệ số của thiết bị (OperatingProfile)
   RuleEngine       tầng 1: luật cho 6 nhãn, kèm bằng chứng
   RiskScorer       tầng 2: mô hình Weka (nếu đã huấn luyện)
   GasEvent_Insert  lưu phiên cùng kết quả luật và mô hình (bảng chỉ được thêm, không được sửa)
   AlertService     quyết định cảnh báo → Alert_Raise (kèm AlertEvidence, không lặp trong 30 phút)
<── {"saved", "duplicates", "alerts", "rejected", "commands"}
```

### Bù nhiệt độ – độ ẩm (điểm mới của đề)

Trong không khí sạch, cảm biến MOX vẫn trôi theo nhiệt độ T và độ ẩm RH. `CompensationModel` học độ trôi đó trên các phiên SAFE bằng hồi quy tuyến tính `raw = baseline + bT·(T − Tref) + bRH·(RH − RHref)`, rồi trừ nó khỏi mọi số đo: `compensated = raw − bT·(T − Tref) − bRH·(RH − RHref)`. Đây là dạng tuyến tính đơn giản của ý tưởng khử tương quan trong bài báo mốc so sánh (Huerta và cộng sự, 2016), không phải bản sao phương pháp của bài.

Mức giảm báo nhầm được đo trong `tool.CompensationFitter`: đặt ngưỡng sao cho bắt được 90% phiên rò rỉ, một lần trên giá trị thô và một lần trên giá trị đã bù, rồi so tỉ lệ phiên SAFE và COOKING_FUMES vượt ngưỡng (báo nhầm) và AUC. Dữ liệu được chia theo bản ghi (5 phần), nên hệ số bù và ngưỡng luôn được thử trên bản ghi chúng chưa thấy. Bảng in ra là bảng chính cho phần "đo mức giảm báo nhầm" của báo cáo.

Kết quả trên dữ liệu UCI (chạy thử ngày 11/10/2026): hệ số bù chỉ giải thích 18% độ trôi của MQ-2 trong không khí sạch (R² 0,18). Bù làm giảm báo nhầm trên phiên COOKING_FUMES (59,6% → 51,0%) và tăng nhẹ AUC (0,786 → 0,794), nhưng không giảm báo nhầm trên phiên SAFE (501 → 516 phiên). Báo cáo phải nêu đúng như vậy, không viết là bù làm đường tín hiệu phẳng.

### Luật cho 6 nhãn (`service.RuleEngine`)

Luật đầu tiên khớp sẽ quyết định, nguy hiểm nhất xét trước. Mỗi kết quả kèm bằng chứng (giá trị đo, ngưỡng, khu).

| Thứ tự | Nhãn | Điều kiện |
|---|---|---|
| 1 | FLAME_DETECTED | Cảm biến lửa báo có lửa |
| 2 | SENSOR_FAULT | MQ-2 không dùng được: thiếu số, ở đáy thang đo (rời dây), hoặc đứng yên 5 phiên liền |
| 3 | FLAME_DETECTED | Nhiệt độ ≥ 65 °C |
| 4 | MAJOR_LEAK | MQ-2 (đã bù) ≥ mức báo động của khu |
| 5 | MINOR_LEAK | MQ-2 (đã bù) ≥ mức cảnh báo của khu |
| 6 | SENSOR_FAULT | MQ-135, nhiệt độ hoặc độ ẩm thiếu hoặc ngoài khoảng hợp lệ |
| 7 | COOKING_FUMES | MQ-135 ≥ mức cảnh báo của nó trong khi MQ-2 vẫn thấp |
| 8 | SAFE | Còn lại |

Hai quyết định an toàn: nếu nhiệt độ hoặc độ ẩm hỏng thì dùng giá trị MQ-2 thô thay cho giá trị đã bù, để cảm biến khí hậu hỏng không che mất rò rỉ; MQ-2 chạm đỉnh thang đo được coi là nồng độ rất cao (rò lớn), không coi là lỗi cảm biến.

**Mức của khu lấy từ đâu**: thiết bị → cảm biến `GasSensor` đang ACTIVE và có khu → dòng `ZoneThreshold` của khu đó cùng loại cảm biến. Thiếu một mắt xích thì luật dùng mức mặc định (MQ-2 1500/2500, MQ-135 1200/2200) và bằng chứng ghi "default levels". Khoảng hợp lệ cho luật SENSOR_FAULT lấy từ `min_valid`, `max_valid` của 4 loại cảm biến dựng sẵn. Hệ số bù lấy từ phiên bản mới nhất trong `OperatingProfile` của thiết bị. Sửa ngưỡng, khoảng hợp lệ hay hệ số bù đều có hiệu lực từ lô dữ liệu kế tiếp, không cần deploy lại.

### Hai tầng cảnh báo (`service.AlertService`)

- Luật ra nhãn khác SAFE → cảnh báo nguồn RULE; nếu có mô hình, ý kiến của mô hình được thêm làm bằng chứng.
- Luật không thấy nguy hiểm nhưng mô hình chắc ≥ 80% là rò rỉ → thêm cảnh báo nguồn ML.
- Mô hình chỉ được thêm cảnh báo, không bao giờ xoá cảnh báo của luật.
- Không tạo cảnh báo mới khi thiết bị đã có cảnh báo cùng nhãn chưa xử lý trong 30 phút gần nhất.

### Mô hình học máy (`tool.ModelTrainer`)

In hai bảng cho 4 thuật toán (RandomForest, J48, NaiveBayes, IBk):

- **Kiểm định chéo 10 phần ngẫu nhiên** như đề. Các cửa sổ cùng một bản ghi rất giống nhau, nên chia ngẫu nhiên cho mô hình "thấy đáp án" lúc học và cho số liệu lạc quan.
- **Kiểm định chéo 10 phần theo bản ghi**: mọi cửa sổ của một bản ghi nằm cùng một phần. Đây là số trung thực để đưa vào báo cáo, và mô hình tốt nhất được chọn theo bảng này.
- **Thử bỏ đặc trưng** cho thuật toán tốt nhất: bỏ `compensated_score`, rồi chỉ giữ hai cảm biến khí, để thấy phần đóng góp của việc bù.

Dòng mốc so sánh (Huerta và cộng sự, 2016) phải chép từ chính bài báo, kèm trích dẫn; công cụ không tự tạo số cho dòng này.

Kết quả trên dữ liệu UCI (chạy thử ngày 11/10/2026): chia ngẫu nhiên RandomForest đạt 83,4%, theo bản ghi chỉ còn 69,3%. Tốt nhất theo bản ghi là IBk (F1 0,683, accuracy 70,6%), thấp hơn mốc "luôn đoán SAFE" 71,4% (885/1.239). Vì vậy báo cáo nêu mốc này và dùng recall rò rỉ (39%) cùng tỉ lệ báo nhầm (8,1% phiên SAFE và COOKING_FUMES bị đoán là rò rỉ) thay cho accuracy. Bỏ hẳn nhiệt độ, độ ẩm và giá trị bù, chỉ giữ hai cảm biến khí, cho F1 0,689: trên bản ghi chưa thấy, hai đặc trưng khí hậu không giúp mô hình.

### API cho thiết bị

`POST /api/gas/batch`, header `X-API-Key`:

```json
{"device_id": 1, "sessions": [
  {"seq": 412, "measured_at": "2026-10-08 05:30:00", "mq2_raw": 1830.5, "mq135_raw": 1650.2,
   "temp_c": 27.4, "rh_pct": 61.0, "flame": false}]}
```

- `measured_at` là giờ UTC (`yyyy-MM-dd HH:mm:ss` hoặc ISO 8601 có múi giờ). Tối đa 200 phiên mỗi lần.
- Thiết bị là chủ của khoá; `device_id` trong body nếu có phải trùng với khoá, nếu không trả về 403. Thiết bị INACTIVE bị từ chối như khoá sai.
- Giá trị cảm biến được phép thiếu hoặc `null`: phiên vẫn được lưu và luật báo SENSOR_FAULT. `compensated_score` do thiết bị gửi bị bỏ qua, server tự tính.
- Phản hồi: `{"saved":1,"duplicates":0,"alerts":0,"rejected":[],"commands":[{"id":7,"command":"CLOSE_VALVE"}]}`. Firmware đọc `commands` để đóng van, bật quạt, tắt còi. Khi không có phiên để gửi, thiết bị gọi `POST /api/health` để nhận lệnh.

## Mô hình IoT (ESP32)

Giảng viên yêu cầu nhóm có mô hình IoT chạy được cùng hệ thống, nên phần thiết bị thật không còn là phần mở rộng tuỳ chọn. Phía web đã sẵn sàng: thiết bị `ESP32-REAL-01` có khoá, khu "Oven corner" có cảm biến và ngưỡng, trang Control gửi được lệnh cho van, quạt, còi.

| File | Nội dung |
|---|---|
| `firmware/esp32_gasguard/esp32_gasguard.ino` | Firmware: đo, gom phiên, tự đóng van và kêu còi khi có gas hoặc lửa (không cần mạng), gửi phiên qua API, nhận lệnh từ web, lấy giờ từ server nên chạy được trên Wi-Fi không có Internet |
| `firmware/README.md` | Linh kiện, sơ đồ đấu dây, cài Arduino IDE, cấu hình, 8 kịch bản thử, an toàn khi thử, cách thu 100 phiên có nhãn cho `TransferCheck` |
| `sql/06_label_device_sessions.sql` | Gán nhãn hàng loạt cho các phiên của ESP32 trong một khoảng giờ, qua procedure nên có nhật ký |

Firmware đã được biên dịch thử không lỗi với gói esp32 bản 2.0.17 và 3.1.1, nhưng chưa chạy trên bo thật.

## Các trang

| Trang | Nội dung |
|---|---|
| Dashboard | Đồng hồ nồng độ khí và biểu đồ MQ-2 trước/sau khi bù, tự cập nhật mỗi 5 giây (vẽ bằng canvas, không cần Internet) |
| Alerts | Danh sách lọc, phân trang, xuất CSV; trang chi tiết có bằng chứng, phiên gây ra, nút đóng van, "I am on it", "Mark incident as handled", mở phiếu bảo trì |
| Sessions | Danh sách phiên (kết quả luật, mô hình, nhãn người gán), trang chi tiết so sánh thô và đã bù, gán/xoá nhãn |
| Maintenance | Phiếu bảo trì: lọc, của tôi, xuất CSV, đóng phiếu có ghi chú |
| Control | Khoá điều khiển (một người một lúc), lệnh có hộp xác nhận, lịch sử lệnh; không cho mở van khi còn cảnh báo rò rỉ hoặc lửa chưa xử lý; đăng xuất tự nhả khoá |
| Homes → Homes | Nhà: tìm theo tên hoặc địa chỉ, chủ nhà, số khu bếp và liên hệ (bấm để mở danh sách lọc sẵn); xoá bị chặn khi nhà còn khu bếp hoặc liên hệ |
| Homes → Kitchen zones | Khu bếp: lọc theo nhà, ngưỡng ppm, van của khu |
| Homes → Zone thresholds | Mức cảnh báo và báo động theo khu và loại cảm biến; mỗi cặp một mức, sửa chỉ đổi hai mức; xoá thì luật dùng mức mặc định |
| Homes → Emergency contacts | Liên hệ khẩn cấp theo nhà, thứ tự gọi, nghe từ mức cảnh báo nào trở lên |
| Devices → Devices | Đăng ký ESP32 (khoá API hiện một lần), sửa vị trí, firmware, trạng thái; cấp khoá mới; trực tuyến và lần cuối gửi; xoá bị chặn khi đã có lịch sử |
| Devices → Gas sensors | Cảm biến nối vào thiết bị nào, chân nào, đặt ở khu nào; lọc theo thiết bị, khu, trạng thái |
| Devices → Sensor types | Loại cảm biến và khoảng hợp lệ (ngoài khoảng là SENSOR_FAULT); 4 loại dựng sẵn không đổi tên, không xoá |
| Devices → Calibrations | Hiệu chuẩn theo thiết bị và loại cảm biến, đánh số phiên bản, đánh dấu bản hết hạn |
| Devices → Compensation profiles | Hồ sơ bù nhiệt độ, độ ẩm theo phiên bản; bản mới nhất đang dùng; "Edit as new version" lưu thành bản mới |
| Admin → Users | Tìm, lọc theo vai trò, thêm tài khoản, đổi vai trò, khoá/mở khoá, đặt lại mật khẩu |
| Admin → Audit log | Nhật ký kiểm toán có chuỗi băm SHA-256, kiểm tra giả mạo |
| My account | Thông tin tài khoản, đổi mật khẩu (phải nhập mật khẩu hiện tại) |

## Bảo mật

Chi tiết và bảng mối nguy nằm trong báo cáo (phần Bảo mật). Tóm tắt những gì chạy trong mã:

- **Đăng nhập**: BCrypt (cost 10); username không tồn tại vẫn so với một hash giả để thời gian trả lời như nhau. Sai 5 lần trong 15 phút thì từ chối đăng nhập (đếm `LOGIN_FAILED` trong AuditLog, ghi thêm `LOGIN_THROTTLED`). Đăng nhập và đổi mật khẩu đều đổi session id.
- **Phân quyền**: `SecurityFilter` gắn mỗi servlet với một tài nguyên; GET cần READ, POST cần WRITE, quyền đọc từ CSDL ở mỗi request. Mọi POST cần CSRF token.
- **Mật khẩu tạm và khoá thiết bị**: sinh ở server bằng `SecureRandom`, hiện một lần, chỉ lưu dạng băm (BCrypt cho mật khẩu, SHA-256 cho khoá).
- **CSDL**: `05_app_login.sql` tạo login `gasguard_app` chỉ đọc, ghi dữ liệu và chạy procedure; bị cấm thêm, sửa, xoá AuditLog và HashChain, cấm sửa, xoá GasEvent. Procedure vẫn ghi được nhờ ownership chaining.
- **Soát lỗi tự động**: `SecurityRulesTest` chạy cùng bộ test. Servlet mới chưa gắn tài nguyên, form POST thiếu `csrfToken`, hay trang in thẳng `${param...}` ra HTML đều làm test fail.

## Những điểm đã sửa so với mã mẫu trong đề

Giai đoạn 1:

- **AuditVerifier**: bản mẫu đọc cột NULL thành `"0"` hoặc `"null"`, trong khi SQL `CONCAT` coi NULL là chuỗi rỗng, nên mọi dòng do hệ thống ghi đều bị báo giả mạo nhầm. Bản này đọc NULL thành chuỗi rỗng giống SQL, và phát hiện thêm dòng chèn tay không qua `Audit_Append`.
- **JSP mẫu** in thẳng dữ liệu, dính XSS; đã thay bằng `<c:out>`.
- **API mẫu** tin `device_id` trong body; giờ thiết bị được xác định bằng khoá.
- **KitchenZone mẫu** ghi số 0 cho khoá ngoại rỗng; đã chuyển sang NULL và có khoá ngoại thật.

Giai đoạn 2:

- **Gom phiên theo `induction_id`** chỉ ra đúng 100 phiên, ít hơn "vài trăm" mà chính đề yêu cầu. Bản này chia mỗi bản ghi thành cửa sổ 10 phút như mã tham chiếu (`Features_split.py`).
- **Nhãn của cả bản ghi**: bản mẫu gán nhãn banana/wine cho cả đoạn trước và sau lúc đặt mẫu khí. Bản này chỉ gán nhãn cho đoạn đang có mẫu khí, đoạn trước đó là SAFE, đoạn sau bỏ đi.
- **Chiều và đơn vị**: bộ dữ liệu ghi điện trở (kΩ, giảm khi có khí), ESP32 đọc ADC (tăng khi có khí). Bản này đổi điện trở sang thang ADC bằng công thức cầu phân áp để dữ liệu phát lại giống thiết bị thật.
- **Hệ số bù 0.8/0.3** chỉ là ví dụ; giờ được học từ dữ liệu và đo được mức giảm báo nhầm.
- **TransferCheck** đánh số nhãn theo thứ tự xuất hiện trong từng câu truy vấn, nên nhãn của tập công khai và tập thiết bị có thể lệch chỉ số. `tool.FeatureDataset` dùng một danh sách 6 nhãn cố định cho mọi tập.
- **DataReplayer** đánh số lại từ 1 mỗi lần chạy (lần hai bị coi là trùng hết) và làm mất bộ đệm khi gửi lỗi. Bản này hỏi server số thứ tự cuối, chỉ đánh dấu đã gửi khi server xác nhận, và gửi giờ UTC.
- **ModelTrainer** có thể chọn mô hình theo F1 bằng NaN; đã chặn, và thêm kiểm định theo bản ghi.

Giai đoạn 3:

- **DbContext mẫu** ghi cứng `sa` và mật khẩu trong mã. Bản này đọc `db.properties`, và `05_app_login.sql` tạo login quyền tối thiểu cho ứng dụng.
- **Bảng dùng chung `Alert`** có cột `session_id` nhưng không có khoá ngoại (mỗi đề có bảng phiên riêng). Với đề gas, `01_schema.sql` thêm `FK_Alert_GasEvent` sau khi tạo `GasEvent`.

## Điểm cần thống nhất với giảng viên

- **Bốn thao tác cho "sự kiện"**: bảng `GasEvent` chỉ được thêm. Cách hiểu đang dùng: thêm là thiết bị gửi qua API, xem là danh sách và chi tiết, sửa là gán hoặc đổi nhãn, xoá là gỡ nhãn. Tương tự, "xoá" người dùng là khoá tài khoản.
- **"Một phiên là một sự kiện"** theo đề, nhưng một phiên mỗi bản ghi chỉ cho 100 phiên. Nhóm dùng cửa sổ 10 phút cho dữ liệu công khai và giải thích lý do trong báo cáo.
- **Bốn thao tác cho thực thể dùng chung**: đủ cho người dùng, thiết bị, loại cảm biến, hiệu chuẩn và hồ sơ bù. Các bảng còn lại đang hiểu như sau: `Alert` do hệ thống tạo, sửa là xác nhận hoặc đánh dấu đã xử lý, không xoá; `MaintenanceTicket` mở, xem, đóng kèm ghi chú, không xoá để giữ lịch sử; `AuditLog`, `HashChain` chỉ thêm và xem; `Roles`, `Permissions` là 5 vai trò cố định theo đề, quyền nạp từ `03_seed.sql`; `AlertEvidence` đi kèm cảnh báo. Hồ sơ bù lưu theo phiên bản: sửa là lưu bản mới, chỉ xoá được bản cũ.

## Trang quản lý của giai đoạn 3

Tám trang đi theo cùng một khuôn. GET làm như `KitchenZoneController`: danh sách (tìm, lọc, phân trang), `action=new`, `action=edit&id=...`, `action=export` (CSV có BOM để Excel đọc đúng tiếng Việt). POST có `action=save` (id rỗng là thêm mới) và `action=delete`; riêng Devices có thêm `action=rotateKey`. Mỗi controller có một trang danh sách `*_list.jsp` và một trang form `*_form.jsp`. `HomeController` là bản dễ đọc nhất để xem khuôn này.

| Controller | Tài nguyên | DAO (actor = id người đang đăng nhập) | Form | Danh sách chọn (`LookupDao`) | Lưu ý |
|---|---|---|---|---|---|
| `/HomeController` | HOME | `HomeDao`: `search(q, page, size)`, `listForExport(q)`, `findById(id)`, `save(actor, home)` → id, `delete(actor, id)` | `HomeForm` | `activeUsers()` cho chủ nhà (được bỏ trống) | Xoá bị chặn khi nhà còn khu bếp hoặc liên hệ |
| `/GasSensorController` | GAS_SENSOR | `GasSensorDao`: `search(q, deviceId, zoneId, status, page, size)`, `listForExport(...)`, `findById`, `save(actor, sensor)`, `delete` | `GasSensorForm` | `devices()`, `sensorTypes()`, `zones()` | Mã cảm biến là duy nhất |
| `/ZoneThresholdController` | ZONE_THRESHOLD | `ZoneThresholdDao`: `search(zoneId, sensorTypeId, page, size)`, `listForExport(...)`, `findById`, `save(actor, zoneId, sensorTypeId, warn, alarm)`, `delete` | `ZoneThresholdForm` | `zones()`, `sensorTypes()` | `save` ghi theo cặp (khu, loại cảm biến): cặp đã có thì ghi đè. Trang sửa chỉ cho sửa hai mức, khoá ô khu và loại |
| `/EmergencyContactController` | EMERGENCY_CONTACT | `EmergencyContactDao`: `search(q, homeId, page, size)`, `listForExport(...)`, `findById`, `save(actor, contact)`, `delete` | `EmergencyContactForm`; ô "notify from" lấy từ `NOTIFY_LABELS` | `homes()` | Priority 1 được gọi trước |
| `/DeviceController` | DEVICE | `DeviceDao`: `search(q, status, page, size)`, `findDevice(id)`, `register(actor, code, location, firmware)` → `DeviceKey`, `update(actor, id, location, firmware, status)`, `rotateKey(actor, id)` → `DeviceKey`, `delete(actor, id)` | `DeviceForm`: `validate(form, errors, creating)` | – | Mã thiết bị chỉ nhập lúc đăng ký. Xoá bị chặn khi đã có lịch sử: chuyển INACTIVE thay vì xoá |
| `/SensorTypeController` | SENSOR_TYPE | `SensorTypeDao`: `search(q, page, size)`, `listForExport(q)`, `findById`, `save(actor, type)` → id, `delete` | `SensorTypeForm` | – | MQ-2, MQ-135, DHT22 temperature, DHT22 humidity là loại dựng sẵn (`isBuiltIn()`): sửa được đơn vị và khoảng hợp lệ, không đổi tên, không xoá. Ẩn nút xoá khi `isLocked()` |
| `/CalibrationController` | CALIBRATION | `CalibrationDao`: `search(deviceId, sensorTypeId, page, size)`, `listForExport(...)`, `findById`, `save(actor, calibration)` → id, `delete` | `CalibrationForm` | `devices()`, `sensorTypes()` | Số phiên bản do procedure tự tăng theo cặp thiết bị và loại cảm biến. Trang sửa khoá hai ô đó. Đánh dấu bản hết hạn bằng `isExpired()` |
| `/OperatingProfileController` | PROFILE | `OperatingProfileDao`: `search(deviceId, page, size)`, `listForExport(deviceId)`, `findById`, `save(actor, deviceId, model)` → id, `delete` | `OperatingProfileForm`: `validate(form, errors, basedOn)` trả về `Draft` (thiết bị và hệ số) | `devices()` | Lưu luôn tạo phiên bản mới; bản mới nhất (`isCurrent()`) đang dùng và không xoá được. "Sửa" mở một bản bằng `OperatingProfileForm.of(p)` rồi lưu thành bản mới. Danh sách hiện `getSummary()` và `getSource()` |

Quy tắc mà 8 controller này tuân theo:

1. **Không gọi `AuditDao.append`**: procedure đã ghi nhật ký trong cùng giao dịch, gọi thêm là ghi hai lần.
2. Lỗi từ procedure (`BaseDao.isBusinessRule(e)`, mã từ 50000) có câu thông báo viết sẵn, hiện thẳng cho người dùng được. `ProcErrors.fieldOf(e)` cho biết lỗi thuộc ô nào; trả về `null` thì hiện bằng flash. Lỗi khác ném `ServletException`.
3. `delete(...)` trả `false` khi dòng đã bị người khác xoá: báo "... no longer exists".
4. Khoá API trong `DeviceKey.getApiKey()` chỉ có ở lần gọi `register` hoặc `rotateKey`: hiện ngay một lần bằng flash (giống mật khẩu tạm ở `UserController`), kèm lời nhắc chép vào firmware. Không ghi khoá ra log hay lưu ở đâu khác.
5. Form POST có `csrfToken`; dữ liệu in bằng `<c:out>`, trong thuộc tính dùng `fn:escapeXml`; link menu bọc trong `me.can('RESOURCE', 'READ')`; nút ghi bọc trong `me.can('RESOURCE', 'WRITE')`.
6. `SecurityRulesTest` kiểm tra servlet và form mới, `ProcErrorsTest` kiểm tra bảng lỗi: chạy Test Project trước khi push.
7. Riêng `/OperatingProfileController`: khi lưu từ một bản có sẵn, lấy bản đó bằng `findById(id)` rồi truyền vào `validate(..., basedOn)`; khi tạo mới truyền `null`. Sau đó gọi `save(actor, draft.getDeviceId(), draft.getModel())`.

Một số điểm riêng của từng trang:

- **Zone thresholds, Calibrations, Compensation profiles**: khi sửa, ô khu/thiết bị và loại cảm biến bị khoá trên form, và controller lấy lại hai giá trị đó từ CSDL chứ không tin request, nên không "chuyển" được một bản ghi sang khu hay thiết bị khác.
- **Devices**: thiết bị mới luôn ở trạng thái ACTIVE; trạng thái chỉ đổi ở trang sửa. Khoá API hiện một lần trong thông báo đầu trang, kèm lời nhắc chép vào `API_KEY` của firmware.
- **Sensor types**: tên loại dựng sẵn hiện ở dạng chỉ đọc; nút xoá ẩn khi `isLocked()`.
- **Menu**: link bọc trong `me.can(..., 'READ')`, nhóm Homes, Devices, Admin chỉ hiện khi người dùng đọc được ít nhất một trang trong nhóm.

## Quy ước khi thêm module mới

Nhân bản theo bộ KitchenZone (`KitchenZoneDao` → `KitchenZoneController` → `kitchen_zone_list.jsp` + `kitchen_zone_form.jsp`), và nhớ:

1. Thêm một dòng `RESOURCE_BY_PATH.put("/TenController", "TEN_RESOURCE")` trong `SecurityFilter`, nếu không `SecurityRulesTest` sẽ fail (servlet chỉ cần đăng nhập phải nằm trong danh sách `LOGIN_ONLY` của test).
2. Mọi form POST có `<input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">`, thiếu là bị 403.
3. Mọi dữ liệu người dùng nhập in ra bằng `<c:out value="..."/>`; trong thuộc tính HTML dùng `${fn:escapeXml(...)}`.
4. Validate lại ở server, kể cả khi form đã có `required`.
5. Nhật ký: nếu thao tác ghi đi qua stored procedure có sẵn `Audit_Append` (mọi procedure `*_Save`, `*_Delete` của giai đoạn 3) thì không làm gì thêm; nếu DAO tự chạy câu `INSERT`/`UPDATE` như KitchenZone thì gọi `new AuditDao().append(...)` với diff JSON (dùng `JsonText`).
6. Thêm link vào menu trong `WEB-INF/jspf/header.jspf` (trong nhóm Homes, Devices hoặc Admin nếu hợp), bọc trong `<c:if test="${me.can('RESOURCE', 'READ')}">`, và thêm quyền đó vào điều kiện hiện nhóm.
7. Số và nhãn hiển thị qua hàm `gg:` (`/WEB-INF/gasguard.tld`): `gg:num`, `gg:label`, `gg:time`.
8. Giao diện, tên biến, tên bảng, chú thích trong mã viết bằng tiếng Anh.

## Kiểm thử và mức độ đã kiểm chứng

- 74 ca TestNG: luật cho 6 nhãn, bù nhiệt độ – độ ẩm (cả hồ sơ nhập tay), đọc body API, chuyển đổi tệp UCI, đo báo nhầm, kiểm định theo bản ghi, nạp mô hình Weka, CSV, mật khẩu, khoá thiết bị, chuỗi băm, kiểm tra form, bảng lỗi procedure, tên loại cảm biến dựng sẵn khớp giữa SQL và Java, và soát quy tắc bảo mật trên mã nguồn.
- 4 ca cần SQL Server thật (`AuditTamperDbTest`), tự bỏ qua khi không kết nối được. 3 ca giả mạo nhật ký cần quyền sửa thẳng AuditLog nên chạy bằng `sa`; ca còn lại kiểm tra `gasguard_app` không sửa được nhật ký nên chạy bằng login đó. Muốn chạy đủ 4 ca: chạy Test Project một lần với `db.properties` dùng `sa`, một lần dùng `gasguard_app`.
- Đã kiểm tra khi viết: biên dịch bằng JDK 8; 32 JSP biên dịch bằng Jasper của Tomcat 9 và hiển thị được với dữ liệu giả (16 trang của giai đoạn 3 thử đủ bốn trường hợp: sửa, thêm mới, có lỗi, tài khoản chỉ đọc); cú pháp T-SQL của các script; mọi câu SQL trong Java đối chiếu với bảng, view và số tham số của procedure; logic chia cửa sổ của `04_public_data.sql` chạy thử trên dữ liệu mẫu.
- **Chưa kiểm chứng được**: chưa chạy trên SQL Server thật, và chưa tải được bộ dữ liệu UCI, nên chưa có số accuracy/F1 và mức giảm báo nhầm thật. Lần chạy đầu trên máy nhóm cần theo dõi lỗi, và các bảng số trong báo cáo phải lấy từ kết quả chạy thật.
- Mức báo động MQ-2 do `CompensationFitter` đề xuất (gấp đôi khoảng cách từ nền sạch tới mức cảnh báo) chỉ là giá trị tạm, vì dữ liệu công khai không có MAJOR_LEAK; cần nêu trong báo cáo.
