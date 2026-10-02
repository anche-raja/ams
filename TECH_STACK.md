# Tech stack

Every version the AMS build and runtime depend on, and where each one is declared.

Captured **19 September 2026** from `ams-parent-bom/pom.xml`, the Dockerfiles, and the running
containers. Where a version differs between what the build compiles against and what actually runs,
both are listed — those gaps are deliberate and the reason is given.

**Single source of truth:** every dependency version is a property in
[`ams-parent-bom/pom.xml`](ams-parent-bom/pom.xml). No child POM declares a version of its own, so
that file is the only place to change one.

---

## Language and build

| Component | Version | Declared in |
|---|---|---|
| Source / target level | **Java 8** (`1.8`) | `maven.compiler.source` / `.target`; `--release 8` on JDK 9+ via the `jdk9plus` profile |
| Build JDK | Zulu **1.8.0_504** (default) or OpenJDK **21** | `build.sh jdk8` / `build.sh jdk21` (`/usr/libexec/java_home`) |
| Runtime JVM (native) | Zulu 1.8.0_504 and OpenJDK 21, one Apache Tomcat 9 instance each | `tomcat-mac.sh jdk8` / `jdk21` |
| Runtime JVM (container) | Temurin 8 on Apache Tomcat 9 | `tomcat:9.0-jdk8-temurin` |
| Maven | **3.9.16** | developer machine |
| Artifact version | `1.0.0-SNAPSHOT` | `ams.version` |

The build targets Java 8 on whichever JDK runs it. Compiled classes carry major version **52**,
confirmed against a class in the deployed WAR. A JDK 9+ build goes through `--release 8`, so it
compiles against the Java 8 class library rather than merely emitting Java 8 bytecode.

## Frameworks

| Component | Version |
|---|---|
| **Struts** | **6.8.0** |
| OGNL | 3.3.5 |
| FreeMarker | 2.3.33 |
| **Spring Framework** | **5.3.39** |
| **Spring Security** | **5.3.13.RELEASE** |
| Spring Security OAuth2 | 2.5.2.RELEASE |
| Jackson | 2.20.2 |
| Log4j2 | 2.26.0 |

## Web tier

| Component | Version | Notes |
|---|---|---|
| Servlet API | **3.1.0** | `javax.*`, `provided` scope, `web-app_3_1` |
| JSTL | 1.2 (`jstl:jstl`) | bundled in the WAR - Tomcat ships none |
| javax.annotation | 1.3.2 | `provided` |
| Apache Tomcat (native) | **9.0.122** | `tomcat-mac.sh` / `tomcat.sh` (`TOMCAT_VERSION`) |
| Apache Tomcat (container) | **9.0** | `tomcat:9.0-jdk8-temurin` |
| JNDI pool | Tomcat DBCP2, `jdbc/amsInternalDS`, 5-10 connections, 30 s wait | `META-INF/context.xml` |
| Dojo Toolkit | **1.17.3** | vendored, ~11,000 files, restored per environment |

## Data

| Component | Version | Notes |
|---|---|---|
| **H2 (runtime + tests)** | **1.3.176** | embedded, file mode; no server, no install |
| Oracle server *(unwired)* | 26ai Free, 23.26.2.0.0 | `docker compose up` only |
| JDBC driver — runtime | **ojdbc8 23.8.0.25.04** | copied into the image; supplied per environment |
| JDBC driver — build/test | ojdbc8 **12.2.0.1** | `ojdbc8.version`, test scope only |
| H2 — tests | 1.3.176 | in-memory, recreated per test JVM, same DDL as runtime |

The two driver versions are intentional and documented in the Dockerfile: the runtime driver matches
the 23ai server it talks to, while the build compiles and tests against the older one. They never
have to agree. `ojdbc8` rather than `ojdbc11` is historical - the Java 8 base image required it - and is
unchanged because the runtime driver is supplied per environment rather than by the build.

## Libraries

