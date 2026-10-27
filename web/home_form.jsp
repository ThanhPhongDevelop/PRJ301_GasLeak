<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isEdit" value="${not empty form.id}" />
<c:set var="pageTitle" value="${isEdit ? 'Edit home' : 'Add home'}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1><c:out value="${pageTitle}"/></h1>
    <a class="btn btn-quiet" href="${ctx}/HomeController">Back to homes</a>
</div>

<form method="post" action="${ctx}/HomeController" class="panel form form-narrow">
    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
    <input type="hidden" name="action" value="save">
    <input type="hidden" name="id" value="<c:out value='${form.id}'/>">

    <label class="field">
        <span>Home name</span>
        <input name="homeName" maxlength="80" required value="<c:out value='${form.homeName}'/>">
        <c:if test="${not empty errors.homeName}"><em class="field-error"><c:out value="${errors.homeName}"/></em></c:if>
    </label>

    <label class="field">
        <span>Address</span>
        <input name="address" maxlength="200" value="<c:out value='${form.address}'/>">
        <small class="field-hint">Optional. Shown to the technician who comes to check the kitchen.</small>
        <c:if test="${not empty errors.address}"><em class="field-error"><c:out value="${errors.address}"/></em></c:if>
    </label>

    <label class="field">
        <span>Owner</span>
        <select name="ownerUserId">
            <option value="">No owner yet</option>
            <c:forEach var="u" items="${owners}">
                <option value="${u.value}" ${u.value == form.ownerUserId ? 'selected' : ''}><c:out value="${u.label}"/></option>
            </c:forEach>
        </select>
        <small class="field-hint">The account that lives in this home, usually a Homeowner.</small>
        <c:if test="${not empty errors.ownerUserId}"><em class="field-error"><c:out value="${errors.ownerUserId}"/></em></c:if>
    </label>

    <div class="form-actions">
        <button type="submit" class="btn">${isEdit ? 'Save changes' : 'Add home'}</button>
    </div>
</form>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
