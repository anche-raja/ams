<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%--
  The welcome file. Sends the caller straight to the home page; there is no landing page of its
  own, and a redirect keeps the URL honest.
--%>
<c:redirect url="/Home.action"/>
