#!/usr/bin/env bash
# Build pinned to JDK 21. The application is Java 8 (maven.compiler.source/target 1.8 in the parent
# POM); a JDK 21 build activates the POM's jdk9plus profile and compiles with --release 8, so the WAR
# it produces is the same Java 8 bytecode build-jdk8.sh produces. Builds the three AMS reactors in
# dependency order, installing each to the local repository before the next one resolves against it.
#
#   ./build-jdk21.sh              clean install, all three reactors
#   ./build-jdk21.sh test         run the tests only
#   ./build-jdk21.sh package      package without installing
#   ./build-jdk21.sh run          build, then deploy to Tomcat 9 and start it (tomcat.sh jdk21 deploy)
#   ./build-jdk21.sh stop         stop that Tomcat instance
#
# Anything after the goal is passed to Maven:  ./build-jdk21.sh install -DskipTests
#
# On macOS this is an alias for ./build.sh jdk21, which finds the JDK through /usr/libexec/java_home.
# Everything below it is the Git Bash build for the Windows WorkSpaces machine, where the toolchain
# lives in C:\tools. Run tools\setup-env.ps1 first in a fresh session there - it restores C:\tools
# after a WorkSpaces restart.
set -euo pipefail

case "$(uname -s)" in
  Darwin) exec "$(cd "$(dirname "$0")" && pwd)/build.sh" jdk21 "$@" ;;
esac

# Git Bash rewrites anything that looks like a POSIX path (/D, /c/...) when calling a Windows
# program. Every path handed to subst and mvn.cmd below is already in Windows form, so turn it off.
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'

JDK='C:\tools\jdk21'
TOOLS='C:\tools'
MVN="$(cygpath -u "$TOOLS")/maven/bin/mvn.cmd"
REPO_LOCAL="$TOOLS\\m2"

[ -f "$(cygpath -u "$JDK")/bin/javac.exe" ] || { echo "!! no JDK at $JDK - run tools\\setup-env.ps1 first"; exit 1; }
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

mvn() { "$MVN" -B "-Dmaven.repo.local=$REPO_LOCAL" "$@"; }

build_reactors() {
  local goal="$1"; shift
  for reactor in ams-parent-bom ams-common ams-internal; do
    echo "==> [$reactor] mvn clean $goal"
    mvn -f "$ROOT\\$reactor\\pom.xml" clean "$goal" "$@"
  done
  echo "==> all reactors built"
}

echo "JAVA_HOME = $JAVA_HOME"
mvn -version | head -1
echo

case "$GOAL" in
  stop)
    exec "$HERE/tomcat.sh" jdk21 stop
    ;;
  run)
    build_reactors install "$@"
    exec "$HERE/tomcat.sh" jdk21 deploy
    ;;
  *)
    build_reactors "$GOAL" "$@"
    ;;
esac
