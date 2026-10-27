<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Emergency contacts" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('EMERGENCY_CONTACT', 'WRITE')}" />
<c:url var="exportUrl" value="/EmergencyContactController">
    <c:param name="action" value="export"/><c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/>
</c:url>
<c:url var="addUrl" value="/EmergencyContactController"><c:param name="action" value="new"/><c:param name="homeId" value="${homeId}"/></c:url>

<div class="page-head">
    <h1>Emergency contacts</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add contact</a></c:if>
    </div>
</div>
<p class="lead">People to call when a home has an alert, in priority order (1 is called first). Each contact hears about alerts from the level chosen in "Notify from" upwards.</p>

<form class="toolbar" method="get" action="${ctx}/EmergencyContactController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search by name or phone" aria-label="Search contacts">
    <select name="homeId" aria-label="Home">
        <option value="">All homes</option>
        <c:forEach var="h" items="${homes}">
            <option value="${h.value}" ${h.value == homeId ? 'selected' : ''}><c:out value="${h.label}"/></option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No contact matches this search.</p>
            <c:if test="${canWrite}"><a class="btn" href="${addUrl}">Add a contact</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th>Home</th><th class="num">Priority</th><th>Name</th><th>Phone</th><th>Email</th><th>Relation</th>
                    <th>Notify from</th><c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="ct" items="${result.items}">
                    <tr>
                        <td><c:out value="${ct.homeName}"/></td>
                        <td class="num">${ct.priority}</td>
                        <td class="strong"><c:out value="${ct.fullName}"/></td>
                        <td class="nowrap"><c:out value="${ct.phone}"/></td>
                        <td><c:out value="${empty ct.email ? '–' : ct.email}"/></td>
                        <td><c:out value="${empty ct.relation ? '–' : ct.relation}"/></td>
                        <td><span class="badge badge-${gg:severity(ct.notifyMinLabel)}"><c:out value="${gg:label(ct.notifyMinLabel)}"/></span></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/EmergencyContactController?action=edit&amp;id=${ct.contactId}">Edit</a>
                                <form method="post" action="${ctx}/EmergencyContactController" data-confirm="Delete the contact ${fn:escapeXml(ct.fullName)}?">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${ct.contactId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/EmergencyContactController">
            <c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/EmergencyContactController">
            <c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} contacts</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
