<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Zone thresholds" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('ZONE_THRESHOLD', 'WRITE')}" />
<c:url var="exportUrl" value="/ZoneThresholdController">
    <c:param name="action" value="export"/><c:param name="zoneId" value="${zoneId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/>
</c:url>
<c:url var="addUrl" value="/ZoneThresholdController">
    <c:param name="action" value="new"/><c:param name="zoneId" value="${zoneId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/>
</c:url>

<div class="page-head">
    <h1>Zone thresholds</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add levels</a></c:if>
    </div>
</div>
<p class="lead">The rules compare each session with the levels of its zone: MQ-2 at the warning level gives a minor leak,
    at the alarm level a major leak; MQ-135 at its warning level while MQ-2 stays below its own gives cooking fumes.
    A zone without levels for a sensor uses the default levels.</p>

<form class="toolbar" method="get" action="${ctx}/ZoneThresholdController">
    <select name="zoneId" aria-label="Kitchen zone">
        <option value="">All zones</option>
        <c:forEach var="z" items="${zones}">
            <option value="${z.value}" ${z.value == zoneId ? 'selected' : ''}><c:out value="${z.label}"/></option>
        </c:forEach>
    </select>
    <select name="sensorTypeId" aria-label="Sensor type">
        <option value="">All sensor types</option>
        <c:forEach var="t" items="${sensorTypes}">
            <option value="${t.value}" ${t.value == sensorTypeId ? 'selected' : ''}><c:out value="${t.label}"/></option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No levels for these filters: the rules use their default levels here.</p>
            <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add levels</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th>Kitchen zone</th><th>Home</th><th>Sensor type</th><th class="num">Warning level</th>
                    <th class="num">Alarm level</th><th>Unit</th><th>Last changed</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="t" items="${result.items}">
                    <tr>
                        <td class="strong"><c:out value="${t.zoneName}"/></td>
                        <td><c:out value="${t.homeName}"/></td>
                        <td><c:out value="${t.sensorTypeName}"/></td>
                        <td class="num">${gg:plain(t.warnLevel)}</td>
                        <td class="num">${gg:plain(t.alarmLevel)}</td>
                        <td><c:out value="${empty t.unit ? '–' : t.unit}"/></td>
                        <td class="small">${gg:time(t.updatedAt)}<c:if test="${not empty t.updatedByName}"><br><span class="muted">by <c:out value="${t.updatedByName}"/></span></c:if></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/ZoneThresholdController?action=edit&amp;id=${t.zoneThresholdId}">Edit</a>
                                <form method="post" action="${ctx}/ZoneThresholdController"
                                      data-confirm="Delete the ${fn:escapeXml(t.sensorTypeName)} levels of ${fn:escapeXml(t.zoneName)}? The rules will use their default levels there.">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${t.zoneThresholdId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/ZoneThresholdController">
            <c:param name="zoneId" value="${zoneId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/ZoneThresholdController">
            <c:param name="zoneId" value="${zoneId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} level pairs</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