| Component | Version |
|---|---|
| commons-lang | **2.5** (`org.apache.commons.lang`) |
| commons-lang3 | **3.17.0** (`org.apache.commons.lang3`) |
| commons-io | 2.17.0 |
| commons-collections4 | 4.5.0 |
| commons-validator | 1.4.0 |
| commons-text | 1.12.0 |
| json-lib / ezmorph | 2.4 / 1.0.6 |
| AspectJ | 1.7.4 |

Both commons-lang generations are on the classpath at once and both are used.

## Test

| Component | Version |
|---|---|
| JUnit | **4.12** |
| Mockito | **1.9.5** (`org.mockito.Matchers`, `MockitoJUnitRunner`) |
| JaCoCo | 0.8.5 |

## Maven plugins

| Plugin | Version | Notes |
|---|---|---|
| maven-war-plugin | **3.4.0** | original was 2.6 |
| maven-ear-plugin | **3.3.0** | original was 2.8 |
| wildfly-maven-plugin | 2.0.2.Final | |
| buildnumber-maven-plugin | 1.3 | |
| maven-checkstyle-plugin | 2.15 (Checkstyle 5.6) | |

The war and ear plugins are the one place the original pinned versions could not be kept: 2.6 and
2.8 date from 2015 and fail to load under Maven 3.9 with a Plexus API incompatibility, so the build
would not run at all. The packaging they produce is the same. The original values are recorded in
the BOM so the change stays visible.

## Containers

| Service | Image | Platform |
|---|---|---|
| `oracle` | `gvenzl/oracle-free:23-slim` | native arm64 |
| `app` | built from `ams-internal/AssetManagementInternalWeb/Dockerfile` (`tomcat:9.0-jdk8-temurin`) | native arm64 |

Both containers pin `TZ=America/New_York`, which has to match `-Duser.timezone` or `TRUNC(x) =
TRUNC(SYSDATE)` day comparisons shift near midnight.

## Modules

Three reactors, built in order by `build.sh`:

```
ams-parent-bom          the BOM; every version property lives here
ams-common              AssetManagementSharedCommon
                        AssetManagementNetworkValidation
                        AssetManagementSharedServices
ams-internal            AssetManagementInternalCommon
                        AssetManagementInternalServices
                        AssetManagementInternalWeb
                        AssetManagementInternalEar
```

---

## Upgrade targets and what blocks them

This section exists because the project is used as input to an automated upgrade. It records the
intended destination and, more usefully, the order the work has to happen in.

| From | To | Blocked by |
|---|---|---|
| Java 8 | Java 21 | base image; `maven.compiler.source` / `.target`; the JDK the servlet container runs on (Tomcat 9 already runs on 21) |
| Servlet 3.1 `javax.*` | Jakarta EE 9+ `jakarta.*` | nothing — but everything else waits on it |
| Spring 5.3.39 | Spring 6.x | Jakarta namespace |
| Spring Security 5.3.13 | Spring Security 6.x | Spring 6 |
| Struts 6.8.0 | Struts 7.x | Jakarta namespace |
| JUnit 4.12 | JUnit 5 | independent |
| Mockito 1.9.5 | Mockito 5.x | `org.mockito.Matchers` removed; independent |

**The servlet API is the hard edge.** Spring 6 and Struts 7 both require Jakarta EE 9+. Moving
`javax.servlet` → `jakarta.servlet` touches every JSP, filter, listener and `web.xml`, and the
servlet container has to move with it (Tomcat 9 → Tomcat 10.1, which serves only `jakarta.*`).
Neither framework upgrade can start before it, and the two cannot be done independently afterwards
either — Struts 7 needs Spring 6 to be on the same namespace.

**The runtime JVM and the compiler target move together.** Raising `maven.compiler.target` alone
produces classes a Java 8 container cannot load; the Tomcat base image and the JDK the local Tomcat
runs on have to change in the same commit.

**Dojo 1.17.3 pins the content security policy.** `WebSecurityConfig` allows `'unsafe-inline'` and
`'unsafe-eval'` specifically because that build needs both. The policy cannot be tightened until the
toolkit is replaced — though nothing currently loads Dojo, so that replacement is smaller than the
file count suggests.

