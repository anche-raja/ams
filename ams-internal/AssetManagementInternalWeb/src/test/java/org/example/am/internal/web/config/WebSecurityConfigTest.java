package org.example.am.internal.web.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.Filter;

import org.example.am.internal.security.AmsRole;
import org.example.am.internal.security.AmsUser;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.security.WebSealPrincipal;
import org.example.am.internal.service.AmsUserDetailsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Stands the real filter chain up and drives requests through it.
 *
 * <p>This is the check that matters most in the whole build: reading the configuration tells you
 * what was intended, but only running a request through the chain tells you what actually happens.
 * The specific things proved here are that the health endpoint answers without any credentials,
 * that everything else is refused with 403 rather than redirected to a login page that does not
 * exist, and that a request carrying the proxy's headers gets through.</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = WebSecurityConfigTest.TestConfig.class)
public class WebSecurityConfigTest {

    /**
     * The security wiring under test, with the one collaborator that would otherwise need a
     * database replaced by a stub. Everything else - the filter chain, the URL rules, the entry
     * point, the header writers - is the production configuration.
     */
    @Configuration
    @Import({ GlobalSecurityConfig.class, WebSecurityConfig.class })
    static class TestConfig {

        @Bean
        public AmsUserDetailsService amsUserDetailsService() {
            return new AmsUserDetailsService() {

                @Override
                public UserDetails loadUserDetails(final Authentication authentication) {
                    final Object principal = authentication.getPrincipal();
                    final String username = principal instanceof WebSealPrincipal
                            ? ((WebSealPrincipal) principal).getUsername()
                            : String.valueOf(principal);
                    if (username == null || username.trim().length() == 0) {
                        throw new UsernameNotFoundException("no identity");
                    }
                    final Collection<GrantedAuthority> authorities =
                            new ArrayList<GrantedAuthority>();
                    authorities.add(new SimpleGrantedAuthority(SecurityRoleType.ROLE_USER));
                    authorities.add(new AmsRole(SecurityRoleType.INT_SEARCH_ASSETS));
                    return new AmsUser(username, authorities);
                }
            };
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    public void theFilterChainIsBuilt() {
        assertNotNull(springSecurityFilterChain);
    }

    /**
     * The container's monitor polls this with no credentials. If it needed them, every deployment
     * would be reported unhealthy and rolled back.
     */
    @Test
    public void theHealthEndpointAnswersWithoutCredentials() throws Exception {
        assertNotEqualsForbidden(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/health"))
                .andReturn().getResponse());
        assertNotEqualsForbidden(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/health.action"))
                .andReturn().getResponse());
    }

    @Test
    public void theErrorPagesAnswerWithoutCredentials() throws Exception {
        assertNotEqualsForbidden(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Unauthorized.action"))
                .andReturn().getResponse());
        assertNotEqualsForbidden(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/CustomError.action"))
                .andReturn().getResponse());
    }

    /**
     * Everything else is refused, and refused with 403 rather than a redirect: a browser that got
     * here without proxy headers bypassed the proxy, and there is no login page to send it to.
     */
    @Test
    public void anUnauthenticatedRequestToAnythingElseIsForbidden() throws Exception {
        for (final String path : new String[] {
                "/Home.action",
                "/install/Site.action",
                "/install/AppointmentSlots.action",
                "/" }) {
            final MockHttpServletResponse response = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                    .andReturn().getResponse();
            assertEquals(path + " should be refused", HttpStatus.FORBIDDEN.value(),
                    response.getStatus());
            assertTrue(path + " must not redirect to a login page",
                    response.getRedirectedUrl() == null);
        }
    }

    /** A request carrying what the proxy asserts authenticates and is let through. */
    @Test
    public void aRequestWithTheProxyHeadersIsAuthenticated() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Home.action")
                        .header("iv-user", "opsuser")
                        .header("iv-groups", "AMS_INTERNAL_OPERATIONS"))
                .andReturn().getResponse();
        assertTrue("Expected the request to get past security, got " + response.getStatus(),
                response.getStatus() != HttpStatus.FORBIDDEN.value());
    }

    /** The proxy's "not signed in" literal must not authenticate anyone. */
    @Test
    public void theUnauthenticatedLiteralIsStillRefused() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Home.action")
                        .header("iv-user", "Unauthenticated"))
                .andReturn().getResponse();
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    /** The security headers are written on every response, refusals included. */
    @Test
    public void securityHeadersAreOnEveryResponse() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Home.action"))
                .andReturn().getResponse();

        assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertNotNull(response.getHeader("Content-Security-Policy"));
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));

        // The policy allows inline and eval'd script because the vendored Dojo build needs both;
        // everything else is same origin. Asserted so that narrowing it is a deliberate change.
        assertTrue(response.getHeader("Content-Security-Policy").contains("default-src 'self'"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("form-action 'self'"));
    }

    /**
     * HSTS is written only over HTTPS, which is correct: the header is meaningless on a plain
     * request and a browser ignores it there. Both halves are asserted so that a change to the
     * header configuration cannot silently drop it from the secure case.
     */
    @Test
    public void hstsIsWrittenOnSecureRequestsOnly() throws Exception {
        final MockHttpServletResponse plain = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Home.action"))
                .andReturn().getResponse();
        assertTrue("HSTS should not be sent over plain HTTP",
                plain.getHeader("Strict-Transport-Security") == null);

        final MockHttpServletResponse secure = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/Home.action").secure(true))
                .andReturn().getResponse();
        assertNotNull(secure.getHeader("Strict-Transport-Security"));
        assertTrue(secure.getHeader("Strict-Transport-Security").contains("includeSubDomains"));
    }

    /** A POST without a CSRF token is refused. */
    @Test
    public void aPostWithoutACsrfTokenIsRefused() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/install/PlaceOrder.action")
                        .header("iv-user", "opsuser")
                        .header("iv-groups", "AMS_INTERNAL_OPERATIONS"))
                .andReturn().getResponse();
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    private static void assertNotEqualsForbidden(final MockHttpServletResponse response) {
        assertTrue("Expected the request to be permitted, got " + response.getStatus(),
                response.getStatus() != HttpStatus.FORBIDDEN.value());
    }
}
