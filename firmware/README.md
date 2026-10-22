# Mô hình IoT GasGuard (ESP32)

Mô hình thật gồm ESP32, hai cảm biến khí MQ-2 và MQ-135, cảm biến lửa, DHT22, và ba cơ cấu chấp hành: van điện từ, quạt hút, còi. Firmware `esp32_gasguard/esp32_gasguard.ino` gửi phiên lên đúng API của project (`POST /api/gas/batch`), nên phía web không phải sửa gì. Trên web, mô hình là thiết bị **ESP32-REAL-01** (khoá `device-2-api-key`), gắn với khu bếp "Oven corner".

Firmware làm bốn việc:

- **Đo và gom phiên.** Mỗi giây đọc khí và lửa, mỗi 2 giây đọc DHT22. Cứ `SESSION_SECONDS` giây thì lấy trung bình thành một phiên.
- **Tự bảo vệ tại chỗ, không cần mạng.** MQ-2 vượt `LOCAL_GAS_ALARM` thì đóng van, bật quạt, kêu còi. Thấy lửa thì đóng van, kêu còi, không bật quạt. Van giữ đóng cho tới khi web gửi lệnh mở.
- **Gửi lên server.** Phiên chờ trong hàng đợi (tối đa 60 phiên) và được gửi bù khi có mạng lại. Server tự bỏ phiên trùng nhờ số thứ tự.
- **Nhận lệnh từ web.** Đóng hoặc mở van, bật hoặc tắt quạt, tắt còi. Lệnh về trong câu trả lời của server, chậm nhất khoảng 5 giây.

Giờ đo lấy từ server (`server_time` của `/api/health`), nên mô hình chạy được trên Wi-Fi không có Internet, ví dụ điểm phát từ điện thoại trong phòng thi.

## Linh kiện

| Linh kiện | Số lượng | Ghi chú |
|---|---|---|
| ESP32 DevKit V1 (ESP32-WROOM-32) | 1 | Kèm cáp micro-USB truyền dữ liệu |
| Module MQ-2 (chân VCC, GND, DO, AO) | 1 | Khí gas, LPG |
| Module MQ-135 | 1 | Hơi nấu ăn, cồn, khói |
| DHT22 (AM2302) | 1 | Thiếu thì dùng DHT11, sửa `DHT_TYPE` |
| Module cảm biến lửa có chân DO | 1 | Loại hồng ngoại 3 hoặc 4 chân |
| Module relay 2 kênh 5 V có opto | 1 | Kênh 1 cho van, kênh 2 cho quạt |
| Van điện từ 12 V thường đóng (NC) | 1 | **Chỉ dùng với nước hoặc không khí** |
| Quạt 12 V (hoặc 5 V) loại nhỏ | 1 | |
| Còi chip (active buzzer) hoặc module còi | 1 | |
| Nguồn 12 V 2 A và mạch hạ áp 12 V → 5 V (LM2596) | 1 | Hoặc một nguồn 5 V 2 A riêng nếu quạt 5 V và bỏ van |
| Điện trở 10 kΩ và 20 kΩ | 2 mỗi loại | Cầu phân áp cho hai chân AO |
| Diode 1N4007 | 1 | Chống xung ngược của cuộn van |
| Breadboard, dây cắm đực–đực, đực–cái | | |

## Đấu dây

Chân trong firmware trùng với chân ghi ở bảng `GasSensor` của thiết bị 2 trong `03_seed.sql`.

| Linh kiện | Chân linh kiện | Nối tới |
|---|---|---|
| MQ-2 | VCC, GND | 5 V và GND của nguồn ngoài |
| MQ-2 | AO | Cầu phân áp → **GPIO34** |
| MQ-135 | VCC, GND | 5 V và GND của nguồn ngoài |
| MQ-135 | AO | Cầu phân áp → **GPIO35** |
| DHT22 (module 3 chân) | +, −, OUT | 3V3, GND, **GPIO4** |
| Cảm biến lửa | VCC, GND, DO | 3V3, GND, **GPIO27** |
| Relay | VCC, GND | 5 V và GND của nguồn ngoài |
| Relay | IN1, IN2 | **GPIO26** (van), **GPIO25** (quạt) |
| Còi | + (hoặc I/O của module), − | **GPIO33**, GND |
| Van 12 V | dây + | NO của relay kênh 1; COM kênh 1 nối +12 V |
| Quạt 12 V | dây + | NO của relay kênh 2; COM kênh 2 nối +12 V |
| Van, quạt | dây − | GND của nguồn 12 V |

