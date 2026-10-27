<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Sensor types" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('SENSOR_TYPE', 'WRITE')}" />
<c:url var="exportUrl" value="/SensorTypeController"><c:param name="action" value="export"/><c:param name="q" value="${q}"/></c:url>

<div class="page-head">
    <h1>Sensor types</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${ctx}/SensorTypeController?action=new">Add sensor type</a></c:if>
    </div>
</div>
<p class="lead">A reading outside the valid range of its type raises a sensor fault. The four built-in types are found by name by the
    rules, so they keep their names and cannot be deleted; a type that sensors, levels or calibrations use cannot be deleted either.</p>

<form class="toolbar" method="get" action="${ctx}/SensorTypeController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search by name or unit" aria-label="Search sensor types">
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No sensor type matches this search.</p>
            <c:if test="${canWrite}"><a class="btn" href="${ctx}/SensorTypeController?action=new">Add a sensor type</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Name</th><th>Unit</th><th>Valid range</th><th class="num">Sensors</th>
                    <th class="num">Zone levels</th><th class="num">Calibrations</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="t" items="${result.items}">
                    <tr>
                        <td class="num">${t.sensorTypeId}</td>
                        <td class="strong"><c:out value="${t.name}"/><c:if test="${t.builtIn}"> <span class="badge badge-info">Built-in</span></c:if></td>
                        <td><c:out value="${empty t.unit ? '–' : t.unit}"/></td>
                        <td class="nowrap">
                            <c:choose>
                                <c:when test="${empty t.minValid}"><span class="muted">No check</span></c:when>
                                <c:otherwise>${gg:plain(t.minValid)} to ${gg:plain(t.maxValid)}</c:otherwise>
                            </c:choose>
                        </td>
                        <td class="num">${t.sensorCount}</td>
                        <td class="num">${t.thresholdCount}</td>
                        <td class="num">${t.calibrationCount}</td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/SensorTypeController?action=edit&amp;id=${t.sensorTypeId}">Edit</a>
                                <c:if test="${not t.locked}">
                                    <form method="post" action="${ctx}/SensorTypeController" data-confirm="Delete the sensor type ${fn:escapeXml(t.name)}?">
                                        <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="id" value="${t.sensorTypeId}">
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
        <c:url var="prevUrl" value="/SensorTypeController"><c:param name="q" value="${q}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/SensorTypeController"><c:param name="q" value="${q}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} sensor types</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
