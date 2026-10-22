<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Audit log" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1>Audit log</h1>
    <a class="btn" href="${ctx}/AuditController?action=verify">Check for tampering</a>
</div>

<c:if test="${verified}">
    <c:choose>
        <c:when test="${brokenId < 0}">
            <p class="notice notice-ok">The hash chain is intact: no entry has been edited, deleted or inserted by hand.</p>
        </c:when>
        <c:otherwise>
            <p class="notice notice-error">Entry #${brokenId} does not match the hash chain. It, or an entry before it, was changed outside the application.</p>
        </c:otherwise>
    </c:choose>
</c:if>

<form class="toolbar" method="get" action="${ctx}/AuditController">
    <select name="filter" aria-label="Action">
        <option value="">All actions</option>
        <c:forEach var="a" items="${actions}">
            <option value="<c:out value='${a}'/>" ${a == filter ? 'selected' : ''}><c:out value="${a}"/></option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<div class="table-wrap">
    <table class="grid grid-dense">
        <thead><tr><th class="num">#</th><th>Time (UTC)</th><th>By</th><th>Action</th><th>Object</th><th>Details</th><th>Row hash</th></tr></thead>
        <tbody>
        <c:forEach var="entry" items="${result.items}">
            <tr class="${verified and entry.auditId == brokenId ? 'row-alarm' : ''}">
                <td class="num">${entry.auditId}</td>
                <td class="nowrap"><c:out value="${entry.timeDisplay}"/></td>
                <td><c:out value="${empty entry.actorUsername ? 'System' : entry.actorUsername}"/></td>
                <td><c:out value="${entry.action}"/></td>
                <td><c:out value="${entry.objectType}"/><c:if test="${not empty entry.objectId}"> #${entry.objectId}</c:if></td>
                <td class="code"><c:out value="${entry.diffPreview}"/></td>
                <td class="code"><c:out value="${entry.shortHash}"/></td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>
<c:url var="prevUrl" value="/AuditController"><c:param name="filter" value="${filter}"/><c:param name="page" value="${result.page - 1}"/></c:url>
<c:url var="nextUrl" value="/AuditController"><c:param name="filter" value="${filter}"/><c:param name="page" value="${result.page + 1}"/></c:url>
<nav class="pager" aria-label="Pages">
    <c:if test="${not result.firstPage}"><a href="${prevUrl}">Newer</a></c:if>
    <span>Page ${result.page} of ${result.totalPages}</span>
    <c:if test="${not result.lastPage}"><a href="${nextUrl}">Older</a></c:if>
</nav>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
