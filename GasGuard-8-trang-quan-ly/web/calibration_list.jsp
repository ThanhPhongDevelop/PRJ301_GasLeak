<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Calibrations" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('CALIBRATION', 'WRITE')}" />
<c:url var="exportUrl" value="/CalibrationController">
    <c:param name="action" value="export"/><c:param name="deviceId" value="${deviceId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/>
</c:url>
<c:url var="addUrl" value="/CalibrationController">
    <c:param name="action" value="new"/><c:param name="deviceId" value="${deviceId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/>
</c:url>

<div class="page-head">
    <h1>Calibrations</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add calibration</a></c:if>
    </div>
</div>
<p class="lead">Bench corrections of a sensor type on a device: corrected = raw &times; scale + offset. Each new calibration of the same
    device and sensor type gets the next version number.</p>

<form class="toolbar" method="get" action="${ctx}/CalibrationController">
    <select name="deviceId" aria-label="Device">
        <option value="">All devices</option>
        <c:forEach var="d" items="${devices}">
            <option value="${d.value}" ${d.value == deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
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
            <p>No calibration for these filters.</p>
            <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add a calibration</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th>Device</th><th>Sensor type</th><th class="num">Version</th><th class="num">Offset</th><th class="num">Scale</th>
                    <th>Valid until</th><th>Created by</th><c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="cal" items="${result.items}">
                    <tr class="${cal.expired ? 'row-quiet' : ''}">
                        <td class="strong nowrap"><c:out value="${cal.deviceCode}"/></td>
                        <td><c:out value="${cal.sensorTypeName}"/></td>
                        <td class="num">${cal.version}</td>
                        <td class="num">${gg:plain(cal.offsetVal)}</td>
                        <td class="num">${gg:plain(cal.scaleVal)}</td>
                        <td class="nowrap">
                            <c:out value="${empty cal.validUntil ? 'No end date' : cal.validUntil}"/>
                            <c:if test="${cal.expired}"> <span class="badge badge-warning">Expired</span></c:if>
                        </td>
                        <td><c:out value="${empty cal.createdByName ? '–' : cal.createdByName}"/></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/CalibrationController?action=edit&amp;id=${cal.calibId}">Edit</a>
                                <form method="post" action="${ctx}/CalibrationController"
                                      data-confirm="Delete version ${cal.version} of the ${fn:escapeXml(cal.sensorTypeName)} calibration of ${fn:escapeXml(cal.deviceCode)}?">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${cal.calibId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/CalibrationController">
            <c:param name="deviceId" value="${deviceId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/CalibrationController">
            <c:param name="deviceId" value="${deviceId}"/><c:param name="sensorTypeId" value="${sensorTypeId}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} calibrations</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
