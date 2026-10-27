<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit gas sensor' : 'Add gas sensor'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/GasSensorController">Back to sensors</a>
</div>

<form method="post" action="${ctx}/GasSensorController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">

    <label class="field">
        <span>Sensor code</span>
        <input name="sensorCode" maxlength="30" required value="<c:out value='${form.sensorCode}'/>">
        <small class="field-hint">Unique, 2 to 30 letters, digits, dots, dashes or underscores, for example MQ2-01.</small>
        <c:if test="${not empty errors.sensorCode}"><em class="field-error"><c:out value="${errors.sensorCode}"/></em></c:if>
    </label>

    <label class="field">
        <span>Device</span>
        <select name="deviceId" required>
            <option value="">Choose a device</option>
            <c:forEach var="d" items="${devices}">
                <option value="${d.value}" ${d.value == form.deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.deviceId}"><em class="field-error"><c:out value="${errors.deviceId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Sensor type</span>
        <select name="sensorTypeId" required>
            <option value="">Choose a type</option>
            <c:forEach var="t" items="${sensorTypes}">
                <option value="${t.value}" ${t.value == form.sensorTypeId ? 'selected' : ''}><c:out value="${t.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.sensorTypeId}"><em class="field-error"><c:out value="${errors.sensorTypeId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Kitchen zone</span>
        <select name="kitchenZoneId">
            <option value="">Not installed in a zone yet</option>
            <c:forEach var="z" items="${zones}">
                <option value="${z.value}" ${z.value == form.kitchenZoneId ? 'selected' : ''}><c:out value="${z.label}"/></option>
            </c:forEach>
        </select>
        <small class="field-hint">The zone's levels apply to this sensor once it is installed there.</small>
        <c:if test="${not empty errors.kitchenZoneId}"><em class="field-error"><c:out value="${errors.kitchenZoneId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Pin</span>
        <input name="pinLabel" maxlength="20" value="<c:out value='${form.pinLabel}'/>">
        <small class="field-hint">ESP32 pin, for example D34 for MQ-2, D35 for MQ-135, D4 for DHT22.</small>
        <c:if test="${not empty errors.pinLabel}"><em class="field-error"><c:out value="${errors.pinLabel}"/></em></c:if>
    </label>

    <label class="field">
        <span>Installed on</span>
        <input type="date" name="installedAt" value="<c:out value='${form.installedAt}'/>">
        <c:if test="${not empty errors.installedAt}"><em class="field-error"><c:out value="${errors.installedAt}"/></em></c:if>
    </label>

    <label class="field">
        <span>Status</span>
        <select name="status">
            <option value="ACTIVE" ${form.status == 'ACTIVE' ? 'selected' : ''}>Active</option>
            <option value="INACTIVE" ${form.status == 'INACTIVE' ? 'selected' : ''}>Inactive</option>
        </select>
        <small class="field-hint">Set a broken or removed sensor to Inactive instead of deleting it, to keep its history.</small>
        <c:if test="${not empty errors.status}"><em class="field-error"><c:out value="${errors.status}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add sensor'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
