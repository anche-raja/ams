<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title><c:out value="${param.pageTitle}"/> - Asset Management System</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css"/>
  <%--
    Two separate tokens, deliberately.

    The AJAX token is this application's own, issued per session by AjaxTokenListener and checked
    by AjaxTokenInterceptor on the Struts side. The CSRF token is Spring Security's, checked by its
    filter before a request reaches Struts at all. An AJAX POST has to carry both: the first is a
    parameter, the second a header. Miss the CSRF one and the request is refused with a bare 403
    that never reaches the application.
  --%>
  <meta name="ams-ajax-token" content="<c:out value='${ajaxToken}'/>"/>
  <c:if test="${not empty _csrf}">
    <meta name="ams-csrf-token" content="<c:out value='${_csrf.token}'/>"/>
    <meta name="ams-csrf-header" content="<c:out value='${_csrf.headerName}'/>"/>
  </c:if>
</head>
<body>

<c:if test="${not empty nonProductionBanner}">
  <div class="ams-nonprod-banner" role="alert">
    <c:out value="${nonProductionBanner}"/>
  </div>
</c:if>

<header class="ams-header">
  <a class="ams-brand" href="${pageContext.request.contextPath}/Home.action">
    Asset Management System
  </a>
  <div class="ams-header-right">
    <%--
      The customer being acted for, on every page. An order is placed for that customer, and
      ordering for the wrong one is the easiest damaging mistake available here - so it is shown
      permanently rather than only on the screen where it was chosen.

      Plain JSTL against the session rather than Struts tags: the Spring MVC error pages include
      this header too, and a Struts tag outside a Struts request has no value stack to read.
    --%>
    <c:choose>
      <c:when test="${not empty sessionScope.currentCustomer}">
        <span class="ams-current-customer">
          <c:out value="${sessionScope.currentCustomer.displayName}"/>
          &middot; <a href="${pageContext.request.contextPath}/Home.action">change</a>
        </span>
      </c:when>
      <c:otherwise>
        <span class="ams-current-customer ams-current-customer--none">
          No customer selected &middot;
          <a href="${pageContext.request.contextPath}/Home.action">choose</a>
        </span>
      </c:otherwise>
    </c:choose>
    <span class="ams-user">
      <c:if test="${not empty pageContext.request.remoteUser}">
        <c:out value="${pageContext.request.remoteUser}"/>
      </c:if>
    </span>
  </div>
</header>

<jsp:include page="/WEB-INF/common/navigation.jsp"/>
