<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Alerts" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:url var="exportUrl" value="/AlertController">
    <c:param name="action" value="export"/><c:param name="status" value="${status}"/>
    <c:param name="label" value="${label}"/><c:param name="source" value="${source}"/>
</c:url>
<div class="page-head">
    <h1>Alerts</h1>
    <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
</div>

<form class="toolbar" method="get" action="${ctx}/AlertController">
    <select name="status" aria-label="Status">
        <option value="">Any status</option>
        <c:forEach var="s" items="${statuses}">
            <option value="${s}" ${s == status ? 'selected' : ''}>${s == 'UNRESOLVED' ? 'Not resolved yet' : gg:label(s)}</option>
        </c:forEach>
    </select>
    <select name="label" aria-label="Label">
        <option value="">Any label</option>
        <c:forEach var="l" items="${labels}">
            <c:if test="${l != 'SAFE'}"><option value="${l}" ${l == label ? 'selected' : ''}>${gg:label(l)}</option></c:if>
        </c:forEach>
    </select>
    <select name="source" aria-label="Raised by">
        <option value="">Rules or model</option>
        <option value="RULE" ${source == 'RULE' ? 'selected' : ''}>Rules</option>
        <option value="ML" ${source == 'ML' ? 'selected' : ''}>Model</option>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty"><p>No alert matches these filters.</p></div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid">
                <thead>
                <tr><th class="num">ID</th><th>Raised (UTC)</th><th>Alert</th><th>Device</th><th>Raised by</th>
                    <th class="num">Leak risk</th><th>Status</th><th class="num">Evidence</th><th class="num">Tickets</th></tr>
                </thead>
                <tbody>
                <c:forEach var="a" items="${result.items}">
                    <tr class="${a.resolved ? 'row-quiet' : ''}">
                        <td class="num"><a href="${ctx}/AlertController?action=view&amp;id=${a.alertId}">${a.alertId}</a></td>
                        <td class="nowrap">${gg:time(a.createdAt)}</td>
                        <td><span class="badge badge-${a.severity}">${gg:label(a.label)}</span></td>
                        <td><c:out value="${a.deviceCode}"/></td>
                        <td>${a.source == 'ML' ? 'Model' : 'Rules'}</td>
                        <td class="num">${gg:pct(a.riskScore)}</td>
                        <td>${gg:label(a.status)}</td>
                        <td class="num">${a.evidenceCount}</td>
                        <td class="num">${a.ticketCount}</td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/AlertController"><c:param name="status" value="${status}"/><c:param name="label" value="${label}"/><c:param name="source" value="${source}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/AlertController"><c:param name="status" value="${status}"/><c:param name="label" value="${label}"/><c:param name="source" value="${source}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Newer</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} alerts</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Older</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