### Patterns present for the migration to find

Counted across `ams-common` and `ams-internal`, excluding `target/`, after the reduction to the
single install order flow (25 September 2026):

| Pattern | Count |
|---|---|
| `com.opensymphony.*` imports | 14 files |
| Field `@Autowired` | 43 fields |
| `@StrutsParameter` annotations | **0** — every bound setter is unannotated |
| `ModelDriven` actions with their own model | 2 — `InstallOrderBaseAction` (shared by the three install steps) and `JsonErrorAction` |
| `Calendar` / `new Date()` | 7 files |
| `SimpleDateFormat` | 1 file |

Also throughout: hand-written SQL as concatenated `String` constants, XML Struts configuration,
anonymous `RowMapper` implementations, and `javax.annotation` rather than `jakarta.annotation`.

`@StrutsParameter` being absent everywhere is the one that fails silently. Struts 7 stops injecting
request parameters into unannotated setters — an action still compiles, still runs, and simply
receives nulls for every field the form submitted.

### Modernisation showcase: the order activity report

`/Report.action` - `ReportAction`, `OrderActivityServiceImpl`, `ActivityReport`,
`ActivityReportLine` and their three tests - was written on 2 October 2026 in deliberately
pre-Java 9 idioms, so a Java 21 upgrade has one small, self-contained feature to rewrite and the
before/after is easy to show. Each idiom, where it is, and what a current JDK offers instead:

| Java 8 idiom | Where | Java 21 equivalent |
|---|---|---|
| Anonymous `Comparator` class | `OrderActivityServiceImpl.buildReport` | lambda, `Comparator.comparingInt(...).thenComparing(...)` |
| `switch` on a `String` with fall-through `case` labels and `break` | `OrderActivityServiceImpl.summarise` | switch expression, `case "A", "B" ->` |
| Hand-written value class: fields, getters, setters, `equals`, `hashCode`, `toString` | `ActivityReportLine` | `record` |
| `instanceof` followed by a cast | `ActivityReportLine.equals` | pattern matching for `instanceof` |
| `Collections.unmodifiableList(new ArrayList<T>(x))`, `unmodifiableMap(...)` | `ActivityReport` constructor | `List.copyOf`, `Map.copyOf` |
| Explicit type arguments: `new ArrayList<ActivityReportLine>()`, `Collections.<Order>emptyList()` | throughout | diamond `<>`, `var`, `List.of()` |
| Index `for` loops and explicit `Iterator` loops | `ActivityReport`, `OrderActivityServiceImpl` | enhanced `for`, streams, `Collectors.groupingBy` |
| Counting into a `Map` with `containsKey` and `put` | `OrderActivityServiceImpl.count` | `Map.merge` |
| `java.util.Date`, `Calendar` arithmetic, `SimpleDateFormat` | `daysBetween`, `describe`, `ReportAction.toCsv` | `java.time`: `LocalDate`, `ChronoUnit.DAYS.between`, `DateTimeFormatter` |
| `null` checks standing in for an absent value | `earlier`, `isAfter`, `getOldestOpenOrderAgeDays` | `Optional` |
| `StringBuffer` and `StringBuilder` text building, `String.format` | `ReportAction.toCsv`, `describe` | text blocks, `String.formatted` |
| `getBytes("UTF-8")` with a checked `UnsupportedEncodingException` | `ReportAction.exportCsv` | `StandardCharsets.UTF_8` |
| `try { } finally { close() }` | `ReportActionTest.readLines` | try-with-resources |
| Boxing by hand: `Integer.valueOf`, `.intValue()`, `.longValue()` | throughout | autoboxing left to the compiler |
| JUnit 4 (`@RunWith`, `@Before`, message-first `assertEquals`) and Mockito 1 (`org.mockito.Matchers`, `org.mockito.runners.MockitoJUnitRunner`, `@InjectMocks`) | the three tests | JUnit 5 (`@ExtendWith`, `@BeforeEach`) and Mockito 5 |
