<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Compensation profiles" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('PROFILE', 'WRITE')}" />
<c:url var="exportUrl" value="/OperatingProfileController"><c:param name="action" value="export"/><c:param name="deviceId" value="${deviceId}"/></c:url>
<c:url var="addUrl" value="/OperatingProfileController"><c:param name="action" value="new"/><c:param name="deviceId" value="${deviceId}"/></c:url>

<div class="page-head">
    <h1>Compensation profiles</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${addUrl}">New profile</a></c:if>
    </div>
</div>
<p class="lead">How much temperature and humidity move each gas sensor, removed from every reading:
    compensated = raw &minus; b<sub>T</sub> &times; (T &minus; T<sub>ref</sub>) &minus; b<sub>RH</sub> &times; (RH &minus; RH<sub>ref</sub>).
    Profiles are kept as versions: the newest of a device is in use, older ones are history.</p>

<form class="toolbar" method="get" action="${ctx}/OperatingProfileController">
    <select name="deviceId" aria-label="Device">
        <option value="">All devices</option>
        <c:forEach var="d" items="${devices}">
            <option value="${d.value}" ${d.value == deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No profile yet for these filters: compensated values equal raw values until one is saved or fitted.</p>
            <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Create a profile</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th>Device</th><th class="num">Version</th><th>Coefficients</th><th>Source</th><th>Saved</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="p" items="${result.items}">
                    <tr class="${p.current ? '' : 'row-quiet'}">
                        <td class="strong nowrap"><c:out value="${p.deviceCode}"/><c:if test="${p.current}"> <span class="badge badge-ok">In use</span></c:if></td>
                        <td class="num">#${p.profileId}</td>
                        <td class="small"><c:out value="${p.summary}"/></td>
                        <td class="nowrap"><c:out value="${p.source}"/></td>
                        <td class="small nowrap">${gg:time(p.updatedAt)}<br><span class="muted"><c:out value="${empty p.updatedByName ? 'by a tool' : 'by '.concat(p.updatedByName)}"/></span></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/OperatingProfileController?action=edit&amp;id=${p.profileId}" title="Open this version and save the result as the newest">Edit as new version</a>
                                <c:if test="${not p.current}">
                                    <form method="post" action="${ctx}/OperatingProfileController"
                                          data-confirm="Delete profile version #${p.profileId} of ${fn:escapeXml(p.deviceCode)}?">
                                        <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="id" value="${p.profileId}">
                                        <button type="submit" class="link link-danger">Delete</button>
                                    </form>
                                </c:if>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/OperatingProfileController"><c:param name="deviceId" value="${deviceId}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/OperatingProfileController"><c:param name="deviceId" value="${deviceId}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} versions</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
