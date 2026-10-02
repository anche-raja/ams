#!/usr/bin/env bash
# macOS build. Builds the three AMS reactors in dependency order, installing each to the local
# repository before the next one resolves against it, on the JDK you name.
#
#   ./build.sh                      clean install, all three reactors, on JDK 8
#   ./build.sh jdk8                 the same, explicitly
#   ./build.sh jdk21                the same on JDK 21 (still Java 8 bytecode - see below)
#   ./build.sh jdk8 test            run the tests only
#   ./build.sh jdk8 package         package without installing
#   ./build.sh jdk8 run             build, then deploy the WAR to Tomcat 9 and start it (tomcat-mac.sh)
#   ./build.sh jdk8 stop            stop that Tomcat instance
#
# Anything after the goal is passed to Maven:   ./build.sh jdk8 install -DskipTests
# The JDK may be omitted, so ./build.sh test and ./build.sh run still mean what they always did.
#
# JDKs are found through /usr/libexec/java_home, so any vendor's install works (Zulu, Temurin,
# Homebrew openjdk). JAVA_HOME in the calling shell is ignored, deliberately: the build is pinned to
# the JDK you asked for, whatever the shell happens to have on its PATH.
#
# The application is Java 8 (maven.compiler.source/target 1.8 in ams-parent-bom/pom.xml). JDK 8 is
# the deployment JDK and compiles it as-is. A newer JDK activates the parent POM's jdk9plus profile,
# which compiles with --release 8 (so a Java 9+ API cannot slip in) and opens java.lang to the test
# JVM for the pinned Mockito; the class files are version 52 either way. A JDK older than the target
# is refused up front rather than failing inside javac.
#
# build-jdk8.sh / build-jdk21.sh (the Git Bash scripts for Windows) run this on macOS.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"

# ------------------------------------------------------------------------------- arguments
JDK_NAME=jdk8
case "${1:-}" in
  jdk[0-9]*) JDK_NAME="$1"; shift ;;
esac
GOAL="${1:-install}"
shift || true

# ------------------------------------------------------------------------------- toolchain
# jdk8 -> 1.8 for java_home; everything newer is named by its major version.
JAVA_VER="${JDK_NAME#jdk}"
[ "$JAVA_VER" = 8 ] && JAVA_VER=1.8
JAVA_HOME="$(/usr/libexec/java_home -v "$JAVA_VER" 2>/dev/null)" \
  || { echo "!! no JDK $JAVA_VER installed - /usr/libexec/java_home -V lists what is"; exit 1; }
export JAVA_HOME
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"
command -v mvn >/dev/null || { echo "!! no mvn on the PATH - brew install maven"; exit 1; }

# "1.8.0_504" -> 8, "21.0.12.1" -> 21
JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.([0-9]+))?.*/\1 \3/' \
             | awk '{ print ($1 == 1) ? $2 : $1 }')"
# The Java level the build targets: maven.compiler.target ("1.8" -> 8), or release if that is what
# the POM uses instead.
POM="$HERE/ams-parent-bom/pom.xml"
TARGET="$(sed -n 's:.*<maven.compiler.target>\([0-9.]*\)</maven.compiler.target>.*:\1:p' "$POM" | head -1)"
[ -n "$TARGET" ] || TARGET="$(sed -n 's:.*<maven.compiler.release>\([0-9]*\)</maven.compiler.release>.*:\1:p' "$POM" | head -1)"
TARGET="${TARGET#1.}"
if [ -n "$TARGET" ] && [ "$JAVA_MAJOR" -lt "$TARGET" ]; then
  cat <<MSG
!! $JDK_NAME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1)) is older than the Java $TARGET this
   application targets (ams-parent-bom/pom.xml), so it can neither compile nor run it.
   Use ./build.sh jdk$TARGET or newer.
MSG
  exit 1
fi

# ------------------------------------------------------------------------------- build
build_reactors() {
  local goal="$1"; shift
  for reactor in ams-parent-bom ams-common ams-internal; do
    echo "==> [$reactor] mvn clean $goal"
    mvn -B -q -f "$HERE/$reactor/pom.xml" clean "$goal" "$@"
  done
  echo "==> all reactors built"
  local war="$HERE/ams-internal/AssetManagementInternalWeb/target/AssetManagementInternalWeb.war"
  [ -f "$war" ] && echo "    $(du -h "$war" | cut -f1)  $war"
}

echo "JAVA_HOME = $JAVA_HOME"
mvn -version | head -1
echo

case "$GOAL" in
  run)
    build_reactors install "$@"
    exec "$HERE/tomcat-mac.sh" "$JDK_NAME" deploy
    ;;
  stop)
    exec "$HERE/tomcat-mac.sh" "$JDK_NAME" stop
    ;;
  *)
    build_reactors "$GOAL" "$@"
    ;;
esac
