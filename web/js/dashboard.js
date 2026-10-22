// Live dashboard: polls /api/dashboard every 5 seconds and draws, with the plain canvas API
// (no library, exam machines may be offline), the gas gauge and the before/after compensation chart.
(function () {
    'use strict';

    var root = document.getElementById('live');
    if (!root) {
        return;
    }
    var api = root.getAttribute('data-api');
    var select = document.getElementById('liveDevice');
    var statusEl = document.getElementById('liveStatus');
    var gauge = document.getElementById('gauge');
    var chart = document.getElementById('compareChart');
    var valueEl = document.getElementById('gaugeValue');
    var labelEl = document.getElementById('gaugeLabel');
    var compensationEl = document.getElementById('compensationText');
    var ADC_MAX = 4095;
    var POLL_MS = 5000;
    var timer = null;
    var lastData = null;
    var stopped = false;

    function cssVar(name, fallback) {
        var value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || fallback;
    }

    function fmt(value, digits) {
        return value === null || value === undefined ? 'n/a' : Number(value).toFixed(digits === undefined ? 1 : digits);
    }

    function field(name) {
        return root.querySelector('[data-field="' + name + '"]');
    }

    function severityOf(label, severity) {
        if (!label) {
            return 'none';
        }
        return label === 'SAFE' ? 'ok' : severity;
    }

    // drawing buffer = displayed size x device pixel ratio, drawing coordinates stay in CSS pixels
    function prepare(canvas) {
        var ratio = window.devicePixelRatio || 1;
        var width = canvas.clientWidth;
        var height = canvas.clientHeight;
        if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
            canvas.width = Math.round(width * ratio);
            canvas.height = Math.round(height * ratio);
        }
        var ctx = canvas.getContext('2d');
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        ctx.clearRect(0, 0, width, height);
        return {ctx: ctx, width: width, height: height};
    }

    function drawGauge(value, levels) {
        var g = prepare(gauge);
        var ctx = g.ctx;
        var cx = g.width / 2;
        var cy = g.height * 0.86;
        // leave room above the arc for the level labels (thickness is 16% of the radius)
        var radius = Math.max(20, Math.min(g.width / 2 - 30, (cy - 30) / 1.08));
        var thickness = Math.max(10, radius * 0.16);
        var angleOf = function (v) {
            var clamped = Math.max(0, Math.min(ADC_MAX, v));
            return Math.PI + clamped / ADC_MAX * Math.PI;
        };
        var bands = [
            [0, levels ? levels.mq2Warn : ADC_MAX, cssVar('--ok', '#2f7d4f')],
            [levels ? levels.mq2Warn : ADC_MAX, levels ? levels.mq2Alarm : ADC_MAX, cssVar('--signal', '#f5b700')],
            [levels ? levels.mq2Alarm : ADC_MAX, ADC_MAX, cssVar('--danger', '#b3261e')]
        ];
        ctx.globalAlpha = value === null ? 0.35 : 1;
        ctx.lineWidth = thickness;
        ctx.lineCap = 'butt';
        bands.forEach(function (band) {
            if (band[1] <= band[0]) {
                return;
            }
            ctx.strokeStyle = band[2];
            ctx.beginPath();
            ctx.arc(cx, cy, radius, angleOf(band[0]), angleOf(band[1]), false);
            ctx.stroke();
        });
        ctx.globalAlpha = 1;

        ctx.fillStyle = cssVar('--steel', '#4a6274');
        ctx.font = '12px ' + cssVar('--body', 'sans-serif');
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillText('0', cx - radius, cy + 6);
        ctx.fillText(String(ADC_MAX), cx + radius, cy + 6);
        if (levels) {
            // warning and alarm labels outside the arc, pushed apart when the levels are close
            var spots = [levels.mq2Warn, levels.mq2Alarm].map(function (level) {
                var a = angleOf(level);
                return {text: String(Math.round(level)), a: a,
                    x: cx + Math.cos(a) * (radius + thickness / 2 + 12),
                    y: cy + Math.sin(a) * (radius + thickness / 2 + 12)};
            });
            var gap = 40 - Math.abs(spots[1].x - spots[0].x);
            if (gap > 0) {
                spots[0].x -= gap / 2;
                spots[1].x += gap / 2;
            }
            ctx.textBaseline = 'middle';
            ctx.strokeStyle = cssVar('--steel', '#4a6274');
            ctx.lineWidth = 1;
            spots.forEach(function (s) {
                ctx.beginPath();
                ctx.moveTo(cx + Math.cos(s.a) * (radius + thickness / 2 + 1), cy + Math.sin(s.a) * (radius + thickness / 2 + 1));
                ctx.lineTo(cx + Math.cos(s.a) * (radius + thickness / 2 + 5), cy + Math.sin(s.a) * (radius + thickness / 2 + 5));
                ctx.stroke();
                ctx.fillText(s.text, s.x, s.y);
            });
        }

        if (value !== null) {
            var angle = angleOf(value);
            ctx.strokeStyle = cssVar('--ink', '#1e2a33');
            ctx.lineWidth = 3;
            ctx.lineCap = 'round';
            ctx.beginPath();
            ctx.moveTo(cx, cy);
            ctx.lineTo(cx + Math.cos(angle) * (radius - thickness), cy + Math.sin(angle) * (radius - thickness));
            ctx.stroke();
            ctx.fillStyle = cssVar('--ink', '#1e2a33');
            ctx.beginPath();
            ctx.arc(cx, cy, 6, 0, Math.PI * 2);
            ctx.fill();
        }
    }

    function drawChart(series, levels) {
        var g = prepare(chart);
        var ctx = g.ctx;
        var pad = {left: 50, right: 12, top: 10, bottom: 24};
        var plotW = g.width - pad.left - pad.right;
        var plotH = g.height - pad.top - pad.bottom;
        var muted = cssVar('--steel', '#4a6274');
        ctx.font = '11px ' + cssVar('--body', 'sans-serif');

        var values = [];
        series.forEach(function (p) {
            if (p.raw !== null) {
                values.push(p.raw);
            }
            if (p.comp !== null) {
                values.push(p.comp);
            }
        });
        if (values.length === 0) {
            ctx.fillStyle = muted;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('No sessions from this device yet', g.width / 2, g.height / 2);
            return;
        }
        if (levels) {
            values.push(levels.mq2Warn, levels.mq2Alarm);
        }
        var min = Math.min.apply(null, values);
        var max = Math.max.apply(null, values);
        var span = (max - min) || 1;
        min -= span * 0.06;
        max += span * 0.06;
        var x = function (i) {
            return pad.left + (series.length < 2 ? plotW / 2 : i * plotW / (series.length - 1));
        };
        var y = function (v) {
            return pad.top + (max - v) / (max - min) * plotH;
        };

        // grid and axis labels
        ctx.strokeStyle = cssVar('--line', '#d5dbe0');
        ctx.lineWidth = 1;
        ctx.fillStyle = muted;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        for (var t = 0; t <= 4; t++) {
            var v = min + (max - min) * t / 4;
            ctx.beginPath();
            ctx.moveTo(pad.left, y(v));
            ctx.lineTo(pad.left + plotW, y(v));
            ctx.stroke();
            ctx.fillText(Math.round(v), pad.left - 6, y(v));
        }
        ctx.textBaseline = 'top';
        ctx.textAlign = 'left';
        ctx.fillText((series[0].t || '').slice(11), pad.left, pad.top + plotH + 6);
        ctx.textAlign = 'right';
        ctx.fillText((series[series.length - 1].t || '').slice(11), pad.left + plotW, pad.top + plotH + 6);

        function level(value, color) {
            ctx.save();
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.5;
            ctx.setLineDash([6, 4]);
            ctx.beginPath();
            ctx.moveTo(pad.left, y(value));
            ctx.lineTo(pad.left + plotW, y(value));
            ctx.stroke();
            ctx.restore();
        }
        if (levels) {
            level(levels.mq2Warn, cssVar('--signal', '#f5b700'));
            level(levels.mq2Alarm, cssVar('--danger', '#b3261e'));
        }

        function line(key, color, width) {
            ctx.strokeStyle = color;
            ctx.lineWidth = width;
            ctx.lineJoin = 'round';
            ctx.beginPath();
            var drawing = false;
            series.forEach(function (p, i) {
                if (p[key] === null) {
                    drawing = false; // a missing reading breaks the line
                    return;
                }
                if (drawing) {
                    ctx.lineTo(x(i), y(p[key]));
                } else {
                    ctx.moveTo(x(i), y(p[key]));
                    drawing = true;
                }
            });
            ctx.stroke();
        }
        line('raw', cssVar('--raw', '#9aa9b4'), 1.5);
        line('comp', cssVar('--ink', '#1e2a33'), 2.5);

        // mark sessions the rules did not consider safe
        series.forEach(function (p, i) {
            if (!p.label || p.label === 'SAFE' || p.comp === null) {
                return;
            }
            var critical = p.label === 'MAJOR_LEAK' || p.label === 'FLAME_DETECTED';
            ctx.fillStyle = critical ? cssVar('--danger', '#b3261e')
                : p.label === 'COOKING_FUMES' ? cssVar('--steel', '#4a6274') : cssVar('--signal', '#f5b700');
            ctx.beginPath();
            ctx.arc(x(i), y(p.comp), 3.5, 0, Math.PI * 2);
            ctx.fill();
        });
    }

    function render(data) {
        lastData = data;
        var latest = data.latest;
        var levels = data.levels || null;
        var value = latest ? (latest.mq2Compensated !== null ? latest.mq2Compensated : latest.mq2Raw) : null;
        drawGauge(value, levels);
        drawChart(data.series || [], levels);

        valueEl.textContent = value === null ? '–' : fmt(value);
        var severity = latest ? severityOf(latest.ruleLabel, latest.severity) : 'none';
        labelEl.className = 'badge badge-' + severity;
        labelEl.textContent = latest ? latest.ruleLabelText : 'No reading';
        gauge.setAttribute('aria-label', latest ? 'Compensated MQ-2 ' + fmt(value) + ', ' + latest.ruleLabelText
            : 'No reading yet');

        field('temp').textContent = latest ? fmt(latest.tempC) + ' °C' : '–';
        field('rh').textContent = latest ? fmt(latest.rhPct) + ' %RH' : '–';
        field('air').textContent = latest ? fmt(latest.mq135Compensated) : '–';
        field('model').textContent = latest && latest.mlLabel
            ? latest.mlLabel.replace(/_/g, ' ').toLowerCase() + ' (' + fmt(latest.mlConfidence * 100, 0) + '%)'
            : (data.model && data.model.name ? data.model.name + ' loaded' : 'No model loaded');
        field('time').textContent = latest ? latest.measuredAt : '–';
        compensationEl.textContent = data.compensation ? 'Compensation: ' + data.compensation.text.replace(/\.$/, '')
            + '.' + (levels ? ' Levels from ' + levels.source + '.' : '') : '';

        var device = data.device;
        statusEl.textContent = device
            ? 'Updated at ' + new Date().toLocaleTimeString() + (device.online ? ' (device online)' : ' (device offline)')
            : 'No device registered yet';
    }

    function schedule() {
        if (!stopped) {
            clearTimeout(timer);
            timer = setTimeout(refresh, POLL_MS);
        }
    }

    function refresh() {
        var url = api + (select && select.value ? '?device=' + encodeURIComponent(select.value) : '');
        fetch(url, {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
            .then(function (response) {
                if (response.status === 401 || response.status === 403) {
                    stopped = true;
                    throw new Error(response.status === 401 ? 'Signed out: reload the page to sign in again.'
                        : 'Your role cannot read live sessions.');
                }
                if (!response.ok) {
                    throw new Error('The server could not read the sessions (' + response.status + '); retrying.');
                }
                return response.json();
            })
            .then(render)
            .catch(function (error) {
                statusEl.textContent = error.message || 'Connection lost; retrying.';
            })
            .then(schedule);
    }

    if (select) {
        select.addEventListener('change', function () {
            clearTimeout(timer);
            refresh();
        });
    }
    var resizeTimer = null;
    window.addEventListener('resize', function () {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function () {
            if (lastData) {
                render(lastData);
            }
        }, 150);
    });
    refresh();
}());
