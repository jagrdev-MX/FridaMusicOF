@echo off
setlocal EnableDelayedExpansion

REM Nuevo launcher local para FridaMusic web (Windows)
cd /d "%~dp0"

set PORT=%1
if "%PORT%"=="" set PORT=8080
set HOST=127.0.0.1
set URL=http://%HOST%:%PORT%/?v=%RANDOM%

set NO_PROXY=localhost,127.0.0.1,::1
set no_proxy=localhost,127.0.0.1,::1

echo ============================================
echo FridaMusic site en: %URL%
echo Nota: abre esa URL (sin /site/) para ver cambios.
echo ============================================

start "" "%URL%"

where py >nul 2>nul
if %ERRORLEVEL%==0 (
  py -3 -m http.server %PORT% --bind %HOST% --directory site
  goto :eof
)

where python >nul 2>nul
if %ERRORLEVEL%==0 (
  python -m http.server %PORT% --bind %HOST% --directory site
  goto :eof
)

echo ERROR: No se encontro Python 3. Instala Python y vuelve a intentar.
exit /b 1
setlocal
set PORT=%1
if "%PORT%"=="" set PORT=8080
set NO_PROXY=localhost,127.0.0.1,::1
set no_proxy=localhost,127.0.0.1,::1

echo FridaMusic site en: http://127.0.0.1:%PORT%/
python -m http.server %PORT% --bind 127.0.0.1
