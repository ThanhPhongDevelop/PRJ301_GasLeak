<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="gg" uri="/WEB-INF/gasguard.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="pageTitle" value="Device control" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<c:set var="canWrite" value="${me.can('CONTROL', 'WRITE')}" />
<div class="page-head"><h1>Device control</h1></div>
<p class="lead">One person controls a device at a time. Sending a command takes control of a free device; release it
    when you are done so others can act. Commands reach the device at its next connection.</p>

<c:forEach var="d" items="${devices}">
    <c:set var="mine" value="${d.controlOwner == me.userId}" />
    <section class="panel device">
        <div class="device-head">
            <h2><c:out value="${d.deviceCode}"/></h2>
            <span class="state ${d.online ? 'state-ok' : 'state-stop'}">${d.online ? 'Online' : 'Offline'}</span>
            <span class="muted"><c:out value="${empty d.zoneNames ? d.location : d.zoneNames}"/>,
                <c:choose><c:when test="${empty d.lastSeen}">never seen</c:when>
                    <c:otherwise>last seen ${gg:time(d.lastSeen)} UTC</c:otherwise></c:choose></span>
        </div>
        <p class="lock">
            <c:choose>
                <c:when test="${empty d.controlOwner}">Nobody controls this device.</c:when>
                <c:when test="${mine}"><strong>You control this device.</strong></c:when>
                <c:otherwise>Controlled by <strong><c:out value="${d.controlOwnerName}"/></strong>.</c:otherwise>
            </c:choose>
        </p>
        <c:if test="${canWrite}">
            <div class="device-actions">
                <c:forEach var="cmd" items="${commandTypes}">
                    <form method="post" action="${ctx}/ControlController"
                          data-confirm="${fn:escapeXml(d.deviceCode)}: ${fn:escapeXml(cmd.confirmText)}">
                        <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                        <input type="hidden" name="action" value="command">
                        <input type="hidden" name="deviceId" value="${d.deviceId}">
                        <input type="hidden" name="command" value="${cmd.code}">
                        <button type="submit" class="btn btn-small ${cmd.risky ? 'btn-danger' : 'btn-quiet'}"
                            ${not empty d.controlOwner and not mine ? 'disabled' : ''}>${cmd.name}</button>
                    </form>
                </c:forEach>
                <c:choose>
                    <c:when test="${mine}">
                        <form method="post" action="${ctx}/ControlController">
                            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                            <input type="hidden" name="action" value="release">
                            <input type="hidden" name="deviceId" value="${d.deviceId}">
                            <button type="submit" class="btn btn-small">Release control</button>
                        </form>
                    </c:when>
                    <c:when test="${empty d.controlOwner}">
                        <form method="post" action="${ctx}/ControlController">
                            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                            <input type="hidden" name="action" value="acquire">
                            <input type="hidden" name="deviceId" value="${d.deviceId}">
                            <button type="submit" class="btn btn-small">Take control</button>
                        </form>
                    </c:when>
                    <c:when test="${me.roleName == 'ADMIN'}">
                        <form method="post" action="${ctx}/ControlController"
                              data-confirm="Remove the control lock of ${fn:escapeXml(d.controlOwnerName)}?">
                            <input type="hidden" name="csrfToken" value="${sessionScope.CSRF_TOKEN}">
                            <input type="hidden" name="action" value="forceRelease">
                            <input type="hidden" name="deviceId" value="${d.deviceId}">
                            <button type="submit" class="btn btn-small btn-danger">Remove lock</button>
                        </form>
                    </c:when>
                </c:choose>
            </div>
        </c:if>
    </section>
</c:forEach>
<c:if test="${empty devices}"><div class="empty"><p>No device registered yet.</p></div></c:if>

<section class="panel">
    <h2>Recent commands</h2>
    <c:choose>
        <c:when test="${empty recentCommands}"><p class="muted">No command sent yet.</p></c:when>
        <c:otherwise>
            <div class="table-wrap">
                <table class="grid grid-dense">
                    <thead><tr><th class="num">#</th><th>Command</th><th>Device</th><th>By</th><th>Issued (UTC)</th><th>Delivered</th></tr></thead>
                    <tbody>
                    <c:forEach var="cmd" items="${recentCommands}">
                        <tr><td class="num">${cmd.commandId}</td><td>${cmd.commandDisplay}</td><td><c:out value="${cmd.deviceCode}"/></td>
                            <td><c:out value="${cmd.issuedByName}"/></td><td class="nowrap">${gg:time(cmd.issuedAt)}</td>
                            <td><c:choose><c:when test="${cmd.status == 'SENT'}">${gg:time(cmd.deliveredAt)}</c:when>
                                <c:otherwise><span class="muted">Waiting for the device</span></c:otherwise></c:choose></td></tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
