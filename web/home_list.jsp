<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Homes" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('HOME', 'WRITE')}" />
<c:url var="exportUrl" value="/HomeController"><c:param name="action" value="export"/><c:param name="q" value="${q}"/></c:url>

<div class="page-head">
    <h1>Homes</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${ctx}/HomeController?action=new">Add home</a></c:if>
    </div>
</div>
<p class="lead">A home groups its kitchen zones and the emergency contacts who are called when an alert opens.</p>

<form class="toolbar" method="get" action="${ctx}/HomeController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search by name or address" aria-label="Search homes">
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty">
            <p>No home matches this search.</p>
            <c:if test="${canWrite}"><a class="btn" href="${ctx}/HomeController?action=new">Add the first home</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Home</th><th>Address</th><th>Owner</th><th class="num">Zones</th>
                    <th class="num">Contacts</th><th>Created (UTC)</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="h" items="${result.items}">
                    <tr>
                        <td class="num">${h.homeId}</td>
                        <td><c:out value="${h.homeName}"/></td>
                        <td><c:out value="${empty h.address ? '–' : h.address}"/></td>
                        <td><c:out value="${empty h.ownerName ? 'No owner yet' : h.ownerName}"/></td>
                        <td class="num">
                            <c:choose>
                                <c:when test="${me.can('KITCHENZONE', 'READ')}"><a href="${ctx}/KitchenZoneController?homeId=${h.homeId}" title="Kitchen zones of this home">${h.zoneCount}</a></c:when>
                                <c:otherwise>${h.zoneCount}</c:otherwise>
                            </c:choose>
                        </td>
                        <td class="num">
                            <c:choose>
                                <c:when test="${me.can('EMERGENCY_CONTACT', 'READ')}"><a href="${ctx}/EmergencyContactController?homeId=${h.homeId}" title="Emergency contacts of this home">${h.contactCount}</a></c:when>
                                <c:otherwise>${h.contactCount}</c:otherwise>
                            </c:choose>
                        </td>
                        <td class="nowrap">${gg:time(h.createdAt)}</td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/HomeController?action=edit&amp;id=${h.homeId}">Edit</a>
                                <form method="post" action="${ctx}/HomeController" data-confirm="Delete the home ${fn:escapeXml(h.homeName)}?">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${h.homeId}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/HomeController"><c:param name="q" value="${q}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/HomeController"><c:param name="q" value="${q}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} homes</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
