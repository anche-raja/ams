<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="This page has expired"/>
</jsp:include>

<div class="ams-content">
  <h1>This page has expired</h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p>This page has been open too long, or was submitted twice.</p>
  <p>Reload it and try again. Nothing has been changed.</p>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
