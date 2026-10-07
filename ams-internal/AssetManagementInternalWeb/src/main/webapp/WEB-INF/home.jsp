<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Home"/>
</jsp:include>

<div class="ams-content">
  <h1>Install orders</h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p class="ams-hint">
    Choose the customer to order for. An install order takes three steps - the site, the device
    and the installation appointment - and nothing is saved until you place it.
  </p>

  <div class="ams-table-scroll">
    <table class="ams-table">
      <thead>
        <tr>
          <th scope="col">Customer</th>
          <th scope="col">Account</th>
          <th scope="col">Ordering</th>
          <th scope="col"></th>
        </tr>
      </thead>
      <tbody>
        <s:iterator value="customerList" var="cust">
          <tr>
            <td><strong><s:property value="#cust.customerName" escapeHtml="true"/></strong></td>
            <td><s:property value="#cust.accountNumber" escapeHtml="true"/></td>
            <td>
              <%-- Ordering needs the flag AND an active customer AND an active service, which is
                   why the button below can still be refused for a customer shown as enabled. --%>
              <s:if test="#cust.canSubmitOrders">
                <span class="ams-badge ams-badge--ok">Enabled</span>
              </s:if>
              <s:else>
                <span class="ams-badge ams-badge--warn">Disabled</span>
              </s:else>
            </td>
            <td>
              <s:if test="#cust.canSubmitOrders">
                <s:form action="StartInstallOrder" namespace="/" method="post">
                  <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>
                  <input type="hidden" name="selectedCustomerId"
                         value="<s:property value='#cust.customerId'/>"/>
                  <s:submit value="New install order" cssClass="ams-primary"/>
                </s:form>
              </s:if>
            </td>
          </tr>
        </s:iterator>
        <s:if test="customerList == null || customerList.isEmpty()">
          <tr><td colspan="4" class="ams-empty">No customers found.</td></tr>
        </s:if>
      </tbody>
    </table>
  </div>

  <s:if test="currentCustomer != null">
    <h2>Orders for <s:property value="currentCustomer.displayName" escapeHtml="true"/></h2>
    <div class="ams-table-scroll">
      <table class="ams-table">
        <thead>
          <tr>
            <th scope="col">Order</th>
            <th scope="col">Device</th>
            <th scope="col">Status</th>
            <th scope="col">Submitted</th>
            <th scope="col">Installation requested</th>
          </tr>
        </thead>
        <tbody>
          <s:iterator value="orders" var="ord">
            <tr>
              <td>
                <a href="<s:url namespace='/install' action='Confirmation'><s:param name='orderId' value='#ord.orderId'/></s:url>">
                  <s:property value="#ord.orderNumber" escapeHtml="true"/>
                </a>
              </td>
              <td><s:property value="#ord.deviceNickname" escapeHtml="true"/></td>
              <td><s:property value="#ord.orderStatusType.description" escapeHtml="true"/></td>
              <td><s:date name="#ord.submittedDate" format="d MMM yyyy"/></td>
              <td><s:date name="#ord.requestedInstallationDate" format="d MMM yyyy"/></td>
            </tr>
          </s:iterator>
          <s:if test="orders == null || orders.isEmpty()">
            <tr><td colspan="5" class="ams-empty">No orders yet.</td></tr>
          </s:if>
        </tbody>
      </table>
    </div>
  </s:if>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
