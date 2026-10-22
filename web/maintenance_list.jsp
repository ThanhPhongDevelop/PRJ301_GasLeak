<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Maintenance" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('MAINTENANCE', 'WRITE')}" />
<c:url var="exportUrl" value="/MaintenanceController">
    <c:param name="action" value="export"/><c:param name="status" value="${status}"/>
    <c:param name="mine" value="${mine ? '1' : ''}"/><c:param name="q" value="${q}"/>
</c:url>
<div class="page-head">
    <h1>Maintenance tickets</h1>
    <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
</div>
<p class="lead">Tickets are opened from an alert. Close a ticket with a short note of what was repaired.</p>

<form class="toolbar" method="get" action="${ctx}/MaintenanceController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search notes or device" aria-label="Search tickets">
    <select name="status" aria-label="Status">
        <option value="">Open or closed</option>
        <option value="OPEN" ${status == 'OPEN' ? 'selected' : ''}>Open</option>
        <option value="CLOSED" ${status == 'CLOSED' ? 'selected' : ''}>Closed</option>
    </select>
    <label class="check"><input type="checkbox" name="mine" value="1" ${mine ? 'checked' : ''}> Assigned to me</label>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty"><p>No ticket matches these filters. Open one from an alert page.</p></div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead><tr><th class="num">#</th><th>Status</th><th>Device</th><th>Alert</th><th>Assigned to</th>
                    <th>Opened (UTC)</th><th>Note</th><c:if test="${canWrite}"><th><span class="sr-only">Close</span></th></c:if></tr></thead>
                <tbody>
                <c:forEach var="t" items="${result.items}">
                    <tr>
                        <td class="num">${t.ticketId}</td>
                        <td><span class="badge badge-${t.status == 'OPEN' ? 'warning' : 'ok'}">${gg:label(t.status)}</span></td>
                        <td><c:out value="${t.deviceCode}"/></td>
                        <td><c:if test="${not empty t.alertId}"><a href="${ctx}/AlertController?action=view&amp;id=${t.alertId}">Alert ${t.alertId}</a>
                            <span class="muted">${gg:label(t.alertLabel)}</span></c:if></td>
                        <td><c:out value="${empty t.assigneeName ? 'Nobody' : t.assigneeName}"/></td>
                        <td class="nowrap">${gg:time(t.openedAt)}<c:if test="${not empty t.closedAt}"><br><span class="muted">closed ${gg:time(t.closedAt)}</span></c:if></td>
                        <td class="note"><c:out value="${t.note}"/></td>
                        <c:if test="${canWrite}">
                            <td>
                                <c:if test="${t.status == 'OPEN'}">
                                    <form method="post" action="${ctx}/MaintenanceController" class="inline-form"
                                          data-confirm="Close ticket ${t.ticketId}?">
                                        <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                        <input type="hidden" name="action" value="close">
                                        <input type="hidden" name="ticketId" value="${t.ticketId}">
                                        <input name="note" maxlength="300" required placeholder="What was repaired" aria-label="Closing note for ticket ${t.ticketId}">
                                        <button type="submit" class="btn btn-small btn-quiet">Close</button>
                                    </form>
                                </c:if>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/MaintenanceController"><c:param name="status" value="${status}"/><c:param name="mine" value="${mine ? '1' : ''}"/><c:param name="q" value="${q}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/MaintenanceController"><c:param name="status" value="${status}"/><c:param name="mine" value="${mine ? '1' : ''}"/><c:param name="q" value="${q}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Newer</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} tickets</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Older</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
