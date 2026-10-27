<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit device' : 'Register device'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/DeviceController">Back to devices</a>
</div>
<c:if test="${not isEdit}">
    <p class="lead">After you register the device, the next page shows its API key once. Copy it into
        <code>API_KEY</code> in <code>esp32_gasguard.ino</code> before you leave that page.</p>
</c:if>

<form method="post" action="${ctx}/DeviceController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">

    <label class="field">
        <span>Device code</span>
        <c:choose>
            <c:when test="${isEdit}">
                <input value="<c:out value='${form.deviceCode}'/>" readonly aria-readonly="true">
                <small class="field-hint">The code is set once, at registration.</small>
            </c:when>
            <c:otherwise>
                <input name="deviceCode" maxlength="30" required value="<c:out value='${form.deviceCode}'/>">
                <small class="field-hint">3 to 30 letters, digits, dots, dashes or underscores, for example ESP32-KITCHEN-02.</small>
            </c:otherwise>
        </c:choose>
        <c:if test="${not empty errors.deviceCode}"><em class="field-error"><c:out value="${errors.deviceCode}"/></em></c:if>
    </label>

    <label class="field">
        <span>Location</span>
        <input name="location" maxlength="80" value="<c:out value='${form.location}'/>">
        <small class="field-hint">Optional, for example Kitchen wall, left of the stove.</small>
        <c:if test="${not empty errors.location}"><em class="field-error"><c:out value="${errors.location}"/></em></c:if>
    </label>

    <label class="field">
        <span>Firmware version</span>
        <input name="firmware" maxlength="20" value="<c:out value='${form.firmware}'/>">
        <c:if test="${not empty errors.firmware}"><em class="field-error"><c:out value="${errors.firmware}"/></em></c:if>
    </label>

    <c:choose>
        <c:when test="${isEdit}">
            <label class="field">
                <span>Status</span>
                <select name="status">
                    <option value="ACTIVE" ${form.status == 'ACTIVE' ? 'selected' : ''}>Active</option>
                    <option value="INACTIVE" ${form.status == 'INACTIVE' ? 'selected' : ''}>Inactive</option>
                </select>
                <small class="field-hint">Inactive refuses the device's API key and frees its control lock. Use it instead of deleting a device with history.</small>
                <c:if test="${not empty errors.status}"><em class="field-error"><c:out value="${errors.status}"/></em></c:if>
            </label>
        </c:when>
        <c:otherwise><input type="hidden" name="status" value="ACTIVE"></c:otherwise>
    </c:choose>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Register and show the key'}</button>
    </div>
</form>

<c:if test="${isEdit and not empty device}">
    <div class="panel">
        <h2>API key</h2>
        <dl class="facts">
            <div><dt>Last seen (UTC)</dt><dd><c:out value="${empty device.lastSeen ? 'Never' : gg:time(device.lastSeen)}"/></dd></div>
            <div><dt>Connection</dt><dd>${device.online ? 'Online' : 'Offline'}</dd></div>
            <div><dt>Kitchen zones</dt><dd><c:out value="${empty device.zoneNames ? 'No sensors yet' : device.zoneNames}"/></dd></div>
        </dl>
        <p class="muted">The server keeps only a SHA-256 of the key, so a lost key cannot be shown again: issue a new one.
            The current key stops working at once.</p>
        <form method="post" action="${ctx}/DeviceController"
              data-confirm="Issue a new API key for ${fn:escapeXml(form.deviceCode)}? The current key stops working at once.">
            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
            <input type="hidden" name="action" value="rotateKey">
            <input type="hidden" name="id" value="<c:out value='${form.id}'/>">
            <button type="submit" class="btn btn-quiet">Issue a new API key</button>
        </form>
    </div>
</c:if>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
