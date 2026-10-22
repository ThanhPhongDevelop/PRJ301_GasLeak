<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Users" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('USER', 'WRITE')}" />
<div class="page-head"><h1>Users</h1></div>

<c:if test="${canWrite}">
    <details class="panel" ${not empty newUserErrors ? 'open' : ''}>
        <summary>Add a user</summary>
        <form method="post" action="${ctx}/UserController" class="form form-grid">
            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
            <input type="hidden" name="action" value="create">
            <label class="field">
                <span>Username</span>
                <input name="username" maxlength="50" required autocomplete="off" value="<c:out value='${newUser.username}'/>">
                <c:if test="${not empty newUserErrors.username}"><em class="field-error"><c:out value="${newUserErrors.username}"/></em></c:if>
            </label>
            <label class="field">
                <span>Full name</span>
                <input name="fullName" maxlength="80" required autocomplete="off" value="<c:out value='${newUser.fullName}'/>">
                <c:if test="${not empty newUserErrors.fullName}"><em class="field-error"><c:out value="${newUserErrors.fullName}"/></em></c:if>
            </label>
            <label class="field">
                <span>Role</span>
                <select name="roleName">
                    <c:forEach var="r" items="${roles}">
                        <option value="${r.key}" ${r.key == newUser.roleName ? 'selected' : ''}><c:out value="${r.value}"/></option>
                    </c:forEach>
                </select>
                <c:if test="${not empty newUserErrors.roleName}"><em class="field-error"><c:out value="${newUserErrors.roleName}"/></em></c:if>
            </label>
            <label class="field">
                <span>Password</span>
                <input type="password" name="password" autocomplete="new-password">
                <small class="field-hint">Leave empty to generate a temporary password, shown once.</small>
                <c:if test="${not empty newUserErrors.password}"><em class="field-error"><c:out value="${newUserErrors.password}"/></em></c:if>
            </label>
            <div class="form-actions"><button type="submit" class="btn">Create account</button></div>
        </form>
    </details>
</c:if>

<form class="toolbar" method="get" action="${ctx}/UserController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search username or name" aria-label="Search users">
    <select name="role" aria-label="Role">
        <option value="">All roles</option>
        <c:forEach var="r" items="${roles}">
            <option value="${r.key}" ${r.key == role ? 'selected' : ''}><c:out value="${r.value}"/></option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<div class="table-wrap">
    <table class="grid">
        <thead><tr><th class="num">ID</th><th>Username</th><th>Full name</th><th>Role</th><th>Status</th>
            <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr></thead>
        <tbody>
        <c:forEach var="u" items="${result.items}">
            <c:set var="isSelf" value="${u.userId == me.userId}" />
            <tr>
                <td class="num">${u.userId}</td>
                <td><c:out value="${u.username}"/></td>
                <td><c:out value="${u.fullName}"/></td>
                <td>
                    <c:choose>
                        <c:when test="${canWrite and not isSelf}">
                            <form method="post" action="${ctx}/UserController" class="inline-form" data-confirm="Change the role of this user?">
                                <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                <input type="hidden" name="action" value="changeRole">
                                <input type="hidden" name="userId" value="${u.userId}">
                                <select name="roleName" aria-label="Role of ${u.userId}">
                                    <c:forEach var="r" items="${roles}">
                                        <option value="${r.key}" ${r.key == u.roleName ? 'selected' : ''}><c:out value="${r.value}"/></option>
                                    </c:forEach>
                                </select>
                                <button type="submit" class="btn btn-small btn-quiet">Change role</button>
                            </form>
                        </c:when>
                        <c:otherwise><c:out value="${u.roleDisplayName}"/></c:otherwise>
                    </c:choose>
                </td>
                <td><span class="state ${u.status == 'ACTIVE' ? 'state-ok' : 'state-stop'}">${u.status == 'ACTIVE' ? 'Active' : 'Locked'}</span></td>
                <c:if test="${canWrite}">
                    <td class="row-actions">
                        <c:if test="${not isSelf}">
                            <form method="post" action="${ctx}/UserController"
                                  data-confirm="${u.status == 'ACTIVE' ? 'Lock this account? The user is signed out at once.' : 'Unlock this account?'}">
                                <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                <input type="hidden" name="action" value="setStatus">
                                <input type="hidden" name="userId" value="${u.userId}">
                                <input type="hidden" name="status" value="${u.status == 'ACTIVE' ? 'LOCKED' : 'ACTIVE'}">
                                <button type="submit" class="link ${u.status == 'ACTIVE' ? 'link-danger' : ''}">${u.status == 'ACTIVE' ? 'Lock' : 'Unlock'}</button>
                            </form>
                            <form method="post" action="${ctx}/UserController"
                                  data-confirm="Reset the password of ${fn:escapeXml(u.username)}? Their current password stops working at once.">
                                <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                <input type="hidden" name="action" value="resetPassword">
                                <input type="hidden" name="userId" value="${u.userId}">
                                <button type="submit" class="link">Reset password</button>
                            </form>
                        </c:if>
                    </td>
                </c:if>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<c:url var="prevUrl" value="/UserController"><c:param name="q" value="${q}"/><c:param name="role" value="${role}"/><c:param name="page" value="${result.page - 1}"/></c:url>
<c:url var="nextUrl" value="/UserController"><c:param name="q" value="${q}"/><c:param name="role" value="${role}"/><c:param name="page" value="${result.page + 1}"/></c:url>
<nav class="pager" aria-label="Pages">
    <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
    <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} users</span>
    <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
</nav>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
