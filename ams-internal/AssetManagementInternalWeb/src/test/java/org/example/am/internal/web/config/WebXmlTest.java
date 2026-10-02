package org.example.am.internal.web.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks the deployment descriptor, because the whole bootstrap depends on it.
 *
 * <p>Filter order in particular is load-bearing and invisible: security has to run before anything
 * else sees the request, and Struts has to run last because it is what serves it. Getting that
 * order wrong produces an application that starts cleanly and is unauthenticated.</p>
 */
public class WebXmlTest {

    private static Document webXml;

    @BeforeClass
    public static void parse() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        webXml = builder.parse(new File(new File("").getAbsoluteFile(),
                "src/main/webapp/WEB-INF/web.xml"));
    }

    private static List<String> textOf(final String tag) {
        final List<String> values = new ArrayList<String>();
        final NodeList nodes = webXml.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().trim());
        }
        return values;
    }

    private static List<String> filterNamesInOrder() {
        final List<String> names = new ArrayList<String>();
        final NodeList filters = webXml.getElementsByTagName("filter");
        for (int i = 0; i < filters.getLength(); i++) {
            final Element filter = (Element) filters.item(i);
            names.add(filter.getElementsByTagName("filter-name").item(0).getTextContent().trim());
        }
        return names;
    }

    @Test
    public void theRootContextIsBuiltFromRootConfig() {
        assertTrue(textOf("param-value").toString()
                .contains("org.example.am.internal.web.config.RootConfig"));
        assertTrue(textOf("param-value").toString()
                .contains("AnnotationConfigWebApplicationContext"));
    }

    @Test
    public void theDispatcherServletIsBuiltFromServletConfig() {
        assertTrue(textOf("param-value").toString()
                .contains("org.example.am.internal.web.config.ServletConfig"));
        assertTrue(textOf("servlet-class").contains(
                "org.springframework.web.servlet.DispatcherServlet"));
    }

    /** Spring MVC serves only the error pages; everything else is Struts. */
    @Test
    public void theDispatcherServletIsMountedOnlyAtAms() {
        final NodeList mappings = webXml.getElementsByTagName("servlet-mapping");
        assertEquals(1, mappings.getLength());
        final Element mapping = (Element) mappings.item(0);
        assertEquals("ams",
                mapping.getElementsByTagName("servlet-name").item(0).getTextContent().trim());
        assertEquals("/ams/*",
                mapping.getElementsByTagName("url-pattern").item(0).getTextContent().trim());
    }

    /**
     * Security first, so nothing downstream ever sees an unauthenticated request; Struts last,
     * because it is the one that completes the response.
     */
    @Test
    public void securityRunsFirstAndStrutsRunsLast() {
        final List<String> filters = filterNamesInOrder();
        assertEquals("springSecurityFilterChain", filters.get(0));
        assertEquals("struts2", filters.get(filters.size() - 1));
        assertTrue(filters.indexOf("loggingFilter") > filters.indexOf("springSecurityFilterChain"));
        assertTrue(filters.indexOf("loggingFilter") < filters.indexOf("struts2"));
    }

    @Test
    public void everyRequiredListenerIsRegistered() {
        final List<String> listeners = textOf("listener-class");
        assertTrue(listeners.contains(
                "org.springframework.web.context.ContextLoaderListener"));
        assertTrue(listeners.contains(
                "org.example.am.internal.web.listener.AjaxTokenListener"));
        assertTrue(listeners.contains(
                "org.example.am.internal.web.listener.ApplicationVersionListener"));
    }

    @Test
    public void theDataSourceIsDeclaredAsAContainerResource() {
        assertTrue(textOf("res-ref-name").contains("jdbc/amsInternalDS"));
        assertTrue(textOf("res-auth").contains("Container"));
    }

    @Test
    public void theSessionCookieIsHttpOnlyAndSecure() {
        // A session cookie readable from script, or sent in the clear, undoes the rest of the
        // security model.
        assertTrue(textOf("http-only").contains("true"));
        assertTrue(textOf("secure").contains("true"));
    }

    @Test
    public void errorPagesAreConfiguredForTheCodesThatMatter() {
        final List<String> codes = textOf("error-code");
        assertTrue(codes.contains("404"));
        assertTrue(codes.contains("405"));
        assertTrue(codes.contains("500"));
    }

    @Test
    public void theWelcomeFileIsIndexJsp() {
        assertTrue(textOf("welcome-file").contains("index.jsp"));
    }

    /** The static asset filters must not be mapped to everything, or nothing would be cacheable. */
    @Test
    public void theStaticAssetFiltersAreScopedToAssetPaths() {
        final NodeList mappings = webXml.getElementsByTagName("filter-mapping");
        int staticMappings = 0;
        for (int i = 0; i < mappings.getLength(); i++) {
            final Element mapping = (Element) mappings.item(i);
            final String name =
                    mapping.getElementsByTagName("filter-name").item(0).getTextContent().trim();
            final String pattern =
                    mapping.getElementsByTagName("url-pattern").item(0).getTextContent().trim();
            if ("staticContentCacheFilter".equals(name) || "etagFilter".equals(name)) {
                staticMappings++;
                assertTrue(name + " must not be mapped to everything", !"/*".equals(pattern));
            }
        }
        assertEquals(8, staticMappings);
    }
}
