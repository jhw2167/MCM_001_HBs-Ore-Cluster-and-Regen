@echo off
REM Script to copy icon.png to resource directories

REM Create directories if they don't exist
mkdir "common\src\main\resources" 2>nul
mkdir "fabric\src\main\resources" 2>nul
mkdir "forge\src\main\resources" 2>nul

REM Copy icon.png to each directory
copy /Y "icon.png" "common\src\main\resources\"
copy /Y "icon.png" "fabric\src\main\resources\"
copy /Y "icon.png" "forge\src\main\resources\"

echo Icon copied to all resource directories
