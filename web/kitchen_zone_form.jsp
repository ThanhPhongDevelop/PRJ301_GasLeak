<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit kitchen zone' : 'Add kitchen zone'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/KitchenZoneController">Back to zones</a>
</div>

<form method="post" action="${ctx}/KitchenZoneController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="${isEdit ? 'update' : 'create'}">
    <c:if test="${isEdit}"><input type="hidden" name="id" value="<c:out value='${form.id}'/>"></c:if>

    <label class="field">
        <span>Zone name</span>
        <input name="zoneName" maxlength="30" required value="<c:out value='${form.zoneName}'/>">
        <c:if test="${not empty errors.zoneName}"><em class="field-error"><c:out value="${errors.zoneName}"/></em></c:if>
    </label>

    <label class="field">
        <span>Home</span>
        <select name="homeId" required>
            <option value="">Choose a home</option>
            <c:forEach var="h" items="${homes}">
                <option value="${h.value}" ${h.value == form.homeId ? 'selected' : ''}><c:out value="${h.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.homeId}"><em class="field-error"><c:out value="${errors.homeId}"/></em></c:if>
    </label>

    <label class="field">
        <span>Alarm threshold (ppm)</span>
        <input type="number" name="thresholdPpm" step="any" min="0" required value="<c:out value='${form.thresholdPpm}'/>">
        <small class="field-hint">Gas level above which this zone raises an alert.</small>
        <c:if test="${not empty errors.thresholdPpm}"><em class="field-error"><c:out value="${errors.thresholdPpm}"/></em></c:if>
    </label>

    <label class="field">
        <span>Valve device</span>
        <select name="valveDeviceId">
            <option value="">No valve in this zone</option>
            <c:forEach var="d" items="${devices}">
                <option value="${d.value}" ${d.value == form.valveDeviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.valveDeviceId}"><em class="field-error"><c:out value="${errors.valveDeviceId}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add zone'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
