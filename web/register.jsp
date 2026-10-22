<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Create an account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<section class="auth">
    <div class="hazard hazard-short" aria-hidden="true"></div>
    <h1 class="auth-title">Create an account</h1>
    <p class="auth-lead">New accounts can view zones, events and alerts. An administrator can give you more access.</p>
    <form method="post" action="${ctx}/RegisterController" class="panel form">
        <label class="field">
            <span>Username</span>
            <input name="username" maxlength="50" required autocomplete="username" value="<c:out value='${username}'/>">
            <c:if test="${not empty errors.username}"><em class="field-error"><c:out value="${errors.username}"/></em></c:if>
        </label>
        <label class="field">
            <span>Full name</span>
            <input name="fullName" maxlength="80" required autocomplete="name" value="<c:out value='${fullName}'/>">
            <c:if test="${not empty errors.fullName}"><em class="field-error"><c:out value="${errors.fullName}"/></em></c:if>
        </label>
        <label class="field">
            <span>Password</span>
            <input type="password" name="password" required autocomplete="new-password">
            <small class="field-hint">At least 8 characters, including a letter and a digit.</small>
            <c:if test="${not empty errors.password}"><em class="field-error"><c:out value="${errors.password}"/></em></c:if>
        </label>
        <label class="field">
            <span>Repeat password</span>
            <input type="password" name="confirm" required autocomplete="new-password">
            <c:if test="${not empty errors.confirm}"><em class="field-error"><c:out value="${errors.confirm}"/></em></c:if>
        </label>
        <button type="submit" class="btn btn-wide">Create account</button>
    </form>
    <p class="auth-switch">Already registered? <a href="${ctx}/LoginController">Sign in</a></p>
</section>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
