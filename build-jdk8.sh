#!/usr/bin/env bash
# Git Bash build pinned to JDK 8: builds the three AMS reactors in dependency order, installing
# each to the local repository before the next one resolves against it.
#
#   ./build-jdk8.sh              clean install, all three reactors
#   ./build-jdk8.sh test         run the tests only
#   ./build-jdk8.sh package      package without installing
#   ./build-jdk8.sh run          build, then run the application natively on Liberty
#   ./build-jdk8.sh stop         stop a natively running server
#
# Anything after the goal is passed to Maven:  ./build-jdk8.sh install -DskipTests
#
# JDK 8 is what the application deploys onto, so this compiles, packages and runs Liberty on the
# same JDK as production. The tests are the exception: the parent POM hardcodes --add-opens into
# the Surefire argLine for the pinned Mockito 1.9.5, and Java 8 refuses to start with that flag.
# Surefire is pointed at JDK 21's java for the forked test JVM instead - the code under test is
# still the Java 8 bytecode compiled here.
#
# Run tools\setup-env.ps1 first in a fresh session - it restores C:\tools after a WorkSpaces restart.
set -euo pipefail

# Git Bash rewrites anything that looks like a POSIX path (/D, /c/...) when calling a Windows
# program. Every path handed to subst and mvn.cmd below is already in Windows form, so turn it off.
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'

JDK='C:\tools\jdk8'
TEST_JDK='C:\tools\jdk21'
TOOLS='C:\tools'
MVN="$(cygpath -u "$TOOLS")/maven/bin/mvn.cmd"
REPO_LOCAL="$TOOLS\\m2"

[ -f "$(cygpath -u "$JDK")/bin/javac.exe" ] || { echo "!! no JDK at $JDK - run tools\\setup-env.ps1 first"; exit 1; }
[ -f "$(cygpath -u "$TEST_JDK")/bin/java.exe" ] || { echo "!! no JDK at $TEST_JDK for the tests - run tools\\setup-env.ps1 first"; exit 1; }
[ -f "$MVN" ] || { echo "!! no Maven at $TOOLS\\maven - run tools\\setup-env.ps1 first"; exit 1; }

export JAVA_HOME="$JDK"
export PATH="$(cygpath -u "$JDK")/bin:$(dirname "$MVN"):$PATH"

HERE="$(cd "$(dirname "$0")" && pwd)"
GOAL="${1:-install}"
shift || true

# The repo's own path is 120 characters deep and its longest file is past MAX_PATH, so everything
# is built through a subst drive. Re-created here because a mapping belongs to the logon session
# that made it.
#
# Re-mapped whenever it points anywhere but this checkout, not only when it is missing: FORGE
# builds a copy of the migrated tree with this script, and a drive still mapped to the original
# would quietly build the original instead. AMS_BUILD_DRIVE moves that build off X:, so it leaves
# the everyday mapping alone.
ROOT="${AMS_BUILD_DRIVE:-X:}"
HERE_WIN="$(cygpath -w "$HERE")"
MAPPED="$(subst | tr -d '\r' | grep -i "^${ROOT}\\\\: => " | sed 's/^.* => //' || true)"
if [ "${MAPPED,,}" != "${HERE_WIN,,}" ]; then
  subst "$ROOT" /D >/dev/null 2>&1 || true
  subst "$ROOT" "$HERE_WIN"
  [ -f "$(cygpath -u "$ROOT")/ams-parent-bom/pom.xml" ] || { echo "!! could not map $ROOT to $HERE"; exit 1; }
  echo "mapped $ROOT -> $HERE_WIN"
fi

mvn() { "$MVN" -B "-Dmaven.repo.local=$REPO_LOCAL" "-Djvm=$TEST_JDK\\bin\\java.exe" "$@"; }

build_reactors() {
  local goal="$1"; shift
  for reactor in ams-parent-bom ams-common ams-internal; do
    echo "==> [$reactor] mvn clean $goal"
    mvn -f "$ROOT\\$reactor\\pom.xml" clean "$goal" "$@"
  done
  echo "==> all reactors built"
}

WEB_POM="$ROOT\\ams-internal\\AssetManagementInternalWeb\\pom.xml"

# Embedded H2 is a file, and exactly one JVM may hold it. A server left behind by an earlier run
# keeps the lock, and the next start fails with "Database may be already in use".
stop_native() {
  mvn -q -f "$WEB_POM" -P native liberty:stop >/dev/null 2>&1 || true
  powershell.exe -NoProfile -Command \
    "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'wlp.*amsInternal' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" \
    >/dev/null 2>&1 || true
}

echo "JAVA_HOME = $JAVA_HOME  (tests fork on $TEST_JDK)"
mvn -version | head -1
echo

case "$GOAL" in
  stop)
    stop_native
    echo "==> native server stopped"
    ;;
  run)
    build_reactors install "$@"
    stop_native
    echo "==> liberty:dev on http://localhost:9081/AssetManagementInternalWeb (Ctrl-C to stop)"
    mvn -f "$WEB_POM" -P native liberty:dev
    ;;
  *)
    build_reactors "$GOAL" "$@"
    ;;
esac
