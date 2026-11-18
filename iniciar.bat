@echo off
rem Arranca el sistema completo: backend + ventana para abrir las tres aplicaciones.
rem Doble clic en este fichero. Necesita un JDK 21+ (JAVA_HOME o java en el PATH).
setlocal
cd /d "%~dp0"

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 (
  echo No se encuentra Java. Instala un JDK 21 o superior y define JAVA_HOME.
  echo Ejemplo: setx JAVA_HOME "C:\Users\TU_USUARIO\.jdks\openjdk-26"
  pause
  exit /b 1
)

set "JAR=lanzador\target\presencia-lanzador-app.jar"
if not exist "%JAR%" (
  echo Primera ejecucion: compilando el proyecto, puede tardar unos minutos...
  call mvnw.cmd -q clean package -DskipTests
  if errorlevel 1 (
    echo La compilacion ha fallado.
    pause
    exit /b 1
  )
)

"%JAVA_EXE%" -jar "%JAR%"
if errorlevel 1 pause
