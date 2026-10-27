<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Gas sensors" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('GAS_SENSOR', 'WRITE')}" />
<c:url var="exportUrl" value="/GasSensorController">
    <c:param name="action" value="export"/><c:param name="q" value="${q}"/><c:param name="deviceId" value="${deviceId}"/>
    <c:param name="zoneId" value="${zoneId}"/><c:param name="status" value="${status}"/>
</c:url>

<div class="page-head">
    <h1>Gas sensors</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${ctx}/GasSensorController?action=new">Add sensor</a></c:if>
    </div>
</div>
<p class="lead">Which sensor is wired to which device, on which pin, and in which kitchen zone. Only active sensors take part in the zone levels.</p>

<form class="toolbar" method="get" action="${ctx}/GasSensorController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search sensor or device code" aria-label="Search sensors">
    <select name="deviceId" aria-label="Device">
        <option value="">All devices</option>
        <c:forEach var="d" items="${devices}">
            <option value="${d.value}" ${d.value == deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
        </c:forEach>
    </select>
    <select name="zoneId" aria-label="Kitchen zone">
        <option value="">All zones</option>
        <c:forEach var="z" items="${zones}">
            <option value="${z.value}" ${z.value == zoneId ? 'selected' : ''}><c:out value="${z.label}"/></option>
        </c:forEach>
    </select>
    <select name="status" aria-label="Status">
        <option value="">Active or inactive</option>
        <option value="ACTIVE" ${status == 'ACTIVE' ? 'selected' : ''}>Active</option>
        <option value="INACTIVE" ${status == 'INACTIVE' ? 'selected' : ''}>Inactive</option>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No sensor matches these filters.</p>
            <c:if test="${canWrite}"><a class="btn" href="${ctx}/GasSensorController?action=new">Add a sensor</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Code</th><th>Device</th><th>Type</th><th>Kitchen zone</th><th>Pin</th>
                    <th>Installed</th><th>Status</th><c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="s" items="${result.items}">
                    <tr class="${s.status == 'ACTIVE' ? '' : 'row-quiet'}">
                        <td class="num">${s.gasSensorId}</td>
                        <td class="strong nowrap"><c:out value="${s.sensorCode}"/></td>
                        <td class="nowrap"><c:out value="${s.deviceCode}"/></td>
                        <td><c:out value="${s.sensorTypeName}"/><c:if test="${not empty s.unit}"> <span class="muted small">(<c:out value="${s.unit}"/>)</span></c:if></td>
                        <td><c:out value="${empty s.zoneName ? 'Not installed' : s.zoneName}"/></td>
                        <td><c:out value="${empty s.pinLabel ? '–' : s.pinLabel}"/></td>
                        <td class="nowrap"><c:out value="${empty s.installedAt ? '–' : s.installedAt}"/></td>
                        <td><span class="state ${s.status == 'ACTIVE' ? 'state-ok' : 'state-stop'}">${s.status == 'ACTIVE' ? 'Active' : 'Inactive'}</span></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/GasSensorController?action=edit&amp;id=${s.gasSensorId}">Edit</a>
                                <form method="post" action="${ctx}/GasSensorController" data-confirm="Delete the sensor ${fn:escapeXml(s.sensorCode)}?">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${s.gasSensorId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/GasSensorController">
            <c:param name="q" value="${q}"/><c:param name="deviceId" value="${deviceId}"/><c:param name="zoneId" value="${zoneId}"/>
            <c:param name="status" value="${status}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/GasSensorController">
            <c:param name="q" value="${q}"/><c:param name="deviceId" value="${deviceId}"/><c:param name="zoneId" value="${zoneId}"/>
            <c:param name="status" value="${status}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} sensors</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
