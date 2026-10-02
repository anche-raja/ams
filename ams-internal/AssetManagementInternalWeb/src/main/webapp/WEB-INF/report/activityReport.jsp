<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Order activity"/>
</jsp:include>

<div class="ams-content">
  <h1>Order activity</h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p class="ams-hint">
    <s:property value="report.summary" escapeHtml="true"/>
    <s:if test="report.earliestInstallationDate != null">
      An order placed now could be installed from
      <s:date name="report.earliestInstallationDate" format="EEEE d MMM yyyy"/>.
    </s:if>
  </p>

  <div class="ams-actions-bar">
    <a href="<s:url action='ReportCsv' namespace='/'/>">Download as CSV</a>
  </div>

  <div class="ams-table-scroll">
    <table class="ams-table">
      <thead>
        <tr>
          <th scope="col">Customer</th>
          <th scope="col">Account</th>
          <th scope="col">Ordering</th>
          <th scope="col">Open</th>
          <th scope="col">Scheduled</th>
          <th scope="col">Completed</th>
          <th scope="col">Cancelled</th>
          <th scope="col">Oldest open</th>
          <th scope="col">Latest order</th>
          <th scope="col">Submitted</th>
        </tr>
      </thead>
      <tbody>
        <s:iterator value="report.lines" var="line">
          <tr class="<s:if test='#line.openOrders > 0'>ams-row-info</s:if>">
            <td><strong><s:property value="#line.customerName" escapeHtml="true"/></strong></td>
            <td><s:property value="#line.accountNumber" escapeHtml="true"/></td>
            <td>
              <s:if test="#line.orderingEnabled">
                <span class="ams-badge ams-badge--ok">Enabled</span>
              </s:if>
              <s:else>
                <span class="ams-badge ams-badge--warn">Disabled</span>
              </s:else>
            </td>
            <td><s:property value="#line.openOrders"/></td>
            <td><s:property value="#line.scheduledInstallations"/></td>
            <td><s:property value="#line.completedOrders"/></td>
            <td><s:property value="#line.cancelledOrders"/></td>
            <td>
              <s:if test="#line.oldestOpenOrderAgeDays != null">
                <s:property value="#line.oldestOpenOrderAgeDays"/> days
              </s:if>
              <s:else><span class="ams-muted">&ndash;</span></s:else>
            </td>
            <td><s:property value="#line.latestOrderNumber" escapeHtml="true"/></td>
            <td><s:date name="#line.latestSubmittedDate" format="d MMM yyyy"/></td>
          </tr>
        </s:iterator>
        <s:if test="report == null || report.lines.isEmpty()">
          <tr><td colspan="10" class="ams-empty">No customers found.</td></tr>
        </s:if>
      </tbody>
    </table>
  </div>

  <s:if test="report != null && !report.ordersByStatus.isEmpty()">
    <h2>Orders by status</h2>
    <table class="ams-table ams-detail">
      <tbody>
        <s:iterator value="report.ordersByStatus" var="entry">
          <tr>
            <th scope="row"><s:property value="#entry.key" escapeHtml="true"/></th>
            <td><s:property value="#entry.value"/></td>
          </tr>
        </s:iterator>
      </tbody>
    </table>
  </s:if>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
