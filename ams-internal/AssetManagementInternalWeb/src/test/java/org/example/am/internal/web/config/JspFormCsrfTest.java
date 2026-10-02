package org.example.am.internal.web.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Asserts that every form which changes state carries the Spring Security CSRF token.
 *
 * <p>This exists because the failure it catches is silent and total. {@code WebSecurityConfig}
 * requires a token on every non-safe method via {@code CSRFTokenRequestMatcher}, and the filter
 * runs before Struts, so a form missing the token is refused with a bare {@code 403} that never
 * reaches an action. Nothing logs it, no test exercised it, and the page renders perfectly right
 * up until the user presses the button — which is exactly how all twenty forms in this application
 * shipped without one.</p>
 *
 * <p>Written against the JSP source rather than a rendered page on purpose: rendering would need a
 * servlet container, and the property being checked is structural.</p>
 */
public class JspFormCsrfTest {

    private static final Path WEBAPP =
            Paths.get(new File("").getAbsolutePath(), "src", "main", "webapp");

    private static final String CSRF_INCLUDE = "/WEB-INF/common/csrfToken.jsp";

    /** A Struts form that is not explicitly a GET. Struts defaults to POST. */
    private static final Pattern STATE_CHANGING_STRUTS_FORM =
            Pattern.compile("<s:form\\b(?![^>]*method=\"get\")[^>]*>");

    /** A hand-written form declaring POST, whose opening tag may wrap across lines. */
    private static final Pattern STATE_CHANGING_PLAIN_FORM =
            Pattern.compile("<form\\b[^>]*method=\"post\"[^>]*>", Pattern.DOTALL);

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> paths = Files.walk(WEBAPP)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsp"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(final Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int count(final Pattern pattern, final String text) {
        final Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    @Test
    public void thereAreJspsToCheck() throws IOException {
        assertFalse("no JSPs found under " + WEBAPP, jsps().isEmpty());
    }

    @Test
    public void theCsrfFragmentExists() {
        assertTrue(CSRF_INCLUDE + " is missing",
                WEBAPP.resolve("WEB-INF/common/csrfToken.jsp").toFile().isFile());
    }

    /**
     * Every state-changing form includes the token fragment, once per form.
     */
    @Test
    public void everyStateChangingFormIncludesTheCsrfToken() throws IOException {
        final List<String> problems = new ArrayList<String>();

        for (final Path jsp : jsps()) {
            final String body = read(jsp);
            final String name = WEBAPP.relativize(jsp).toString();

            final int forms = count(STATE_CHANGING_STRUTS_FORM, body)
                    + count(STATE_CHANGING_PLAIN_FORM, body);
            if (forms == 0) {
                continue;
            }

            final int includes = count(Pattern.compile(Pattern.quote(CSRF_INCLUDE)), body);
            if (includes < forms) {
                problems.add(name + ": " + forms + " state-changing form(s) but "
                        + includes + " CSRF include(s)");
            }
        }

        if (!problems.isEmpty()) {
            fail("Forms that would be refused with 403 at runtime:\n  "
                    + String.join("\n  ", problems));
        }
    }

    /**
     * A GET form must NOT carry the token: it would be appended to the query string, and from
     * there into browser history, bookmarks, referer headers and the access log.
     */
    @Test
    public void getFormsDoNotLeakTheTokenIntoTheQueryString() throws IOException {
        final Pattern getForm = Pattern.compile("<s:form\\b[^>]*method=\"get\"[^>]*>");
        final List<String> problems = new ArrayList<String>();

        for (final Path jsp : jsps()) {
            final String body = read(jsp);
            final Matcher matcher = getForm.matcher(body);
            while (matcher.find()) {
                // Look only at what follows this form tag, up to the next 200 characters, which
                // comfortably covers an include placed as the first child.
                final int from = matcher.end();
                final String following = body.substring(from, Math.min(body.length(), from + 200));
                if (following.contains(CSRF_INCLUDE)) {
                    problems.add(WEBAPP.relativize(jsp).toString());
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("GET forms must not carry a CSRF token: " + problems);
        }
    }

    /**
     * The AJAX transport sends the token as a header, so the page has to publish it — and the
     * header name too, since Spring Security's is configurable.
     */
    @Test
    public void thePageExposesTheTokenToScript() throws IOException {
        final String header = read(WEBAPP.resolve("WEB-INF/common/header.jsp"));
        assertTrue("header.jsp must publish the CSRF token for js/common.js",
                header.contains("ams-csrf-token"));
        assertTrue("header.jsp must publish the CSRF header name",
                header.contains("ams-csrf-header"));

        final String script = read(WEBAPP.resolve("js/common.js"));
        assertTrue("common.js must read the CSRF meta tags",
                script.contains("ams-csrf-token") && script.contains("ams-csrf-header"));
        assertTrue("common.js must set the CSRF header on its requests",
                script.contains("setRequestHeader(csrf.header, csrf.token)"));
    }
}
