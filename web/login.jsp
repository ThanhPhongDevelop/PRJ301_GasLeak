<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Sign in" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<section class="auth">
    <div class="hazard hazard-short" aria-hidden="true"></div>
    <h1 class="auth-title">GasGuard</h1>
    <p class="auth-lead">Kitchen gas monitoring for your home.</p>
    <c:if test="${param.registered == '1'}">
        <p class="notice notice-ok">Account created. Sign in with your new username.</p>
    </c:if>
    <c:if test="${not empty error}">
        <p class="notice notice-error"><c:out value="${error}"/></p>
    </c:if>
    <form method="post" action="${ctx}/LoginController" class="panel form">
        <label class="field">
            <span>Username</span>
            <input name="username" maxlength="50" required autofocus autocomplete="username"
                   value="<c:out value='${username}'/>">
        </label>
        <label class="field">
            <span>Password</span>
            <input type="password" name="password" required autocomplete="current-password">
        </label>
        <button type="submit" class="btn btn-wide">Sign in</button>
    </form>
    <p class="auth-switch">New here? <a href="${ctx}/RegisterController">Create an account</a></p>
</section>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
