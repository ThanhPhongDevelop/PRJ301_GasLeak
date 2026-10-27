<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit sensor type' : 'Add sensor type'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/SensorTypeController">Back to sensor types</a>
</div>

<form method="post" action="${ctx}/SensorTypeController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">

    <label class="field">
        <span>Name</span>
        <c:choose>
            <c:when test="${builtIn}">
                <input name="name" maxlength="40" readonly aria-readonly="true" value="<c:out value='${form.name}'/>">
                <small class="field-hint">Built-in type: the rules find it by this name, so the name cannot change.</small>
            </c:when>
            <c:otherwise>
                <input name="name" maxlength="40" required value="<c:out value='${form.name}'/>">
            </c:otherwise>
        </c:choose>
        <c:if test="${not empty errors.name}"><em class="field-error"><c:out value="${errors.name}"/></em></c:if>
    </label>

    <label class="field">
        <span>Unit</span>
        <input name="unit" maxlength="20" value="<c:out value='${form.unit}'/>">
        <small class="field-hint">For example ADC for MQ sensors, °C, %RH.</small>
        <c:if test="${not empty errors.unit}"><em class="field-error"><c:out value="${errors.unit}"/></em></c:if>
    </label>

    <label class="field">
        <span>Lowest valid value</span>
        <input type="number" name="minValid" step="any" value="<c:out value='${form.minValid}'/>">
        <c:if test="${not empty errors.minValid}"><em class="field-error"><c:out value="${errors.minValid}"/></em></c:if>
    </label>

    <label class="field">
        <span>Highest valid value</span>
        <input type="number" name="maxValid" step="any" value="<c:out value='${form.maxValid}'/>">
        <small class="field-hint">Fill both ends or leave both empty. A reading outside the range is reported as a sensor fault,
            for example 0 to 4095 for the ESP32's 12-bit ADC.</small>
        <c:if test="${not empty errors.maxValid}"><em class="field-error"><c:out value="${errors.maxValid}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add sensor type'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
