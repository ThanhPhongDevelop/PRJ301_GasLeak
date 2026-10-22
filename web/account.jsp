<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="My account" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head"><h1>My account</h1></div>

<div class="detail-grid">
    <section class="panel">
        <h2>Change password</h2>
        <form method="post" action="${ctx}/AccountController" class="form">
            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
            <input type="hidden" name="action" value="changePassword">
            <label class="field">
                <span>Current password</span>
                <input type="password" name="currentPassword" required autocomplete="current-password">
                <c:if test="${not empty errors.currentPassword}"><em class="field-error"><c:out value="${errors.currentPassword}"/></em></c:if>
            </label>
            <label class="field">
                <span>New password</span>
                <input type="password" name="newPassword" required autocomplete="new-password">
                <small class="field-hint">At least 8 characters, including a letter and a digit.</small>
                <c:if test="${not empty errors.newPassword}"><em class="field-error"><c:out value="${errors.newPassword}"/></em></c:if>
            </label>
            <label class="field">
                <span>Repeat new password</span>
                <input type="password" name="confirmPassword" required autocomplete="new-password">
                <c:if test="${not empty errors.confirmPassword}"><em class="field-error"><c:out value="${errors.confirmPassword}"/></em></c:if>
            </label>
            <div class="form-actions"><button type="submit" class="btn">Change password</button></div>
        </form>
    </section>

    <section class="panel">
        <h2>Account</h2>
        <dl class="facts">
            <div><dt>Username</dt><dd><c:out value="${account.username}"/></dd></div>
            <div><dt>Full name</dt><dd><c:out value="${account.fullName}"/></dd></div>
            <div><dt>Role</dt><dd><c:out value="${account.roleDisplayName}"/></dd></div>
            <div><dt>Member since (UTC)</dt><dd>${gg:time(account.createdAt)}</dd></div>
        </dl>
        <h3>What your role can do</h3>
        <p class="muted">Read lets you open a page; write lets you change it.</p>
        <ul class="chips">
            <c:forEach var="permission" items="${me.permissionList}">
                <li class="chip"><c:out value="${permission}"/></li>
            </c:forEach>
        </ul>
    </section>
</div>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
