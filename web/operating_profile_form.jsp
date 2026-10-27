<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="fromVersion" value="${not empty basedOn}" />
<c:set var="pageTitle" value="${fromVersion ? 'Edit compensation profile' : 'New compensation profile'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/OperatingProfileController">Back to profiles</a>
</div>
<p class="lead">
    <c:choose>
        <c:when test="${fromVersion}">Starting from version #${basedOn.profileId} of <c:out value="${basedOn.deviceCode}"/>
            (<c:out value="${basedOn.source}"/>${basedOn.current ? ', in use' : ''}). Saving adds a new version that is used from
            now on; version #${basedOn.profileId} stays in the history.</c:when>
        <c:otherwise>Coefficients typed in after a bench test. Saving adds the newest version of the device's profile, which is
            used from now on. To learn them from data instead, run <code>tool.CompensationFitter</code>.</c:otherwise>
    </c:choose>
</p>

<form method="post" action="${ctx}/OperatingProfileController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">
    <c:if test="${fromVersion}"><input type="hidden" name="deviceId" value="<c:out value='${form.deviceId}'/>"></c:if>

    <label class="field">
        <span>Device</span>
        <select ${fromVersion ? 'disabled' : 'name="deviceId" required'}>
            <option value="">Choose a device</option>
            <c:forEach var="d" items="${devices}">
                <option value="${d.value}" ${d.value == form.deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
            </c:forEach>
        </select>
        <c:if test="${not empty errors.deviceId}"><em class="field-error"><c:out value="${errors.deviceId}"/></em></c:if>
    </label>

    <fieldset>
        <legend>Reference conditions</legend>
        <label class="field">
            <span>Reference temperature (°C)</span>
            <input type="number" name="tRef" step="any" min="-40" max="80" required value="<c:out value='${form.tRef}'/>">
            <c:if test="${not empty errors.tRef}"><em class="field-error"><c:out value="${errors.tRef}"/></em></c:if>
        </label>
        <label class="field">
            <span>Reference humidity (%RH)</span>
            <input type="number" name="rhRef" step="any" min="0" max="100" required value="<c:out value='${form.rhRef}'/>">
            <small class="field-hint">At these conditions no correction is applied.</small>
            <c:if test="${not empty errors.rhRef}"><em class="field-error"><c:out value="${errors.rhRef}"/></em></c:if>
        </label>
    </fieldset>

    <fieldset>
        <legend>MQ-2 (gas)</legend>
        <label class="field">
            <span>Change per °C (b<sub>T</sub>)</span>
            <input type="number" name="mq2BT" step="any" min="-1000" max="1000" required value="<c:out value='${form.mq2BT}'/>">
            <c:if test="${not empty errors.mq2BT}"><em class="field-error"><c:out value="${errors.mq2BT}"/></em></c:if>
        </label>
        <label class="field">
            <span>Change per %RH (b<sub>RH</sub>)</span>
            <input type="number" name="mq2BRh" step="any" min="-1000" max="1000" required value="<c:out value='${form.mq2BRh}'/>">
            <c:if test="${not empty errors.mq2BRh}"><em class="field-error"><c:out value="${errors.mq2BRh}"/></em></c:if>
        </label>
    </fieldset>

    <fieldset>
        <legend>MQ-135 (smoke, fumes)</legend>
        <label class="field">
            <span>Change per °C (b<sub>T</sub>)</span>
            <input type="number" name="mq135BT" step="any" min="-1000" max="1000" required value="<c:out value='${form.mq135BT}'/>">
            <c:if test="${not empty errors.mq135BT}"><em class="field-error"><c:out value="${errors.mq135BT}"/></em></c:if>
        </label>
        <label class="field">
            <span>Change per %RH (b<sub>RH</sub>)</span>
            <input type="number" name="mq135BRh" step="any" min="-1000" max="1000" required value="<c:out value='${form.mq135BRh}'/>">
            <small class="field-hint">In ADC counts. 0 means no correction for that factor.</small>
            <c:if test="${not empty errors.mq135BRh}"><em class="field-error"><c:out value="${errors.mq135BRh}"/></em></c:if>
        </label>
    </fieldset>

    <div class="form-actions">
        <button type="submit" class="btn">Save as the version in use</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
