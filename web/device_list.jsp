<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Devices" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('DEVICE', 'WRITE')}" />
<c:url var="exportUrl" value="/DeviceController">
    <c:param name="action" value="export"/><c:param name="q" value="${q}"/><c:param name="status" value="${status}"/>
</c:url>

<div class="page-head">
    <h1>Devices</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${ctx}/DeviceController?action=new">Register device</a></c:if>
    </div>
</div>
<p class="lead">ESP32 boards that send sessions to the web. Each device signs its requests with its own API key, shown once when the
    device is registered or given a new key. An inactive device is refused until it is set active again.</p>

<form class="toolbar" method="get" action="${ctx}/DeviceController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search by code or location" aria-label="Search devices">
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
            <p>No device matches this search.</p>
            <c:if test="${canWrite}"><a class="btn" href="${ctx}/DeviceController?action=new">Register a device</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Code</th><th>Location</th><th>Kitchen zones</th>
                    <th>Status, last seen (UTC)</th><th>Controlled by</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="d" items="${result.items}">
                    <tr class="${d.status == 'ACTIVE' ? '' : 'row-quiet'}">
                        <td class="num">${d.deviceId}</td>
                        <td class="strong nowrap"><c:out value="${d.deviceCode}"/></td>
                        <td><c:out value="${empty d.location ? '–' : d.location}"/><c:if test="${not empty d.firmware}"><br><span class="muted small">Firmware <c:out value="${d.firmware}"/></span></c:if></td>
                        <td><c:out value="${empty d.zoneNames ? 'No sensors yet' : d.zoneNames}"/></td>
                        <td class="nowrap">
                            <span class="state ${d.status == 'ACTIVE' ? 'state-ok' : 'state-stop'}">${d.status == 'ACTIVE' ? 'Active' : 'Inactive'}</span>
                            <c:choose>
                                <c:when test="${d.online}"> <span class="badge badge-ok">Online</span><br><span class="small">${gg:time(d.lastSeen)}</span></c:when>
                                <c:when test="${empty d.lastSeen}"><br><span class="muted small">Never connected</span></c:when>
                                <c:otherwise><br><span class="muted small">${gg:time(d.lastSeen)}</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${empty d.controlOwnerName ? '–' : d.controlOwnerName}"/></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/DeviceController?action=edit&amp;id=${d.deviceId}">Edit</a>
                                <form method="post" action="${ctx}/DeviceController"
                                      data-confirm="Issue a new API key for ${fn:escapeXml(d.deviceCode)}? The current key stops working at once, so the board must get the new key in its firmware.">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="rotateKey">
                                    <input type="hidden" name="id" value="${d.deviceId}">
                                    <button type="submit" class="link">New key</button>
                                </form>
                                <form method="post" action="${ctx}/DeviceController" data-confirm="Delete the device ${fn:escapeXml(d.deviceCode)}? A device with history can only be set inactive.">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${d.deviceId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/DeviceController">
            <c:param name="q" value="${q}"/><c:param name="status" value="${status}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/DeviceController">
            <c:param name="q" value="${q}"/><c:param name="status" value="${status}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} devices</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
