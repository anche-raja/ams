<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%-- Action errors, field errors and confirmations, in one place. --%>
<s:if test="hasActionErrors()">
  <div class="ams-errors" role="alert">
    <ul>
      <s:iterator value="actionErrors">
        <li><s:property escapeHtml="true"/></li>
      </s:iterator>
    </ul>
  </div>
</s:if>

<s:if test="hasFieldErrors()">
  <div class="ams-errors" role="alert">
    <ul>
      <s:iterator value="fieldErrors">
        <s:iterator value="value">
          <li><s:property escapeHtml="true"/></li>
        </s:iterator>
      </s:iterator>
    </ul>
  </div>
</s:if>

<s:if test="hasActionMessages()">
  <div class="ams-messages" role="status">
    <ul>
      <s:iterator value="actionMessages">
        <li><s:property escapeHtml="true"/></li>
      </s:iterator>
    </ul>
  </div>
</s:if>
