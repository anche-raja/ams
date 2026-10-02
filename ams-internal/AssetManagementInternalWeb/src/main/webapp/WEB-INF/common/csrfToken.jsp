<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  The Spring Security CSRF token, as a hidden field.

  Included inside every form that changes state. WebSecurityConfig requires a token on every
  non-safe method through CSRFTokenRequestMatcher, so a form without this is refused with a bare
  403 and no explanation - the request never reaches an action, so nothing in the application gets
  a chance to say why.

  Guarded on _csrf being present rather than assumed: the attribute is set by Spring Security's
  CsrfFilter, and a JSP rendered outside that chain (the 404 page, for instance) would otherwise
  fail with an EL error on an error page, which is the worst possible place for one.

  The AJAX endpoints do not use this - they send the token as a header from js/common.js, read
  from the meta tags in header.jsp.
--%>
<c:if test="${not empty _csrf}">
  <input type="hidden" name="<c:out value='${_csrf.parameterName}'/>"
         value="<c:out value='${_csrf.token}'/>"/>
</c:if>
