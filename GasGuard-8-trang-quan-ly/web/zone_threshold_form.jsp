<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit zone levels' : 'Add zone levels'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/ZoneThresholdController">Back to thresholds</a>
</div>

<form method="post" action="${ctx}/ZoneThresholdController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">
    <c:if test="${isEdit}">
        <%-- saved levels keep their zone and sensor type: show them, send them unchanged --%>
        <input type="hidden" name="kitchenZoneId" value="<c:out value='${form.kitchenZoneId}'/>">
        <input type="hidden" name="sensorTypeId" value="<c:out value='${form.sensorTypeId}'/>">
    </c:if>

    <label class="field">
        <span>Kitchen zone</span>
        <select ${isEdit ? 'disabled' : 'name="kitchenZoneId" required'}>
            <option value="">Choose a zone</option>
            <c:forEach var="z" items="${zones}">
                <option value="${z.value}" ${z.value == form.kitchenZoneId ? 'selected' : ''}><c:out value="${z.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.kitchenZoneId}"><em class="field-error"><c:out value="${errors.kitchenZoneId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Sensor type</span>
        <select ${isEdit ? 'disabled' : 'name="sensorTypeId" required'}>
            <option value="">Choose a type</option>
            <c:forEach var="t" items="${sensorTypes}">
                <option value="${t.value}" ${t.value == form.sensorTypeId ? 'selected' : ''}><c:out value="${t.label}"/></option>
            </c:forEach>
        </select>
        <c:choose>
            <c:when test="${isEdit}"><small class="field-hint">To set levels for another zone or sensor type, add new levels instead.</small></c:when>
            <c:otherwise><small class="field-hint">A zone keeps one pair of levels per sensor type: if this pair already has levels, they are replaced.</small></c:otherwise>
        </c:choose>
        <c:if test="${not empty errors.sensorTypeId}"><em class="field-error"><c:out value="${errors.sensorTypeId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Warning level</span>
        <input type="number" name="warnLevel" step="any" min="0" required value="<c:out value='${form.warnLevel}'/>">
        <c:if test="${not empty errors.warnLevel}"><em class="field-error"><c:out value="${errors.warnLevel}"/></em></c:if>
    </label>

    <label class="field">
        <span>Alarm level</span>
        <input type="number" name="alarmLevel" step="any" min="0" required value="<c:out value='${form.alarmLevel}'/>">
        <small class="field-hint">In the sensor's unit: ADC counts from 0 to 4095 for MQ-2 and MQ-135, as the ESP32 reads them.
            The alarm level must be above the warning level.</small>
        <c:if test="${not empty errors.alarmLevel}"><em class="field-error"><c:out value="${errors.alarmLevel}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save levels' : 'Add levels'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
