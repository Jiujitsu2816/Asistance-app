#!/usr/bin/env sh
# Arranca el sistema completo: backend + ventana para abrir las tres aplicaciones.
set -e
cd "$(dirname "$0")"
JAVA_EXE=java
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_EXE="$JAVA_HOME/bin/java"; fi
if ! "$JAVA_EXE" -version >/dev/null 2>&1; then
  echo "No se encuentra Java. Instala un JDK 21 o superior y define JAVA_HOME." >&2
  exit 1
fi
JAR=lanzador/target/presencia-lanzador-app.jar
if [ ! -f "$JAR" ]; then
  echo "Primera ejecucion: compilando el proyecto, puede tardar unos minutos..."
  ./mvnw -q clean package -DskipTests
fi
exec "$JAVA_EXE" -jar "$JAR"
