<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  Each link is shown only when the user holds the role behind it, so the navigation never offers
  something that would be refused on clicking.
--%>
<nav class="ams-nav">
  <ul>
    <c:if test="${pageContext.request.isUserInRole('INT_SEARCH_CUSTOMERS')}">
      <li><a href="${pageContext.request.contextPath}/Home.action">Home</a></li>
    </c:if>
    <%-- Resumes the order in progress; the flow sends the user home if there is none. --%>
    <c:if test="${pageContext.request.isUserInRole('INT_CREATE_ORDER')}">
      <li><a href="${pageContext.request.contextPath}/install/Site.action">Install order</a></li>
    </c:if>
    <c:if test="${pageContext.request.isUserInRole('INT_VIEW_ORDER')}">
      <li><a href="${pageContext.request.contextPath}/Report.action">Order activity</a></li>
    </c:if>
  </ul>
</nav>
