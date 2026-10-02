<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<footer class="ams-footer">
  <span class="ams-version">
    <%-- Stamped into the servlet context at start-up by ApplicationVersionListener. --%>
    <c:out value="${applicationScope.amsApplicationVersion}"/>
  </span>
</footer>

<script src="${pageContext.request.contextPath}/js/common.js"></script>
</body>
</html>