Mấy điểm dễ sai:

- **Cầu phân áp cho AO.** Module MQ chạy 5 V nên chân AO lên tới 5 V, trong khi chân ADC của ESP32 chỉ chịu 3,3 V. Đấu AO → 10 kΩ → điểm giữa → 20 kΩ → GND, rồi nối điểm giữa vào GPIO34 (hoặc GPIO35). Điện trở 20 kΩ nối đất còn giúp kéo chân về 0 khi rời dây AO, và server báo SENSOR_FAULT.
- **Chỉ dùng chân ADC1 (GPIO32–39) cho cảm biến khí.** Các chân ADC2 không đọc được khi Wi-Fi đang chạy.
- **Nối chung GND.** GND của ESP32, nguồn 5 V và nguồn 12 V phải nối với nhau.
- **Không cấp nguồn cảm biến khí từ ESP32.** Mỗi module MQ có cuộn nhiệt tiêu thụ khoảng 150 mA. Cáp USB từ laptop không đủ cho hai MQ, hai relay và Wi-Fi, ESP32 sẽ tự khởi động lại. Cho ESP32 chạy bằng USB, còn cảm biến khí và relay dùng nguồn 5 V ngoài.
- **Diode cho van.** Đấu 1N4007 song song cuộn van, vạch của diode về phía +12 V.
- **Mức kích của module.** Phần lớn relay bật khi chân điều khiển ở mức LOW (`RELAY_ACTIVE_LOW = true`). Cảm biến lửa thường kéo DO xuống LOW khi thấy lửa (`FLAME_ACTIVE_LOW = true`). Module của nhóm khác kiểu thì đổi hai hằng này. Vặn biến trở trên module lửa sao cho đèn DO chỉ sáng khi có lửa.

## Cài phần mềm

1. Cài Arduino IDE 2.
2. File → Preferences → *Additional boards manager URLs*: thêm `https://espressif.github.io/arduino-esp32/package_esp32_index.json`. Vào Boards Manager, cài **esp32 by Espressif Systems**.
3. Library Manager: cài **DHT sensor library** (Adafruit, chọn *Install all* để cài kèm Adafruit Unified Sensor) và **ArduinoJson** (Benoit Blanchon).
4. Mở `esp32_gasguard/esp32_gasguard.ino`. Chọn board **ESP32 Dev Module** và cổng COM. Không thấy cổng thì cài driver CP210x hoặc CH340, tuỳ chip USB trên bo.

Firmware đã được biên dịch thử, không lỗi, không cảnh báo, với gói esp32 bản 2.0.17 và 3.1.1, ArduinoJson 7 (bản 6 cũng được). Phần đọc giờ được thử riêng, và server đã nhận đúng định dạng JSON mà firmware gửi. Firmware **chưa chạy trên bo thật**, nên lần nạp đầu cần theo dõi Serial Monitor.

## Cấu hình và nạp

Sửa khối đầu file `.ino`:

| Hằng | Giá trị |
|---|---|
| `WIFI_SSID`, `WIFI_PASSWORD` | Wi-Fi mà laptop chạy Tomcat cũng đang dùng |
| `SERVER_URL` | `http://<IPv4 của laptop>:8080/PRJ301`. Trên Windows gõ `ipconfig`, lấy dòng *IPv4 Address* của card Wi-Fi |
| `API_KEY` | `device-2-api-key`, hoặc khoá `gg_...` khi đăng ký thiết bị mới ở trang Devices |
| `SESSION_SECONDS` | 10 khi demo; 60 hoặc 600 khi thu dữ liệu cho TransferCheck |

