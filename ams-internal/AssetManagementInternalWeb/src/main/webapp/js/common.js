/*
 * Shared browser behaviour.
 *
 * Written against the DOM directly rather than against the vendored toolkit, so that the pieces
 * that matter - above all attaching the AJAX token to every request - do not depend on it.
 */
(function () {
    "use strict";

    /**
     * The per-session token the server issued, rendered into the page by the common header.
     * Every state-changing request has to carry it, or AjaxTokenInterceptor rejects the request.
     */
    function getAjaxToken() {
        var meta = document.querySelector('meta[name="ams-ajax-token"]');
        return meta ? meta.getAttribute("content") : null;
    }

    /**
     * Spring Security's CSRF token and the header name it expects it in.
     *
     * Separate from the AJAX token above: that one is checked by the Struts interceptor, this one
     * by Spring Security's filter, which runs first. A request missing this is refused with a bare
     * 403 before any application code sees it, so there is nothing to report back to the user - it
     * simply has to be sent.
     */
    function getCsrf() {
        var token = document.querySelector('meta[name="ams-csrf-token"]');
        var header = document.querySelector('meta[name="ams-csrf-header"]');
        if (!token || !header) {
            return null;
        }
        return { header: header.getAttribute("content"), token: token.getAttribute("content") };
    }

    /**
     * Posts form data to an application endpoint with both tokens attached.
     *
     * @param {string} url application-relative URL
     * @param {Object} data key/value pairs to send
     * @param {Function} onSuccess called with the parsed JSON body
     * @param {Function} onError called with a message
     */
    function post(url, data, onSuccess, onError) {
        var request = new XMLHttpRequest();
        var body = [];
        var key;
        var csrf;

        data = data || {};
        data.ajaxToken = getAjaxToken();

        for (key in data) {
            if (Object.prototype.hasOwnProperty.call(data, key) && data[key] !== null
                    && data[key] !== undefined) {
                body.push(encodeURIComponent(key) + "=" + encodeURIComponent(data[key]));
            }
        }

        request.open("POST", url, true);
        request.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");

        csrf = getCsrf();
        if (csrf) {
            request.setRequestHeader(csrf.header, csrf.token);
        }
        request.onreadystatechange = function () {
            var parsed;
            if (request.readyState !== 4) {
                return;
            }
            try {
                parsed = JSON.parse(request.responseText);
            } catch (notJson) {
                if (onError) {
                    onError("The server returned an unexpected response.");
                }
                return;
            }
            // The server reports an expired session in the body, because the transport does not
            // reliably surface the status code to this handler.
            if (parsed && parsed.invalidSession) {
                window.location.reload();
                return;
            }
            if (request.status >= 200 && request.status < 300) {
                if (onSuccess) {
                    onSuccess(parsed);
                }
            } else if (onError) {
                onError((parsed && parsed.detail) || "The request could not be completed.");
            }
        };
        request.send(body.join("&"));
    }

    /**
     * Fills the installation appointment picker from its JSON endpoint.
     *
     * The slots are fetched when the page is shown rather than rendered into it, so the places
     * left reflect the calendar now. They are rendered as radio buttons inside the Place order
     * form, so the choice is posted with the rest of it; the server re-checks it against what is
     * on offer at that moment, which is what actually decides.
     */
    function loadSlots(container) {
        var url = container.getAttribute("data-slots-url");
        if (!url) {
            return;
        }
        post(url, {}, function (body) {
            var slots = (body && body.slots) || [];
            var i, slot, label, radio, text;

            container.textContent = "";
            if (slots.length === 0) {
                container.textContent = "No appointments are free in the next few weeks. You can"
                    + " still place the order; operations will book the visit by hand.";
                return;
            }
            for (i = 0; i < slots.length; i++) {
                slot = slots[i];
                label = document.createElement("label");
                label.className = "ams-slot";
                radio = document.createElement("input");
                radio.type = "radio";
                radio.name = "installationTimeslotId";
                radio.value = String(slot.timeslotId);
                radio.checked = slot.selected === true;
                // textContent, never innerHTML: the label comes from the database.
                text = document.createTextNode(" " + slot.day + " - " + slot.label + " ("
                    + slot.placesLeft + (slot.placesLeft === 1 ? " place" : " places") + " left)");
                label.appendChild(radio);
                label.appendChild(text);
                container.appendChild(label);
            }
        }, function (message) {
            container.textContent = message;
        });
    }

    function onReady() {
        var pickers = document.querySelectorAll("[data-slots-url]");
        var i;
        for (i = 0; i < pickers.length; i++) {
            loadSlots(pickers[i]);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", onReady);
    } else {
        onReady();
    }

    window.ams = { post: post, getAjaxToken: getAjaxToken, getCsrf: getCsrf };
}());
