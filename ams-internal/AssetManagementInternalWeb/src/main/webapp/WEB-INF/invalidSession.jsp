<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Your session has ended"/>
</jsp:include>

<div class="ams-content">
  <h1>Your session has ended</h1>

  <p>Your session has ended, either because it was idle too long or because you signed in elsewhere.</p>
  <p>Reload the page to sign in again.</p>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
