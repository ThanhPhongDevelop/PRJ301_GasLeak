<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<c:set var="pageTitle" value="Session ${event.sessionId}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1>Session ${event.sessionId}</h1>
    <a class="btn btn-quiet" href="${ctx}/GasEventController">Back to sessions</a>
</div>

<div class="detail-grid">
    <section class="panel">
        <h2>Before and after compensation</h2>
        <p class="muted">Compensation removes the part of each reading explained by temperature and humidity, so
            only a real change in gas moves the compensated value.</p>
        <table class="grid compare">
            <thead><tr><th>Sensor</th><th class="num">Raw</th><th class="num">Compensated</th><th class="num">Change</th></tr></thead>
            <tbody>
            <tr><th scope="row">MQ-2 (gas)</th><td class="num">${gg:num(event.mq2Raw)}</td>
                <td class="num strong">${gg:num(event.compensatedScore)}</td><td class="num">${gg:signed(event.compensationDelta)}</td></tr>
            <tr><th scope="row">MQ-135 (air quality)</th><td class="num">${gg:num(event.mq135Raw)}</td>
                <td class="num strong">${gg:num(event.mq135Compensated)}</td>
                <td class="num"><c:if test="${not empty event.mq135Raw and not empty event.mq135Compensated}">${gg:signed(event.mq135Compensated - event.mq135Raw)}</c:if></td></tr>
            </tbody>
        </table>
        <dl class="facts">
            <div><dt>Temperature</dt><dd>${gg:num(event.tempC)} &deg;C</dd></div>
            <div><dt>Humidity</dt><dd>${gg:num(event.rhPct)} %RH</dd></div>
            <div><dt>Flame sensor</dt><dd><c:choose><c:when test="${empty event.flameDetected}">Not reported</c:when>
                <c:when test="${event.flameDetected}">Flame</c:when><c:otherwise>No flame</c:otherwise></c:choose></dd></div>
            <div><dt>Device</dt><dd><c:out value="${event.deviceCode}"/>, sequence ${event.deviceSeq}</dd></div>
            <div><dt>Measured (UTC)</dt><dd>${gg:time(event.measuredAt)}</dd></div>
            <div><dt>Received (UTC)</dt><dd>${gg:time(event.ingestedAt)}</dd></div>
        </dl>
    </section>

    <section class="panel">
        <h2>Results</h2>
        <dl class="facts">
            <div><dt>Rules</dt><dd><span class="badge badge-${gg:severity(event.ruleLabel)}">${gg:label(event.ruleLabel)}</span></dd></div>
            <div><dt>Model</dt><dd><c:choose><c:when test="${empty event.mlLabel}">No model loaded</c:when>
                <c:otherwise>${gg:label(event.mlLabel)}, ${gg:pct(event.mlConfidence)} sure</c:otherwise></c:choose></dd></div>
            <div><dt>Label given by a person</dt><dd><c:choose><c:when test="${empty event.humanLabel}">None yet</c:when>
                <c:otherwise>${gg:label(event.humanLabel)} <span class="muted">by <c:out value="${event.labeledByName}"/>, ${gg:time(event.labeledAt)}</span></c:otherwise></c:choose></dd></div>
        </dl>
        <c:if test="${me.can('GASEVENT', 'WRITE')}">
            <form method="post" action="${ctx}/GasEventController" class="form label-form">
                <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                <input type="hidden" name="action" value="label">
                <input type="hidden" name="id" value="${event.sessionId}">
                <label class="field">
                    <span>What really happened</span>
                    <select name="label" required>
                        <c:forEach var="l" items="${labels}">
                            <option value="${l}" ${l == (empty event.humanLabel ? event.ruleLabel : event.humanLabel) ? 'selected' : ''}>${gg:label(l)}</option>
                        </c:forEach>
                    </select>
                    <small class="field-hint">Labelled sessions of a real device feed tool.TransferCheck.</small>
                </label>
                <div class="form-actions"><button type="submit" class="btn">Save label</button></div>
            </form>
            <c:if test="${not empty event.humanLabel}">
                <form method="post" action="${ctx}/GasEventController" data-confirm="Remove the label of this session?">
                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                    <input type="hidden" name="action" value="unlabel">
                    <input type="hidden" name="id" value="${event.sessionId}">
                    <button type="submit" class="link link-danger">Remove label</button>
                </form>
            </c:if>
        </c:if>
    </section>
</div>

<section class="panel">
    <h2>Alerts raised by this session</h2>
    <c:choose>
        <c:when test="${empty alerts}"><p class="muted">None. Either the session was safe, or an alert for the same
            problem was already open on this device.</p></c:when>
        <c:otherwise>
            <ul class="plain-list">
                <c:forEach var="a" items="${alerts}">
                    <li><a href="${ctx}/AlertController?action=view&amp;id=${a.alertId}">Alert ${a.alertId}</a>
                        <span class="badge badge-${a.severity}">${gg:label(a.label)}</span>
                        <span class="muted">${a.source}, <c:out value="${a.status}"/></span></li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
</section>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
