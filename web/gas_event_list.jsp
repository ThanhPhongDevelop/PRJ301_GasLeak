<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Sessions" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:url var="exportUrl" value="/GasEventController">
    <c:param name="action" value="export"/><c:param name="deviceId" value="${deviceId}"/>
    <c:param name="rule" value="${rule}"/><c:param name="human" value="${human}"/>
</c:url>
<div class="page-head">
    <h1>Sessions</h1>
    <a class="btn btn-quiet" href="${exportUrl}">Export CSV</a>
</div>
<p class="lead">Every session a device sent, with what the rules and the model concluded. Open a session to compare the
    raw and compensated readings or to give it a label.</p>

<form class="toolbar" method="get" action="${ctx}/GasEventController">
    <select name="deviceId" aria-label="Device">
        <option value="">All devices</option>
        <c:forEach var="d" items="${devices}">
            <option value="${d.value}" ${d.value == deviceId ? 'selected' : ''}><c:out value="${d.label}"/></option>
        </c:forEach>
    </select>
    <select name="rule" aria-label="Rule result">
        <option value="">Any rule result</option>
        <c:forEach var="l" items="${labels}">
            <option value="${l}" ${l == rule ? 'selected' : ''}>${gg:label(l)}</option>
        </c:forEach>
    </select>
    <select name="human" aria-label="Human label">
        <option value="">Labelled or not</option>
        <option value="NONE" ${human == 'NONE' ? 'selected' : ''}>Not labelled yet</option>
        <c:forEach var="l" items="${labels}">
            <option value="${l}" ${l == human ? 'selected' : ''}>Labelled ${gg:label(l)}</option>
        </c:forEach>
    </select>
    <button type="submit" class="btn btn-quiet">Apply</button>
</form>

<c:choose>
    <c:when test="${empty result.items}">
        <div class="empty"><p>No session matches these filters. Sessions appear here once a device or
            tool.DataReplayer sends data.</p></div>
    </c:when>
    <c:otherwise>
        <div class="table-wrap">
            <table class="grid grid-dense">
                <thead>
                <tr><th class="num">ID</th><th>Measured (UTC)</th><th>Device</th><th class="num">MQ-2 raw</th>
                    <th class="num">Compensated</th><th class="num">&deg;C</th><th class="num">%RH</th>
                    <th>Rule result</th><th>Model</th><th>Label</th><th class="num">Alerts</th></tr>
                </thead>
                <tbody>
                <c:forEach var="s" items="${result.items}">
                    <tr>
                        <td class="num"><a href="${ctx}/GasEventController?action=view&amp;id=${s.sessionId}">${s.sessionId}</a></td>
                        <td class="nowrap">${gg:time(s.measuredAt)}</td>
                        <td><c:out value="${s.deviceCode}"/> <span class="muted">#${s.deviceSeq}</span></td>
                        <td class="num">${gg:num(s.mq2Raw)}</td>
                        <td class="num">${gg:num(s.compensatedScore)}</td>
                        <td class="num">${gg:num(s.tempC)}</td>
                        <td class="num">${gg:num(s.rhPct)}</td>
                        <td><span class="badge badge-${gg:severity(s.ruleLabel)}">${gg:label(s.ruleLabel)}</span></td>
                        <td><c:choose><c:when test="${empty s.mlLabel}"><span class="muted">n/a</span></c:when>
                            <c:otherwise>${gg:label(s.mlLabel)} <span class="muted">${gg:pct(s.mlConfidence)}</span></c:otherwise></c:choose></td>
                        <td><c:choose><c:when test="${empty s.humanLabel}"><span class="muted">None</span></c:when>
                            <c:otherwise>${gg:label(s.humanLabel)}</c:otherwise></c:choose></td>
                        <td class="num">${s.alertCount}</td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <c:url var="prevUrl" value="/GasEventController"><c:param name="deviceId" value="${deviceId}"/><c:param name="rule" value="${rule}"/><c:param name="human" value="${human}"/><c:param name="page" value="${result.page - 1}"/></c:url>
        <c:url var="nextUrl" value="/GasEventController"><c:param name="deviceId" value="${deviceId}"/><c:param name="rule" value="${rule}"/><c:param name="human" value="${human}"/><c:param name="page" value="${result.page + 1}"/></c:url>
        <nav class="pager" aria-label="Pages">
            <c:if test="${not result.firstPage}"><a href="${prevUrl}">Newer</a></c:if>
            <span>Page ${result.page} of ${result.totalPages}, ${result.totalRows} sessions</span>
            <c:if test="${not result.lastPage}"><a href="${nextUrl}">Older</a></c:if>
        </nav>
    </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
