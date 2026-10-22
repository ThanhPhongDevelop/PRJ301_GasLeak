<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Kitchen zones" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('KITCHENZONE', 'WRITE')}" />
<c:url var="exportUrl" value="/KitchenZoneController">
    <c:param name="action" value="export"/><c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/>
</c:url>

<div class="page-head">
    <h1>Kitchen zones</h1>
    <div class="page-actions">
        <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
        <c:if test="${canWrite}"><a class="btn" href="${ctx}/KitchenZoneController?action=new">Add zone</a></c:if>
    </div>
</div>

<form class="toolbar" method="get" action="${ctx}/KitchenZoneController">
    <input type="search" name="q" value="<c:out value='${q}'/>" placeholder="Search by zone name" aria-label="Search by zone name">
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
            <p>No kitchen zone matches this search.</p>
            <c:if test="${canWrite}"><a class="btn" href="${ctx}/KitchenZoneController?action=new">Add the first zone</a></c:if>
        </div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Zone</th><th>Home</th><th class="num">Threshold (ppm)</th><th>Valve device</th>
                    <c:if test="${canWrite}"><th><span class="sr-only">Actions</span></th></c:if></tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${result.items}">
                    <tr>
                        <td class="num">${item.id}</td>
                        <td><c:out value="${item.zoneName}"/></td>
                        <td><c:out value="${item.homeName}"/></td>
                        <td class="num">${item.thresholdPpm}</td>
                        <td><c:out value="${empty item.valveDeviceCode ? 'None' : item.valveDeviceCode}"/></td>
                        <c:if test="${canWrite}">
                            <td class="row-actions">
                                <a href="${ctx}/KitchenZoneController?action=edit&amp;id=${item.id}">Edit</a>
                                <form method="post" action="${ctx}/KitchenZoneController" data-confirm="Delete this kitchen zone?">
                                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                                    <input type="hidden" name="action" value="delete">
                                    <input type="hidden" name="id" value="${item.id}">
                                    <button type="submit" class="link link-danger">Delete</button>
                                </form>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/KitchenZoneController">
            <c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/><c:param name="page" value="${result.page - 1}"/>
        </c:url>
        <c:url var="nextUrl" value="/KitchenZoneController">
            <c:param name="q" value="${q}"/><c:param name="homeId" value="${homeId}"/><c:param name="page" value="${result.page + 1}"/>
        </c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Previous</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} zones</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Next</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
