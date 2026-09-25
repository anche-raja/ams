<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Install order - appointment"/>
</jsp:include>

<div class="ams-content">
  <h1>Installation appointment</h1>
  <jsp:include page="/WEB-INF/common/installSteps.jsp">
    <jsp:param name="step" value="3"/>
  </jsp:include>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <div class="ams-panel">
    <h2>Installing</h2>
    <p class="ams-address">
      <strong><s:property value="deviceNickname" escapeHtml="true"/></strong><br/>
      <s:property value="siteAddress.addressLine1" escapeHtml="true"/>,
      <s:property value="siteAddress.city" escapeHtml="true"/>,
      <s:property value="siteAddress.state.code" escapeHtml="true"/>
      <s:property value="siteAddress.zipCode" escapeHtml="true"/><br/>
      Site contact: <s:property value="siteContact.firstName" escapeHtml="true"/>
      <s:property value="siteContact.lastName" escapeHtml="true"/>,
      <s:property value="siteContact.phoneNumber" escapeHtml="true"/>
    </p>
  </div>

  <s:form action="PlaceOrder" namespace="/install" method="post">
    <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>

    <fieldset>
      <legend>Choose a visit</legend>
      <s:if test="regionUnmapped">
        <%-- A gap in the region map, not an error: the order can still be placed. --%>
        <p class="ams-warning">
          No engineer region covers ZIP code <s:property value="siteAddress.zipCode" escapeHtml="true"/>
          yet, so no appointment can be offered. You can still place the order; operations will
          book the visit by hand.
        </p>
      </s:if>
      <s:else>
        <p class="ams-hint">
          Region <s:property value="region" escapeHtml="true"/>. The earliest visit is
          <s:date name="earliestDate" format="EEE d MMM yyyy"/>, which leaves time to build and
          ship the device. The appointment is reserved when you place the order.
        </p>
        <%-- Filled in by js/common.js from the AppointmentSlots JSON endpoint. --%>
        <div class="ams-slots" id="ams-slots" role="radiogroup" aria-label="Installation appointments"
             data-slots-url="${pageContext.request.contextPath}/install/AppointmentSlots.action">
          Loading available appointments&hellip;
        </div>
        <noscript><p class="ams-warning">Appointments need JavaScript to load.</p></noscript>
        <s:fielderror><s:param>installationTimeslotId</s:param></s:fielderror>
      </s:else>

      <div class="ams-field">
        <label for="comments">Notes for the engineer</label>
        <s:textarea name="comments" id="comments" rows="3" cols="60"/>
        <s:fielderror><s:param>comments</s:param></s:fielderror>
      </div>
    </fieldset>

    <div class="ams-button-row">
      <s:submit value="Place order" cssClass="ams-primary"/>
    </div>
  </s:form>

  <s:form action="Abandon" namespace="/install" method="post" cssClass="ams-inline-form">
    <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>
    <s:submit value="Discard this order"/>
  </s:form>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