Chuẩn bị mạng:

- **ESP32 chỉ dùng được Wi-Fi 2,4 GHz.** Với điểm phát từ điện thoại, chọn băng tần 2,4 GHz. Trên iPhone, bật *Maximize Compatibility*.
- **Mở cổng 8080 trên Windows Firewall** (chạy cmd bằng quyền Administrator):
  `netsh advfirewall firewall add rule name="Tomcat 8080" dir=in action=allow protocol=TCP localport=8080`
- **Thử mạng từ điện thoại** đang dùng cùng Wi-Fi: mở `http://<IPv4 của laptop>:8080/PRJ301/`. Thấy trang đăng nhập là ESP32 cũng vào được.

Nạp xong, mở Serial Monitor ở 115200. Lần lượt sẽ thấy:

```
GasGuard ESP32. Warming up the gas sensors for 60 s, the valve stays closed.
Wi-Fi connected, IP 192.168.43.120
Clock set from the server: 2026-10-10 16:20:05 UTC. Next session number 1.
Warm-up finished: valve open, sessions start now.
Session closed: MQ-2 612, MQ-135 540, 29.1 C, 71.0 %RH, flame no
Server: saved 1, duplicates 0, new alerts 0
```

Trên web, vào Dashboard và chọn thiết bị **ESP32-REAL-01**. LED trên bo sáng khi server vừa trả lời trong 15 giây gần nhất.

## Chỉnh ngưỡng theo cảm biến thật

Mức mẫu của khu "Oven corner" là 1500/2500 cho MQ-2, tính trên thang ADC 0–4095. Cảm biến thật của nhóm sẽ cho số khác.

1. Cảm biến MQ mới nên được cấp điện 24–48 giờ lần đầu cho ổn định. Mỗi lần bật lên, firmware chờ 60 giây rồi mới tính phiên.
2. Xem giá trị MQ-2 trong không khí sạch trên Serial Monitor, rồi xem giá trị khi thử gas bật lửa (mục dưới).
3. Đặt mức cảnh báo và mức báo động ở khoảng giữa hai giá trị đó. Trang ngưỡng của Thông xong thì sửa trên trang; trước đó chạy trong SSMS (đi qua procedure nên có nhật ký):

   ```sql
   USE PRJ301_GAS;
   DECLARE @zone INT = (SELECT kitchen_zone_id FROM KitchenZone WHERE zone_name = N'Oven corner');
   DECLARE @mq2 INT = (SELECT sensor_type_id FROM SensorType WHERE name = N'MQ-2');
   EXEC ZoneThreshold_Upsert 1, @zone, @mq2, 1000, 1600;  -- 1 = admin; mức cảnh báo, mức báo động (ví dụ)
   ```

4. Sửa `LOCAL_GAS_ALARM` trong firmware bằng mức báo động vừa đặt, rồi nạp lại.

## Kịch bản thử

| # | Thao tác | Trên mô hình | Trên web |
|---|---|---|---|
| 1 | Để không khí sạch | Van mở, LED sáng | Dashboard cập nhật, phiên SAFE |
| 2 | Bấm xả gas bật lửa (**không đánh lửa**) cách MQ-2 2–3 cm trong 2–3 giây | Còi kêu, van đóng, quạt chạy sau 1–2 giây | Phiên MINOR_LEAK hoặc MAJOR_LEAK, cảnh báo kèm bằng chứng |
| 3 | Ở chỗ khác, khi gas đã tan, đưa lửa bật lửa cách cảm biến lửa 20–30 cm | Còi kêu, van đóng, quạt tắt | FLAME_DETECTED |
| 4 | Rút dây AO của MQ-2 | Không kêu | SENSOR_FAULT: "at the bottom of the range" |
| 5 | Rút dây OUT của DHT22 | Không kêu | SENSOR_FAULT: thiếu nhiệt độ, độ ẩm |
| 6 | Trang Control, chọn ESP32-REAL-01, bấm "Turn exhaust fan on" | Relay quạt bật trong khoảng 5 giây | Lệnh chuyển sang SENT |
| 7 | Bấm "Open gas valve" khi cảnh báo còn mở | Van vẫn đóng | Bị từ chối. Sau "Mark incident as handled" thì mở được, relay van bật |
| 8 | Tắt điểm phát Wi-Fi 1 phút rồi bật lại | Còi và van vẫn tự phản ứng | Có mạng lại: các phiên trong lúc mất mạng được gửi bù |

