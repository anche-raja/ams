<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  The install order's progress bar.

  Included by each step with its own number, e.g.:
      <jsp:include page="/WEB-INF/common/installSteps.jsp">
        <jsp:param name="step" value="2"/>
      </jsp:include>

  Steps already reached are links, so going back to change something does not mean starting
  again; steps not yet reached are plain text, because their actions would only bounce a user who
  has not filled in what they depend on.
--%>
<c:set var="current" value="${param.step}"/>
<c:set var="furthest">
  <c:choose>
    <c:when test="${not empty furthestStepReached}">${furthestStepReached}</c:when>
    <c:otherwise>${current}</c:otherwise>
  </c:choose>
</c:set>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<ol class="ams-steps">
  <c:forEach var="s" begin="1" end="3">
    <c:set var="label">
      <c:choose>
        <c:when test="${s == 1}">Site</c:when>
        <c:when test="${s == 2}">Device</c:when>
        <c:otherwise>Appointment</c:otherwise>
      </c:choose>
    </c:set>
    <c:set var="action">
      <c:choose>
        <c:when test="${s == 1}">Site</c:when>
        <c:when test="${s == 2}">Device</c:when>
        <c:otherwise>Appointment</c:otherwise>
      </c:choose>
    </c:set>
    <li class="ams-step
               <c:if test="${s == current}">ams-step--current</c:if>
               <c:if test="${s < current}">ams-step--done</c:if>"
        <c:if test="${s == current}">aria-current="step"</c:if>>
      <span class="ams-step-number">${s}</span>
      <c:choose>
        <c:when test="${s != current and s <= furthest}">
          <a href="${ctx}/install/${action}.action"><c:out value="${label}"/></a>
        </c:when>
        <c:otherwise>
          <c:out value="${label}"/>
        </c:otherwise>
      </c:choose>
    </li>
  </c:forEach>
</ol>
