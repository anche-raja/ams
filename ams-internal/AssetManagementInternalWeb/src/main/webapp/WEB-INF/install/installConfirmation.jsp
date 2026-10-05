<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Install order"/>
</jsp:include>

<div class="ams-content">
  <h1>Order <s:property value="order.orderNumber" escapeHtml="true"/></h1>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <s:if test="appointmentLost">
    <p class="ams-warning" role="status">
      The order has been placed, but the appointment you chose was taken before it could be
      reserved. Operations will contact the site to book another visit.
    </p>
  </s:if>

  <table class="ams-table ams-detail">
    <tbody>
      <tr><th scope="row">Status</th>
          <td><s:property value="order.orderStatusType.description" escapeHtml="true"/></td></tr>
      <tr><th scope="row">Submitted</th>
          <td><s:date name="order.submittedDate" format="d MMM yyyy HH:mm"/></td></tr>
      <tr><th scope="row">Device</th>
          <td><s:property value="order.deviceNickname" escapeHtml="true"/></td></tr>
      <tr><th scope="row">Site</th>
          <td class="ams-address">
            <s:property value="order.shippingAddress.addressLine1" escapeHtml="true"/><br/>
            <s:if test="order.shippingAddress.addressLine2 != null && order.shippingAddress.addressLine2 != ''">
              <s:property value="order.shippingAddress.addressLine2" escapeHtml="true"/><br/>
            </s:if>
            <s:property value="order.shippingAddress.city" escapeHtml="true"/>,
            <s:property value="order.shippingAddress.state.code" escapeHtml="true"/>
            <s:property value="order.shippingAddress.zipCode" escapeHtml="true"/>
          </td></tr>
      <tr><th scope="row">Site contact</th>
          <td>
            <s:property value="order.installationContact.firstName" escapeHtml="true"/>
            <s:property value="order.installationContact.lastName" escapeHtml="true"/>,
            <s:property value="order.installationContact.emailAddress" escapeHtml="true"/>,
            <s:property value="order.installationContact.phoneNumber" escapeHtml="true"/>
          </td></tr>
      <tr><th scope="row">WAN</th>
          <td>
            <s:property value="order.assetConfiguration.networkConfigurationType.description" escapeHtml="true"/>
            <s:if test="order.assetConfiguration.wanIpAddress != null">
              &middot; <s:property value="order.assetConfiguration.wanIpAddress" escapeHtml="true"/>
              / <s:property value="order.assetConfiguration.wanSubnetMask" escapeHtml="true"/>
              via <s:property value="order.assetConfiguration.defaultGateway" escapeHtml="true"/>
            </s:if>
          </td></tr>
      <tr><th scope="row">LAN</th>
          <td>
            <s:property value="order.assetConfiguration.assetConfigurationType.description" escapeHtml="true"/>
            &middot; <s:property value="order.assetConfiguration.lanIpAddress" escapeHtml="true"/>
            / <s:property value="order.assetConfiguration.lanSubnetMask" escapeHtml="true"/>
          </td></tr>
      <tr><th scope="row">Installation</th>
          <td>
            <s:if test="order.installation != null && order.installation.timeslot != null">
              <s:date name="order.installation.scheduledDate" format="EEE d MMM yyyy"/>,
              <s:property value="order.installation.timeslot.displayLabel" escapeHtml="true"/>
              <span class="ams-badge ams-badge--ok">
                <s:property value="order.installation.installationStatusType.description" escapeHtml="true"/>
              </span>
            </s:if>
            <s:else>
              <span class="ams-badge ams-badge--warn">Not yet scheduled</span>
            </s:else>
          </td></tr>
      <s:if test="order.comments != null">
        <tr><th scope="row">Notes</th>
            <td><s:property value="order.comments" escapeHtml="true"/></td></tr>
      </s:if>
    </tbody>
  </table>

  <p class="ams-actions">
    <a href="${pageContext.request.contextPath}/Home.action">Back to the home page</a>
  </p>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