Kịch bản COOKING_FUMES (MQ-135 cao trong khi MQ-2 thấp) khó tạo bằng cảm biến thật, vì MQ-2 cũng phản ứng với cồn và khói. Khi demo, dùng dữ liệu phát lại (`DataReplayer`) để minh hoạ nhãn này.

## An toàn khi thử

- **Thử gas:** chỉ xả gas từ bật lửa, không đánh lửa, mỗi lần 2–3 giây, trong phòng thoáng, không có lửa gần.
- **Thử lửa:** làm ở chỗ khác và sau khi gas đã tan. Không bao giờ thử gas và thử lửa cùng lúc.
- **Van điện từ:** chỉ nối với ống nước hoặc để trống. Tuyệt đối không nối vào đường gas thật.
- **Điện:** chỉ dùng nguồn một chiều 5 V và 12 V. Không đấu relay vào điện 220 V.
- **Khi đấu dây:** rút nguồn trước khi đấu hoặc sửa dây. Cảm biến MQ nóng khi chạy, đừng chạm vào.

## Thu dữ liệu cho TransferCheck (kết quả của phần mở rộng)

Đề yêu cầu thu khoảng 100 phiên có nhãn từ thiết bị thật, rồi chạy `tool/TransferCheck`. Công cụ này huấn luyện trên dữ liệu công khai, thử trên dữ liệu thiết bị, và in chênh lệch F1.

1. Đặt `SESSION_SECONDS = 60`, mất khoảng 1 giờ 40 phút cho 100 phiên. Nếu kịp thời gian, đặt 600 để giống cửa sổ 10 phút của dữ liệu công khai, khi đó mất khoảng 17 giờ.
2. Chạy từng tình huống đủ lâu và ghi lại giờ bắt đầu, giờ kết thúc: không khí sạch (SAFE), có hơi nấu ăn hoặc cồn gần MQ-135 (COOKING_FUMES), thỉnh thoảng xả một chút gas bật lửa (MINOR_LEAK). Mỗi nhãn nên có từ 30 phiên.
3. Gán nhãn theo khoảng giờ bằng `sql/06_label_device_sessions.sql`. Giờ trong CSDL là UTC, tức giờ Việt Nam trừ 7 tiếng. Cũng có thể gán từng phiên trên trang Sessions.
4. Chạy `tool/TransferCheck`, đưa hai dòng F1 vào báo cáo.

Trong báo cáo, cần ghi rõ phần chênh lệch đến từ đâu: dữ liệu công khai đo bằng cảm biến khác, đổi sang thang ADC bằng công thức, và lấy trung bình 10 phút.

## Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Upload báo *Connecting...* rồi lỗi | Giữ nút BOOT trên bo khi bắt đầu upload, hoặc giảm Upload Speed xuống 115200 |
| `cannot reach the server` | Sai `SERVER_URL`, laptop khác mạng, hoặc tường lửa chặn cổng 8080 |
| `the API key was refused` | Sai `API_KEY`, hoặc thiết bị đang INACTIVE |
| MQ-2 luôn đọc 0 | Kiểm tra dây AO và cầu phân áp, nguồn 5 V của module |
| Nhiệt độ, độ ẩm là `nan` | Kiểm tra dây OUT của DHT22; cảm biến trần (không có module) cần điện trở 10 kΩ từ OUT lên 3V3 |
| Relay không kêu | Relay chưa có nguồn 5 V, hoặc sai `RELAY_ACTIVE_LOW` |
| ESP32 tự khởi động lại khi relay đóng | Nguồn yếu: chuyển cảm biến khí và relay sang nguồn 5 V ngoài |
