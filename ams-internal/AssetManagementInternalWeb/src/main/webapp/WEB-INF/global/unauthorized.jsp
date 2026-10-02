<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Not permitted"/>
</jsp:include>

<div class="ams-content">
  <h1>Not permitted</h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p>You do not have permission to do that.</p>
  <p>If you believe you should have, ask your administrator which group grants it.</p>
  <p><a href="${pageContext.request.contextPath}/Home.action">Return to the home page</a></p>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
