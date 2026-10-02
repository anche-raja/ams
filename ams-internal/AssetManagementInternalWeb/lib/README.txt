Runtime Oracle JDBC driver
==========================

The image expects ojdbc8-23.8.0.25.04.jar here. It is not committed: Oracle's licence does not permit
redistributing it, which is also why the build resolves 12.2.0.1 from Maven Central for tests only
and the runtime driver arrives as a file.

Place the jar in this directory before building the image. The Dockerfile copies it into Tomcat's
lib directory, where the connection pool declared in META-INF/context.xml loads it from, so the
version can be changed without touching the application.
