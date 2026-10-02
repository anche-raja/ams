package org.example.am.internal.web.action;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The container's liveness probe.
 *
 * <p>Writes the response itself and returns {@code NONE} rather than forwarding to a JSP: the probe
 * runs every few seconds and there is no reason to spin up the view layer for it. It reads nothing
 * and touches no database, so it stays cheap and cannot fail for a reason unrelated to the
 * application being up.</p>
 *
 * <p>This is the one endpoint permitted without authentication. It is safe to expose because it
 * neither reads nor writes anything: the response is a fixed string.</p>
 */
@Component("HealthAction")
@Scope("prototype")
public class HealthAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    private static final String BODY = "OK";

    public String health() throws Exception {
        final HttpServletResponse response = getServletResponse();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain;charset=UTF-8");
        // Never cached: a cached health check is worse than no health check.
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        final PrintWriter writer = response.getWriter();
        writer.print(BODY);
        writer.flush();
        return Action.NONE;
    }

    @Override
    public String execute() throws Exception {
        return health();
    }
}
