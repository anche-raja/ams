<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Install order - site"/>
</jsp:include>

<div class="ams-content">
  <h1>The site</h1>
  <jsp:include page="/WEB-INF/common/installSteps.jsp">
    <jsp:param name="step" value="1"/>
  </jsp:include>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <%--
    The address validation interceptor's two questions, asked on this page rather than on pages
    of their own. The address service is wrong often enough on new builds and renumbered streets
    that keeping what was typed is always a supported answer.
  --%>
  <s:if test="suggestedAddress != null">
    <div class="ams-address-compare">
      <div class="ams-panel">
        <h2>As you typed it</h2>
        <p class="ams-address">
          <s:property value="siteAddress.addressLine1" escapeHtml="true"/><br/>
          <s:if test="siteAddress.addressLine2 != null && siteAddress.addressLine2 != ''">
            <s:property value="siteAddress.addressLine2" escapeHtml="true"/><br/>
          </s:if>
          <s:property value="siteAddress.city" escapeHtml="true"/>,
          <s:property value="siteAddress.state.code" escapeHtml="true"/>
          <s:property value="siteAddress.zipCode" escapeHtml="true"/>
        </p>
        <s:form action="KeepAddress" namespace="/install" method="post">
          <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>
          <s:submit value="Keep what I typed"/>
        </s:form>
      </div>
      <div class="ams-panel">
        <h2>Suggested</h2>
        <p class="ams-address">
          <s:property value="suggestedAddress.addressLine1" escapeHtml="true"/><br/>
          <s:if test="suggestedAddress.addressLine2 != null && suggestedAddress.addressLine2 != ''">
            <s:property value="suggestedAddress.addressLine2" escapeHtml="true"/><br/>
          </s:if>
          <s:property value="suggestedAddress.city" escapeHtml="true"/>,
          <s:property value="suggestedAddress.state.code" escapeHtml="true"/>
          <s:property value="suggestedAddress.zipCode" escapeHtml="true"/>
        </p>
        <s:form action="AcceptSuggestion" namespace="/install" method="post">
          <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>
          <s:submit value="Use the suggested address" cssClass="ams-primary"/>
        </s:form>
      </div>
    </div>
    <p class="ams-hint">Or correct the address below and check it again.</p>
  </s:if>

  <s:if test="addressCheckUnavailable">
    <div class="ams-warning" role="status">
      <p>
        The address could not be checked just now. You can carry on with it as typed - the order
        will be marked unverified for the warehouse - or correct it below and try again.
      </p>
      <s:form action="KeepAddress" namespace="/install" method="post">
        <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>
        <s:submit value="Continue with this address" cssClass="ams-primary"/>
      </s:form>
    </div>
  </s:if>

  <s:form action="SaveSite" namespace="/install" method="post">
    <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>

    <fieldset>
      <legend>Site contact &mdash; who meets the engineer</legend>
      <div class="ams-field-grid">
        <div class="ams-field">
          <label for="first">First name</label>
          <s:textfield name="siteContact.firstName" id="first" size="20" maxlength="60"/>
          <s:fielderror><s:param>siteContact.firstName</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="last">Last name</label>
          <s:textfield name="siteContact.lastName" id="last" size="20" maxlength="60"/>
          <s:fielderror><s:param>siteContact.lastName</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="email">Email</label>
          <s:textfield name="siteContact.emailAddress" id="email" size="28" maxlength="120"/>
          <s:fielderror><s:param>siteContact.emailAddress</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="phone">Phone</label>
          <s:textfield name="siteContact.phoneNumber" id="phone" size="18" maxlength="30"/>
          <s:fielderror><s:param>siteContact.phoneNumber</s:param></s:fielderror>
        </div>
      </div>
    </fieldset>

    <fieldset>
      <legend>Installation address</legend>
      <div class="ams-field-grid">
        <div class="ams-field">
          <label for="line1">Address line 1</label>
          <s:textfield name="siteAddress.addressLine1" id="line1" size="30" maxlength="26"/>
          <s:fielderror><s:param>siteAddress.addressLine1</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="line2">Address line 2</label>
          <s:textfield name="siteAddress.addressLine2" id="line2" size="30" maxlength="26"/>
          <s:fielderror><s:param>siteAddress.addressLine2</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="city">City</label>
          <s:textfield name="siteAddress.city" id="city" size="24" maxlength="20"/>
          <s:fielderror><s:param>siteAddress.city</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="state">State</label>
          <s:select name="stateCode" id="state" list="stateOptions"
                    listKey="code" listValue="description" headerKey="" headerValue="Choose..."/>
          <s:fielderror><s:param>siteAddress.state</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="zip">ZIP code</label>
          <s:textfield name="siteAddress.zipCode" id="zip" size="10" maxlength="15"/>
          <s:fielderror><s:param>siteAddress.zipCode</s:param></s:fielderror>
        </div>
        <div class="ams-field">
          <label for="country">Country</label>
          <s:select name="countryCode" id="country" list="countryOptions"
                    listKey="code" listValue="description"/>
        </div>
      </div>
      <p class="ams-hint">
        The device is delivered to and fitted at this address, and its ZIP code decides which
        engineer region can book the visit. Address lines are limited to 26 characters and the
        city to 20, because that is what fits on the carrier's label.
      </p>
    </fieldset>

    <div class="ams-button-row">
      <s:submit value="Check address and continue" cssClass="ams-primary"/>
    </div>
  </s:form>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
