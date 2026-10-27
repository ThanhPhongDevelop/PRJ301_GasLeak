<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit calibration' : 'Add calibration'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/><c:if test="${not empty saved}"> <span class="badge badge-info">Version ${saved.version}</span></c:if></h1>
    <a class="btn btn-quiet" href="${ctx}/CalibrationController">Back to calibrations</a>
</div>

<form method="post" action="${ctx}/CalibrationController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">
    <c:if test="${isEdit}">
        <%-- a calibration stays with its device and sensor type: show them, send them unchanged --%>
        <input type="hidden" name="deviceId" value="<c:out value='${form.deviceId}'/>">
        <input type="hidden" name="sensorTypeId" value="<c:out value='${form.sensorTypeId}'/>">
    </c:if>

    <label class="field">
        <span>Device</span>
        <select ${isEdit ? 'disabled' : 'name="deviceId" required'}>
            <option value="">Choose a device</option>
            <c:forEach var="d" items="${devices}">
                <option value="${d.value}" ${d.value == form.deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.deviceId}"><em class="field-error"><c:out value="${errors.deviceId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Sensor type</span>
        <select ${isEdit ? 'disabled' : 'name="sensorTypeId" required'}>
            <option value="">Choose a type</option>
            <c:forEach var="t" items="${sensorTypes}">
                <option value="${t.value}" ${t.value == form.sensorTypeId ? 'selected' : ''}><c:out value="${t.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${isEdit}"><small class="field-hint">To calibrate another device or sensor type, add a new calibration.</small></c:if>
        <c:if test="${not empty errors.sensorTypeId}"><em class="field-error"><c:out value="${errors.sensorTypeId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Offset</span>
        <input type="number" name="offsetVal" step="any" required value="<c:out value='${form.offsetVal}'/>">
        <small class="field-hint">Added after scaling, in the sensor's unit. 0 means no offset.</small>
        <c:if test="${not empty errors.offsetVal}"><em class="field-error"><c:out value="${errors.offsetVal}"/></em></c:if>
    </label>

    <label class="field">
        <span>Scale</span>
        <input type="number" name="scaleVal" step="any" min="0" required value="<c:out value='${form.scaleVal}'/>">
        <small class="field-hint">Corrected = raw &times; scale + offset. 1 means no scaling.</small>
        <c:if test="${not empty errors.scaleVal}"><em class="field-error"><c:out value="${errors.scaleVal}"/></em></c:if>
    </label>

    <label class="field">
        <span>Valid until</span>
        <input type="date" name="validUntil" value="<c:out value='${form.validUntil}'/>">
        <small class="field-hint">Optional. MQ sensors drift, so plan the next bench test; after this date the calibration shows as expired.</small>
        <c:if test="${not empty errors.validUntil}"><em class="field-error"><c:out value="${errors.validUntil}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add calibration'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
