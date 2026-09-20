#!/usr/bin/env bash
# Builds the three AMS reactors in dependency order, installing each to the local
# repository before the next one resolves against it.
#
#   ./build.sh              clean install, all three reactors
#   ./build.sh test         run the tests only
#   ./build.sh run          build, then run the application natively - no Docker, no Oracle
#   ./build.sh stop         stop a natively running server
#   ./build.sh docker       build, then bring the whole stack up under docker compose
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}"
export PATH="/opt/homebrew/bin:$JAVA_HOME/bin:$PATH"

HERE="$(cd "$(dirname "$0")" && pwd)"
GOAL="${1:-install}"
shift || true

build_reactors() {
  local goal="$1"; shift
  for reactor in ams-parent-bom ams-common ams-internal; do
    echo "==> [$reactor] mvn clean $goal"
    mvn -q -f "$HERE/$reactor/pom.xml" clean "$goal" "$@"
  done
  echo "==> all reactors built"
}

WEB_POM="$HERE/ams-internal/AssetManagementInternalWeb/pom.xml"

# Embedded H2 is a file, and exactly one JVM may hold it. A server left behind by an earlier run -
# or by a start that failed after the JVM came up - keeps the lock, and the next start fails with
# "Database may be already in use" rather than anything that names the real problem.
stop_native() {
  mvn -q -f "$WEB_POM" -P native liberty:stop >/dev/null 2>&1 || true
  pkill -f 'wlp.*amsInternal' >/dev/null 2>&1 || true
}

if [ "$GOAL" = "stop" ]; then
  stop_native
  echo "==> native server stopped"
  exit 0
fi

if [ "$GOAL" = "run" ]; then
  build_reactors install "$@"
  stop_native
  echo "==> liberty:dev (Ctrl-C to stop)"
  # dev rather than run: it deploys the application loose, from target/classes and src/main/webapp,
  # instead of copying the 37 MB WAR on every cycle - almost all of which is the vendored Dojo tree.
  exec mvn -f "$WEB_POM" -P native liberty:dev
fi

if [ "$GOAL" = "docker" ]; then
  # The image copies target/AssetManagementInternalWeb.war, so the Maven build has to run first.
  build_reactors install "$@"

  WAR="$HERE/ams-internal/AssetManagementInternalWeb/target/AssetManagementInternalWeb.war"
  [ -f "$WAR" ] || { echo "!! $WAR was not produced"; exit 1; }

  # Both are supplied per environment and deliberately not committed; the Dockerfile COPYs them
  # unconditionally, so a missing one fails the image build with a much less obvious message.
  LIB="$HERE/ams-internal/AssetManagementInternalWeb/lib"
  CRT="$HERE/ams-internal/AssetManagementInternalWeb/certs/internal-ca.crt"
  compgen -G "$LIB/ojdbc8-*.jar" >/dev/null \
    || { echo "!! no ojdbc8 jar in $LIB - see the README there"; exit 1; }
  [ -f "$CRT" ] \
    || { echo "!! $CRT is missing - see the README in that directory"; exit 1; }

  echo "==> docker compose up --build"
  exec docker compose -f "$HERE/docker-compose.yml" up --build
fi

build_reactors "$GOAL" "$@"
