<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="code" value="${requestScope['javax.servlet.error.status_code']}" />
<c:set var="pageTitle" value="Something went wrong" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<section class="auth">
    <div class="hazard hazard-short" aria-hidden="true"></div>
    <h1 class="auth-title">
        <c:choose>
            <c:when test="${code == 403}">No access to this page</c:when>
            <c:when test="${code == 404}">Page not found</c:when>
            <c:otherwise>The server could not finish this request</c:otherwise>
        </c:choose>
    </h1>
    <p class="auth-lead">
        <c:choose>
            <c:when test="${code == 403}">Your role does not include this action. Ask an administrator if you need it.</c:when>
            <c:when test="${code == 404}">The link may be old or mistyped.</c:when>
            <c:otherwise>Try again. If it keeps happening, tell the administrator what you were doing.</c:otherwise>
        </c:choose>
    </p>
    <a class="btn" href="${ctx}/DashboardController">Go to the dashboard</a>
</section>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
