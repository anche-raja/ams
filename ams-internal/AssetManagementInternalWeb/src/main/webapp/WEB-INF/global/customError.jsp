<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Something went wrong"/>
</jsp:include>

<div class="ams-content">
  <h1>Something went wrong</h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p>Your request could not be completed. Nothing has been changed.</p>
  <s:if test="reference != null">
    <p class="ams-reference">
      Quote reference <strong><s:property value="reference" escapeHtml="true"/></strong>
      if you contact support.
    </p>
  </s:if>
  <p><a href="${pageContext.request.contextPath}/Home.action">Return to the home page</a></p>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
