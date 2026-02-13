@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "C:\Users\Argimba\AndroidStudioProjects\eorders"
call "C:\Users\Argimba\AndroidStudioProjects\eorders\gradlew.bat" assembleDebug
echo.
echo === BUILD FINISHED ===
echo APK location (if successful):
dir /s /b "C:\Users\Argimba\AndroidStudioProjects\eorders\app\build\outputs\apk\debug\*.apk" 2>nul
