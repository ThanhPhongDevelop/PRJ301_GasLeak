@echo off
rem Smoke test of the device API without the UCI data. Run it while the web application is running.
rem Fresh database from 00-03 and no profile fitted yet: expect saved 4, alerts 3 (minor leak, major leak,
rem sensor fault) on the first run, then duplicates 4 on the second run.
set URL=http://localhost:8080/PRJ301
set KEY=device-1-api-key
echo == health
curl.exe -s -H "X-API-Key: %KEY%" %URL%/api/health
echo.
echo == first send
curl.exe -s -H "X-API-Key: %KEY%" -H "Content-Type: application/json" --data-binary @"%~dp0api-sample.json" %URL%/api/gas/batch
echo.
echo == second send, same sequence numbers
curl.exe -s -H "X-API-Key: %KEY%" -H "Content-Type: application/json" --data-binary @"%~dp0api-sample.json" %URL%/api/gas/batch
echo.
echo == wrong key, expect 401
curl.exe -s -o NUL -w "%%{http_code}" -H "X-API-Key: wrong" %URL%/api/health
echo.
pause
