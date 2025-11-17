@echo off
REM Run JavaFX GUI client for Hint Connection Game
REM Requires JAVA_FX_LIB env var pointing to JavaFX SDK lib directory
REM Usage options:
REM   (cmd.exe)   set JAVA_FX_LIB=C:\javafx-sdk-25\lib & run-gui.cmd
REM   (powershell) $env:JAVA_FX_LIB="C:\javafx-sdk-25\lib"; cmd /c run-gui.cmd
REM   Or pass path as first argument: run-gui.cmd C:\javafx-sdk-25\lib

if "%JAVA_FX_LIB%"=="" (
  if "%~1"=="" (
    echo [ERROR] JAVA_FX_LIB not set and no path argument provided.
    echo   Set env var or pass path: run-gui.cmd C:\path\to\javafx\lib
    echo   PowerShell example: $env:JAVA_FX_LIB="C:\javafx-sdk-25.0.1\lib"; cmd /c run-gui.cmd
    exit /b 1
  ) else (
    set "JAVA_FX_LIB=%~1"
  )
)

echo [INFO] Using JAVA_FX_LIB=%JAVA_FX_LIB%

REM Compile core sources if not already
if not exist out (
  echo [INFO] Creating out directory and compiling core classes...
  mkdir out
  javac -d out src\server\*.java
)

echo [INFO] Compiling JavaFX classes...
javac --module-path "%JAVA_FX_LIB%" --add-modules javafx.controls,javafx.fxml -d out src\client\ParsedMessage.java src\client\ClientController.java src\client\GuiClient.java
if errorlevel 1 (
  echo [ERROR] JavaFX compilation failed.
  exit /b 1
)

if not exist out\client mkdir out\client
copy /Y src\client\client.fxml out\client\client.fxml >nul

echo [INFO] Launching GUI client...
java --enable-native-access=javafx.graphics --module-path "%JAVA_FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp out client.GuiClient
