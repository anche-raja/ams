package org.example.am.internal.web.action;

import org.example.am.internal.web.model.JsonErrorModel;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The error answer for callers expecting JSON.
 *
 * <p>An expired session is reported in the body rather than only as a status code, because the
 * vendored Dojo transport does not surface the status to the grid's error handler. Without the flag
 * a grid whose session had gone would simply spin.</p>
 */
@Component("JsonErrorAction")
@Scope("prototype")
public class JsonErrorAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    private JsonErrorModel model = JsonErrorModel.invalidSession();

    @Override
    public JsonErrorModel getModel() {
        return model;
    }

    public String invalidSession() throws Exception {
        model = JsonErrorModel.invalidSession();
        return Action.SUCCESS;
    }

    /**
     * A generic failure. The message is deliberately fixed rather than derived from the exception,
     * so nothing internal is echoed to the browser.
     */
    public String jsonError() throws Exception {
        model = new JsonErrorModel(false, "REQUEST_FAILED");
        model.setDetail("The request could not be completed. Please try again.");
        return Action.SUCCESS;
    }

    @Override
    public String execute() throws Exception {
        return invalidSession();
    }
}
