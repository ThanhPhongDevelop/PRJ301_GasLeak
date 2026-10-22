<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Alert ${alert.alertId}" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1>Alert ${alert.alertId}: ${gg:label(alert.label)}</h1>
    <a class="btn btn-quiet" href="${ctx}/AlertController?status=UNRESOLVED">Back to alerts</a>
</div>

<section class="status-strip status-${alert.resolved ? 'calm' : alert.severity}">
    <strong>
        <c:choose>
            <c:when test="${alert.resolved}">Handled</c:when>
            <c:when test="${alert.status == 'ACKNOWLEDGED'}">Someone is on it</c:when>
            <c:otherwise>Waiting for someone to respond</c:otherwise>
        </c:choose>
    </strong>
    <span>Raised ${gg:time(alert.createdAt)} UTC on <c:out value="${alert.deviceCode}"/> by the
        ${alert.source == 'ML' ? 'model' : 'rules'}<c:if test="${not empty alert.riskScore}">, leak risk ${gg:pct(alert.riskScore)}</c:if>.</span>
</section>

<div class="detail-grid">
    <section class="panel">
        <h2>Evidence</h2>
        <table class="grid grid-dense evidence">
            <tbody>
            <c:forEach var="e" items="${evidence}">
                <tr><th scope="row"><c:out value="${e.item}"/></th><td><c:out value="${e.value}"/></td></tr>
            </c:forEach>
            </tbody>
        </table>
        <c:if test="${not empty event}">
            <h3>Session that raised it</h3>
            <p><a href="${ctx}/GasEventController?action=view&amp;id=${event.sessionId}">Session ${event.sessionId}</a>:
                MQ-2 ${gg:num(event.mq2Raw)} raw, ${gg:num(event.compensatedScore)} compensated,
                ${gg:num(event.tempC)} &deg;C, ${gg:num(event.rhPct)} %RH.</p>
        </c:if>
        <c:if test="${not empty episode}">
            <h3>Later sessions with the same result</h3>
            <ul class="plain-list">
                <c:forEach var="s" items="${episode}">
                    <li><a href="${ctx}/GasEventController?action=view&amp;id=${s.sessionId}">Session ${s.sessionId}</a>
                        <span class="muted">${gg:time(s.measuredAt)}, MQ-2 ${gg:num(s.compensatedScore)}</span></li>
                </c:forEach>
            </ul>
        </c:if>
    </section>

    <section class="panel actions">
        <h2>Respond</h2>
        <c:if test="${me.can('CONTROL', 'WRITE') and not empty valve and not alert.resolved}">
            <form method="post" action="${ctx}/ControlController" data-confirm="Close the gas valve on ${fn:escapeXml(valve.deviceCode)}? The stove will go out.">
                <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                <input type="hidden" name="action" value="command">
                <input type="hidden" name="command" value="CLOSE_VALVE">
                <input type="hidden" name="deviceId" value="${valve.deviceId}">
                <input type="hidden" name="returnAlert" value="${alert.alertId}">
                <button type="submit" class="btn btn-danger btn-wide">Close gas valve</button>
            </form>
            <p class="field-hint">Valve on <c:out value="${valve.deviceCode}"/><c:if test="${not empty valve.controlOwnerName}">,
                controlled by <c:out value="${valve.controlOwnerName}"/></c:if>.</p>
        </c:if>
        <c:if test="${me.can('ALERT', 'WRITE')}">
            <c:if test="${alert.status == 'OPEN'}">
                <form method="post" action="${ctx}/AlertController">
                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                    <input type="hidden" name="action" value="acknowledge">
                    <input type="hidden" name="id" value="${alert.alertId}">
                    <button type="submit" class="btn btn-quiet btn-wide">I am on it</button>
                </form>
            </c:if>
            <c:if test="${not alert.resolved}">
                <form method="post" action="${ctx}/AlertController" class="form"
                      data-confirm="Confirm that the incident has been handled and the kitchen is safe?">
                    <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                    <input type="hidden" name="action" value="resolve">
                    <input type="hidden" name="id" value="${alert.alertId}">
                    <label class="field">
                        <span>What was done</span>
                        <textarea name="note" rows="3" maxlength="400" required
                                  placeholder="Closed the valve, aired the kitchen, readings back to normal"></textarea>
                    </label>
                    <button type="submit" class="btn btn-wide">Mark incident as handled</button>
                </form>
            </c:if>
        </c:if>
        <c:if test="${alert.resolved}"><p class="muted">This alert is resolved. The audit log keeps who did it and the note.</p></c:if>
        <c:if test="${not alert.resolved and not me.can('ALERT', 'WRITE')}">
            <p class="muted">Your role can follow this alert but not respond to it. The homeowner or a technician
                acknowledges it and confirms when it is handled.</p>
        </c:if>
    </section>
</div>

<c:if test="${me.can('MAINTENANCE', 'READ')}">
<section class="panel">
    <h2>Maintenance tickets</h2>
    <c:choose>
        <c:when test="${empty tickets}"><p class="muted">No ticket for this alert.</p></c:when>
        <c:otherwise>
            <ul class="plain-list">
                <c:forEach var="t" items="${tickets}">
                    <li>Ticket ${t.ticketId} <span class="badge badge-${t.status == 'OPEN' ? 'warning' : 'ok'}">${gg:label(t.status)}</span>
                        <span class="muted">assigned to <c:out value="${empty t.assigneeName ? 'nobody' : t.assigneeName}"/>,
                        opened ${gg:time(t.openedAt)}</span><br><c:out value="${t.note}"/></li>
                </c:forEach>
            </ul>
        </c:otherwise>
    </c:choose>
    <c:if test="${me.can('MAINTENANCE', 'WRITE')}">
        <form method="post" action="${ctx}/MaintenanceController" class="form form-narrow">
            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
            <input type="hidden" name="action" value="open">
            <input type="hidden" name="alertId" value="${alert.alertId}">
            <label class="field">
                <span>Assign to</span>
                <select name="assignedTo">
                    <option value="">Nobody yet</option>
                    <c:forEach var="t" items="${technicians}">
                        <option value="${t.value}" ${t.value == me.userId ? 'selected' : ''}><c:out value="${t.label}"/></option>
                    </c:forEach>
                </select>
            </label>
            <label class="field">
                <span>What to check</span>
                <textarea name="note" rows="2" maxlength="400" required placeholder="Check the MQ-2 sensor and the valve seal"></textarea>
            </label>
            <div class="form-actions"><button type="submit" class="btn btn-quiet">Open maintenance ticket</button></div>
        </form>
    </c:if>
</section>
</c:if>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
