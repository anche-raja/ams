package org.example.am.internal.web.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Checks the Struts wiring hangs together.
 *
 * <p>Nothing else catches these mistakes until the container starts: a mistyped action class or a
 * result pointing at a JSP that was renamed both deploy perfectly happily and fail on the first
 * click. Walking the configuration here turns that into a build failure.</p>
 */
public class StrutsConfigurationTest {

    private static final File MODULE = new File("").getAbsoluteFile();
    private static final File RESOURCES = new File(MODULE, "src/main/resources");
    private static final File WEBAPP = new File(MODULE, "src/main/webapp");

    /** Action name to the class attribute that declares it, across every configuration file. */
    private static final Map<String, String> ACTION_CLASSES = new LinkedHashMap<String, String>();

    /** Every namespace/actionName pair the configuration exposes. */
    private static final Set<String> ACTION_PATHS = new HashSet<String>();

    /** Every JSP a result points at. */
    private static final List<String> RESULT_PATHS = new ArrayList<String>();

    private static Document rootDocument;

    @BeforeClass
    public static void parseConfiguration() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        // The DTD is published on a remote host; the test must not need the network to run.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(final String publicId, final String systemId) {
                return new InputSource(new java.io.StringReader(""));
            }
        });

        rootDocument = builder.parse(new File(RESOURCES, "struts.xml"));

        final File[] files = RESOURCES.listFiles();
        assertNotNull("No configuration files were found", files);
        for (final File file : files) {
            if (!file.getName().startsWith("struts") || !file.getName().endsWith(".xml")) {
                continue;
            }
            collect(builder.parse(file));
        }
    }

    private static void collect(final Document document) {
        final NodeList packages = document.getElementsByTagName("package");
        for (int p = 0; p < packages.getLength(); p++) {
            final Element pkg = (Element) packages.item(p);
            final String namespace = pkg.getAttribute("namespace");

            final NodeList actions = pkg.getElementsByTagName("action");
            for (int a = 0; a < actions.getLength(); a++) {
                final Element action = (Element) actions.item(a);
                final String name = action.getAttribute("name");
                ACTION_CLASSES.put(name, action.getAttribute("class"));
                ACTION_PATHS.add(namespace + "/" + name);

                final NodeList results = action.getElementsByTagName("result");
                for (int r = 0; r < results.getLength(); r++) {
                    final String body = results.item(r).getTextContent().trim();
                    if (body.endsWith(".jsp")) {
                        RESULT_PATHS.add(body);
                    }
                }
            }

            final NodeList globalResults = pkg.getElementsByTagName("global-results");
            for (int g = 0; g < globalResults.getLength(); g++) {
                final NodeList results = ((Element) globalResults.item(g))
                        .getElementsByTagName("result");
                for (int r = 0; r < results.getLength(); r++) {
                    final String body = results.item(r).getTextContent().trim();
                    if (body.endsWith(".jsp")) {
                        RESULT_PATHS.add(body);
                    }
                }
            }
        }
    }

    @Test
    public void thereIsAConfigurationToCheck() {
        assertFalse(ACTION_CLASSES.isEmpty());
        assertFalse(RESULT_PATHS.isEmpty());
    }

    /**
     * Every {@code class} attribute names a Spring bean, and every one of those beans is an action
     * class that exists. A typo here is otherwise only found on the first click.
     */
    @Test
    public void everyActionClassExists() {
        final List<String> missing = new ArrayList<String>();
        for (final Map.Entry<String, String> entry : ACTION_CLASSES.entrySet()) {
            final String beanName = entry.getValue();
            if (beanName == null || beanName.length() == 0) {
                missing.add(entry.getKey() + " has no class");
                continue;
            }
            try {
                Class.forName("org.example.am.internal.web.action." + beanName);
            } catch (final ClassNotFoundException notFound) {
                missing.add(entry.getKey() + " -> " + beanName);
            }
        }
        if (!missing.isEmpty()) {
            fail("Actions naming a class that does not exist: " + missing);
        }
    }

    /** Every action class is a {@code @Component} with the bean name the configuration uses. */
    @Test
    public void everyActionClassIsAPrototypeScopedComponent() throws Exception {
        final List<String> wrong = new ArrayList<String>();
        for (final String beanName : new HashSet<String>(ACTION_CLASSES.values())) {
            if (beanName == null || beanName.length() == 0) {
                continue;
            }
            final Class<?> actionClass =
                    Class.forName("org.example.am.internal.web.action." + beanName);

            final org.springframework.stereotype.Component component =
                    actionClass.getAnnotation(org.springframework.stereotype.Component.class);
            final org.springframework.context.annotation.Scope scope =
                    actionClass.getAnnotation(org.springframework.context.annotation.Scope.class);

            if (component == null || !beanName.equals(component.value())) {
                wrong.add(beanName + " is not a @Component named \"" + beanName + "\"");
            }
            // Actions hold per-request state, so a singleton would leak one user's data into
            // another's request.
            if (scope == null || !"prototype".equals(scope.value())) {
                wrong.add(beanName + " is not @Scope(\"prototype\")");
            }
        }
        if (!wrong.isEmpty()) {
            fail(String.valueOf(wrong));
        }
    }

    /** Every named method exists on the class the action points at. */
    @Test
    public void everyActionMethodExists() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(final String publicId, final String systemId) {
                return new InputSource(new java.io.StringReader(""));
            }
        });

        final List<String> missing = new ArrayList<String>();
        for (final File file : RESOURCES.listFiles()) {
            if (!file.getName().startsWith("struts-") || !file.getName().endsWith(".xml")) {
                continue;
            }
            final NodeList actions = builder.parse(file).getElementsByTagName("action");
            for (int a = 0; a < actions.getLength(); a++) {
                final Element action = (Element) actions.item(a);
                final String method = action.getAttribute("method");
                if (method == null || method.length() == 0) {
                    continue;
                }
                final Class<?> actionClass = Class.forName(
                        "org.example.am.internal.web.action." + action.getAttribute("class"));
                try {
                    actionClass.getMethod(method);
                } catch (final NoSuchMethodException notFound) {
                    missing.add(action.getAttribute("class") + "." + method + "()");
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("Actions naming a method that does not exist: " + missing);
        }
    }

    /** Every result points at a JSP that is actually in the web application. */
    @Test
    public void everyResultResolvesToAJspThatExists() {
        final List<String> missing = new ArrayList<String>();
        for (final String path : RESULT_PATHS) {
            if (!new File(WEBAPP, path).isFile()) {
                missing.add(path);
            }
        }
        if (!missing.isEmpty()) {
            fail("Results pointing at JSPs that do not exist: " + missing);
        }
    }

    /** The URLs the deployment checklist names have to be there. */
    @Test
    public void theDocumentedEntryPointsAreMapped() {
        assertTrue(ACTION_PATHS.contains("//Home"));
        assertTrue(ACTION_PATHS.contains("/install/Site"));
        assertTrue(ACTION_PATHS.contains("/install/PlaceOrder"));
        assertTrue(ACTION_PATHS.contains("//health"));
    }

    /**
     * Dynamic method invocation makes every public no-argument method on every action an endpoint,
     * which would bypass the per-method role checks entirely.
     */
    @Test
    public void dynamicMethodInvocationIsOff() {
        assertFalse(Boolean.parseBoolean(getConstant("struts.enable.DynamicMethodInvocation")));
        assertFalse(Boolean.parseBoolean(getConstant("struts.devMode")));
    }

    /** The Struts filter is mapped to everything, so the Spring MVC path has to be excluded. */
    @Test
    public void theSpringMvcPathIsExcludedFromStruts() {
        assertTrue(getConstant("struts.action.excludePattern").contains("/ams/"));
    }

    /** Action beans have to come from Spring, or nothing in them can be autowired. */
    @Test
    public void actionsAreResolvedFromSpring() {
        assertTrue("spring".equals(getConstant("struts.objectFactory")));
    }

    /** Both extensions, so /health answers as well as /health.action. */
    @Test
    public void theEmptyExtensionIsAccepted() {
        final String extensions = getConstant("struts.action.extension");
        assertTrue(extensions.contains("action"));
        assertTrue("The empty extension must be accepted for /health",
                extensions.endsWith(",") || extensions.contains(",,"));
    }

    /** Every sub-configuration is actually included; one left out is silently dead. */
    @Test
    public void everyConfigurationFileIsIncluded() {
        final Set<String> included = new HashSet<String>();
        final NodeList includes = rootDocument.getElementsByTagName("include");
        for (int i = 0; i < includes.getLength(); i++) {
            included.add(((Element) includes.item(i)).getAttribute("file"));
        }

        final List<String> orphaned = new ArrayList<String>();
        for (final File file : RESOURCES.listFiles()) {
            if (file.getName().startsWith("struts-") && file.getName().endsWith(".xml")
                    && !included.contains(file.getName())) {
                orphaned.add(file.getName());
            }
        }
        if (!orphaned.isEmpty()) {
            fail("Configuration files that nothing includes: " + orphaned);
        }
    }

    private static String getConstant(final String name) {
        final NodeList constants = rootDocument.getElementsByTagName("constant");
        for (int i = 0; i < constants.getLength(); i++) {
            final Element constant = (Element) constants.item(i);
            if (name.equals(constant.getAttribute("name"))) {
                return constant.getAttribute("value");
            }
        }
        return "";
    }
}
