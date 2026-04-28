@echo off
setlocal

REM Asegura ejecución desde la raíz del repo donde vive este .bat
cd /d "%~dp0"

set PORT=%1
if "%PORT%"=="" set PORT=8080
set HOST=127.0.0.1

set NO_PROXY=localhost,127.0.0.1,::1
set no_proxy=localhost,127.0.0.1,::1

where py >nul 2>nul
if %ERRORLEVEL%==0 (
  echo FridaMusic site en: http://%HOST%:%PORT%/
  start "" "http://%HOST%:%PORT%/"
  py -3 -m http.server %PORT% --bind %HOST% --directory site
  goto :eof
)

where python >nul 2>nul
if %ERRORLEVEL%==0 (
  echo FridaMusic site en: http://%HOST%:%PORT%/
  start "" "http://%HOST%:%PORT%/"
  python -m http.server %PORT% --bind %HOST% --directory site
  goto :eof
)

echo ERROR: No se encontro Python. Instala Python 3 y vuelve a intentar.
exit /b 1
