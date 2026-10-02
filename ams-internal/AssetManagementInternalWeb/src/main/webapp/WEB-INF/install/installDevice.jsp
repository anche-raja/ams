<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<jsp:include page="/WEB-INF/common/header.jsp">
  <jsp:param name="pageTitle" value="Install order - device"/>
</jsp:include>

<div class="ams-content">
  <h1>The device</h1>
  <jsp:include page="/WEB-INF/common/installSteps.jsp">
    <jsp:param name="step" value="2"/>
  </jsp:include>
  <jsp:include page="/WEB-INF/common/messages.jsp"/>

  <p class="ams-hint">
    The device is staged in the warehouse from this configuration and arrives on site already
    addressed, so both sides are keyed now rather than by the engineer on the day. Everything here
    is validated before you can continue: a transposed digit found on site is a wasted visit.
  </p>

  <s:form action="SaveDevice" namespace="/install" method="post">
    <jsp:include page="/WEB-INF/common/csrfToken.jsp"/>

    <fieldset>
      <legend>Device</legend>
      <div class="ams-field">
        <label for="nickname">Nickname</label>
        <s:textfield name="deviceNickname" id="nickname" size="36" maxlength="60"
                     placeholder="Front desk router"/>
        <s:fielderror><s:param>deviceNickname</s:param></s:fielderror>
      </div>
      <p class="ams-hint">The customer's own name for it - what they will quote on the phone.</p>
    </fieldset>

    <fieldset>
      <legend>WAN &mdash; the carrier facing side</legend>
      <div class="ams-field">
        <label for="netcfg">Addressing</label>
        <s:select name="networkConfigurationCode" id="netcfg"
                  list="networkConfigurationOptions" listKey="code" listValue="description"/>
      </div>
      <p class="ams-hint">
        With DHCP or PPPoE the carrier assigns the address at connection time, so the WAN fields
        below are left blank and are not checked.
      </p>
      <div class="ams-field-grid">
        <div class="ams-field">
          <label for="wan-ip">WAN IP address</label>
          <s:textfield name="assetConfiguration.wanIpAddress" id="wan-ip" placeholder="64.12.30.6" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="wan-mask">WAN subnet mask</label>
          <s:textfield name="assetConfiguration.wanSubnetMask" id="wan-mask" placeholder="255.255.255.252" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="wan-gw">Default gateway</label>
          <s:textfield name="assetConfiguration.defaultGateway" id="wan-gw" placeholder="64.12.30.5" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="dns1">Primary DNS</label>
          <s:textfield name="assetConfiguration.primaryDnsAddress" id="dns1" placeholder="9.9.9.9" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="dns2">Secondary DNS</label>
          <s:textfield name="assetConfiguration.secondaryDnsAddress" id="dns2" placeholder="149.112.112.112" size="18" maxlength="15"/>
        </div>
      </div>
      <s:fielderror><s:param>assetConfiguration.wanIpAddress</s:param></s:fielderror>
    </fieldset>

    <fieldset>
      <legend>LAN &mdash; the site side</legend>
      <div class="ams-field-grid">
        <div class="ams-field">
          <label for="lan-type">LAN type</label>
          <s:select name="configurationTypeCode" id="lan-type"
                    list="configurationTypeOptions" listKey="code" listValue="description"/>
        </div>
        <div class="ams-field">
          <label for="lan-ip">LAN IP address</label>
          <s:textfield name="assetConfiguration.lanIpAddress" id="lan-ip" placeholder="10.10.5.1" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="lan-mask">LAN subnet mask</label>
          <s:textfield name="assetConfiguration.lanSubnetMask" id="lan-mask" placeholder="255.255.255.0" size="18" maxlength="15"/>
        </div>
        <div class="ams-field">
          <label for="lan-gw">LAN gateway</label>
          <s:textfield name="assetConfiguration.lanGateway" id="lan-gw" placeholder="10.10.5.254" size="18" maxlength="15"/>
        </div>
      </div>
      <p class="ams-hint">
        The LAN IP address is the device's own address on the site network; the LAN gateway is the
        customer's own router, a different box, and the two may not be the same address. LAN type A
        expects the device at the first usable address of a private subnet and the customer's
        router at the last.
      </p>
      <s:fielderror><s:param>assetConfiguration.lanIpAddress</s:param></s:fielderror>
    </fieldset>

    <div class="ams-button-row">
      <s:submit value="Continue to the appointment" cssClass="ams-primary"/>
    </div>
  </s:form>
</div>

<jsp:include page="/WEB-INF/common/footer.jsp"/>
