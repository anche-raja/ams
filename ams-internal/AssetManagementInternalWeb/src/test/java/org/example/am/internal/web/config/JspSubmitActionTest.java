package org.example.am.internal.web.config;

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
 * Asserts that no JSP submits a form with {@code <s:submit action="...">}.
 *
 * <p>That tag renders {@code name="action:Something"}, which Struts only acts on when
 * {@code struts.mapper.action.prefix.enabled} is true. It is false by default in Struts 6 - and
 * deliberately left false here, for the same reason dynamic method invocation is off: it lets a
 * request name the action it wants to reach.</p>
 *
 * <p>The failure is silent, which is why this test exists. The button renders, the form submits,
 * and the request goes to the form's own action instead of the named one - so "Save for later"
 * quietly places the order, and "Add more rows" quietly moves to the next step. Nothing logs it.
 * Use {@code formaction} on a plain submit button instead: it posts the whole form, CSRF token
 * included, to whichever URL is named.</p>
 */
public class JspSubmitActionTest {

    private static final Path WEBAPP =
            Paths.get(new File("").getAbsolutePath(), "src", "main", "webapp");

    private static final Pattern SUBMIT_WITH_ACTION =
            Pattern.compile("<s:submit\\b[^>]*\\baction\\s*=", Pattern.DOTALL);

    /** JSP comments, stripped before scanning - several of them quote the tag to explain it. */
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    /** The setting this test exists to guard. It must stay off. */
    private static final Pattern ACTION_PREFIX_ENABLED = Pattern.compile(
            "<constant\\s+name=\"struts\\.mapper\\.action\\.prefix\\.enabled\"\\s+value=\"true\"");

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> paths = Files.walk(WEBAPP)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsp"))
                    .collect(Collectors.toList());
        }
    }

    @Test
    public void noJspRoutesASubmitThroughTheActionPrefix() throws IOException {
        final List<String> problems = new ArrayList<String>();
        for (final Path jsp : jsps()) {
            final String body = JSP_COMMENT.matcher(
                    new String(Files.readAllBytes(jsp), StandardCharsets.UTF_8)).replaceAll("");
            final Matcher matcher = SUBMIT_WITH_ACTION.matcher(body);
            while (matcher.find()) {
                problems.add(WEBAPP.relativize(jsp).toString());
                break;
            }
        }
        if (!problems.isEmpty()) {
            fail("<s:submit action=\"...\"> does nothing with the action prefix disabled;"
                    + " use formaction on a plain submit button instead. Found in: " + problems);
        }
    }

    /**
     * Turning the prefix on would make the test above pass for the wrong reason, so the setting is
     * pinned here too.
     */
    @Test
    public void theActionPrefixStaysDisabled() throws IOException {
        final Path strutsXml = Paths.get(new File("").getAbsolutePath(),
                "src", "main", "resources", "struts.xml");
        final String body = new String(Files.readAllBytes(strutsXml), StandardCharsets.UTF_8);
        assertTrue("struts.mapper.action.prefix.enabled must not be turned on",
                !ACTION_PREFIX_ENABLED.matcher(body).find());
    }
}
