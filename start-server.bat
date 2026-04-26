@echo off
setlocal
set PORT=%1
if "%PORT%"=="" set PORT=8080
set NO_PROXY=localhost,127.0.0.1,::1
set no_proxy=localhost,127.0.0.1,::1

echo FridaMusic site en: http://127.0.0.1:%PORT%/
python -m http.server %PORT% --bind 127.0.0.1
