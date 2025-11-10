@echo off
setlocal enabledelayedexpansion

set "versions=1.15 1.16 1.16.2 1.18.2 1.19.3 1.20.5"
set "successList="
set "failList="

for %%v in (%versions%) do (
    echo ==============================
    echo Building Minecraft %%v...
    echo ==============================

    call gradlew build -PmcVersion=%%v
    set "result=!ERRORLEVEL!"

    if !result! equ 0 (
        set "successList=!successList! %%v"
    ) else (
        set "failList=!failList! %%v"
    )
)

echo.
echo ==============================
echo Build summary:
echo Successful builds: !successList!
echo Failed builds: !failList!
echo ==============================
endlocal
