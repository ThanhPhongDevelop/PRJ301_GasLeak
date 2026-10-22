<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="pageTitle" value="Dashboard" />
<%@ include file="/WEB-INF/jspf/header.jspf" %>
<div class="page-head">
    <h1>Kitchen status</h1>
</div>

<section class="status-strip ${summary.urgentAlerts > 0 ? 'status-alarm' : 'status-calm'}">
    <c:choose>
        <c:when test="${summary.urgentAlerts > 0}">
            <strong>${summary.urgentAlerts} alert${summary.urgentAlerts == 1 ? '' : 's'} waiting for action</strong>
            <span>Check the affected zone, then confirm each alert as handled.
                <c:if test="${me.can('ALERT', 'READ')}"><a href="${ctx}/AlertController?status=UNRESOLVED">Open the alerts</a></c:if></span>
        </c:when>
        <c:otherwise>
            <strong>No leak, flame or sensor fault alerts</strong>
            <span>Every kitchen zone is within its levels<c:if test="${summary.openAlerts > 0}">;
                ${summary.openAlerts} cooking fumes notice${summary.openAlerts == 1 ? '' : 's'} not yet resolved</c:if>.</span>
        </c:otherwise>
    </c:choose>
</section>

<section class="panel live" id="live" data-api="${ctx}/api/dashboard">
    <div class="live-head">
        <h2>Live gas level</h2>
        <label class="live-device">Device
            <select id="liveDevice">
                <c:forEach var="d" items="${devices}">
                    <option value="${d.deviceId}"><c:out value="${d.deviceCode}"/></option>
                </c:forEach>
            </select>
        </label>
        <span id="liveStatus" class="muted" aria-live="polite">Loading readings</span>
    </div>
    <div class="live-body">
        <figure class="gauge-wrap">
            <canvas id="gauge" width="320" height="190" role="img" aria-label="Gas level gauge"></canvas>
            <figcaption>
                <span class="gauge-value" id="gaugeValue">&ndash;</span>
                <span class="gauge-unit">compensated MQ-2</span>
                <span class="badge badge-none" id="gaugeLabel">No reading</span>
            </figcaption>
        </figure>
        <figure class="chart-wrap">
            <canvas id="compareChart" width="640" height="240" role="img"
                    aria-label="MQ-2 before and after temperature and humidity compensation"></canvas>
            <figcaption class="legend">
                <span><i class="key key-raw"></i>Raw MQ-2</span>
                <span><i class="key key-comp"></i>Compensated</span>
                <span><i class="key key-warn"></i>Warning level</span>
                <span><i class="key key-alarm"></i>Alarm level</span>
            </figcaption>
        </figure>
    </div>
    <dl class="readings" id="readings">
        <div><dt>Temperature</dt><dd data-field="temp">&ndash;</dd></div>
        <div><dt>Humidity</dt><dd data-field="rh">&ndash;</dd></div>
        <div><dt>MQ-135 compensated</dt><dd data-field="air">&ndash;</dd></div>
        <div><dt>Model</dt><dd data-field="model">&ndash;</dd></div>
        <div><dt>Last session (UTC)</dt><dd data-field="time">&ndash;</dd></div>
    </dl>
    <p class="muted small" id="compensationText"></p>
</section>

<dl class="figures">
    <div><dt>Kitchen zones</dt><dd>${summary.zoneCount}</dd></div>
    <div><dt>Active devices</dt><dd>${summary.activeDevices}</dd></div>
    <div><dt>Devices online in the last 5 minutes</dt><dd>${summary.onlineDevices}</dd></div>
    <div><dt>Sessions received today (UTC)</dt><dd>${summary.sessionsToday}</dd></div>
</dl>

<details class="panel">
    <summary>What your role can do</summary>
    <p class="muted">Signed in as <c:out value="${me.username}"/>, role <c:out value="${me.roleDisplayName}"/>.
        Read lets you open a page; write lets you change it.</p>
    <ul class="chips">
        <c:forEach var="permission" items="${me.permissionList}">
            <li class="chip"><c:out value="${permission}"/></li>
        </c:forEach>
    </ul>
</details>
<script src="${ctx}/js/dashboard.js"></script>
<%@ include file="/WEB-INF/jspf/footer.jspf" %>
