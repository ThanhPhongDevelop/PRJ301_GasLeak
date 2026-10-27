<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit emergency contact' : 'Add emergency contact'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/EmergencyContactController">Back to contacts</a>
</div>

<form method="post" action="${ctx}/EmergencyContactController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">

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
        <span>Full name</span>
        <input name="fullName" maxlength="80" required value="<c:out value='${form.fullName}'/>">
        <c:if test="${not empty errors.fullName}"><em class="field-error"><c:out value="${errors.fullName}"/></em></c:if>
    </label>

    <label class="field">
        <span>Phone</span>
        <input type="tel" name="phone" maxlength="20" required value="<c:out value='${form.phone}'/>">
        <small class="field-hint">For example 0900 000 001, or 114 for the fire brigade.</small>
        <c:if test="${not empty errors.phone}"><em class="field-error"><c:out value="${errors.phone}"/></em></c:if>
    </label>

    <label class="field">
        <span>Email</span>
        <input type="email" name="email" maxlength="100" value="<c:out value='${form.email}'/>">
        <c:if test="${not empty errors.email}"><em class="field-error"><c:out value="${errors.email}"/></em></c:if>
    </label>

    <label class="field">
        <span>Relation</span>
        <input name="relation" maxlength="40" value="<c:out value='${form.relation}'/>">
        <small class="field-hint">Optional, for example Owner, Neighbour, Building security.</small>
        <c:if test="${not empty errors.relation}"><em class="field-error"><c:out value="${errors.relation}"/></em></c:if>
    </label>

    <label class="field">
        <span>Priority</span>
        <input type="number" name="priority" min="1" max="99" step="1" required value="<c:out value='${form.priority}'/>">
        <small class="field-hint">1 is called first.</small>
        <c:if test="${not empty errors.priority}"><em class="field-error"><c:out value="${errors.priority}"/></em></c:if>
    </label>

    <label class="field">
        <span>Notify from</span>
        <select name="notifyMinLabel">
            <c:forEach var="label" items="${notifyLabels}">
                <option value="${label}" ${label == form.notifyMinLabel ? 'selected' : ''}><c:out value="${gg:label(label)}"/></option>
            </c:forEach>
        </select>
        <small class="field-hint">The least serious alert this person should hear about; more serious alerts reach them too.</small>
        <c:if test="${not empty errors.notifyMinLabel}"><em class="field-error"><c:out value="${errors.notifyMinLabel}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add contact'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
