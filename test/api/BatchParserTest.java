package api;

import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BatchParserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 6, 0);

    private static BatchParser.Batch parse(String json) {
        return BatchParser.parse(JsonParser.parseString(json), NOW);
    }

    @Test
    public void readsTheWorkshopFormat() {
        BatchParser.Batch batch = parse("{\"device_id\":1,\"sessions\":[{\"seq\":7,\"measured_at\":\"2026-10-08 05:30:00\","
                + "\"mq2_raw\":1830.5,\"mq135_raw\":1650.2,\"temp_c\":27.4,\"rh_pct\":61.0,\"compensated_score\":1.0}]}");
        Assert.assertEquals(batch.getClaimedDeviceId(), Integer.valueOf(1));
        Assert.assertEquals(batch.getSessions().size(), 1);
        BatchParser.SessionInput s = batch.getSessions().get(0);
        Assert.assertEquals(s.getSeq(), 7);
        Assert.assertEquals(s.getMeasuredAt(), LocalDateTime.of(2026, 10, 8, 5, 30));
        Assert.assertEquals(s.getMq2Raw(), 1830.5);
        Assert.assertNull(s.getFlame());
    }

    @Test
    public void isoTimesWithOffsetAreConvertedToUtc() {
        Assert.assertEquals(BatchParser.parseTime("2026-10-08T12:30:00+07:00"), LocalDateTime.of(2026, 10, 8, 5, 30));
        Assert.assertEquals(BatchParser.parseTime("2026-10-08T05:30:00Z"), LocalDateTime.of(2026, 10, 8, 5, 30));
    }

    @Test
    public void missingSensorValuesAreKeptAsNullSoTheRulesSeeTheFault() {
        BatchParser.Batch batch = parse("{\"sessions\":[{\"seq\":1,\"measured_at\":\"2026-10-08 05:30:00\","
                + "\"mq2_raw\":1800,\"temp_c\":null,\"flame\":1}]}");
        BatchParser.SessionInput s = batch.getSessions().get(0);
        Assert.assertNull(s.getTempC());
        Assert.assertNull(s.getRhPct());
        Assert.assertEquals(s.getFlame(), Boolean.TRUE);
    }

    @Test
    public void badSessionsAreRejectedOneByOne() {
        BatchParser.Batch batch = parse("{\"sessions\":["
                + "{\"seq\":1,\"measured_at\":\"2026-10-08 05:30:00\",\"mq2_raw\":1800},"
                + "{\"seq\":2},"
                + "{\"seq\":3,\"measured_at\":\"1970-01-01 00:00:00\"},"
                + "{\"seq\":4,\"measured_at\":\"2026-10-08 05:31:00\",\"mq2_raw\":\"high\"},"
                + "{\"seq\":1,\"measured_at\":\"2026-10-08 05:32:00\"},"
                + "{\"seq\":2.5,\"measured_at\":\"2026-10-08 05:33:00\"}]}");
        Assert.assertEquals(batch.getSessions().size(), 1);
        Assert.assertEquals(batch.getRejected().size(), 5);
        Assert.assertEquals(batch.getRejected().get(0).getIndex(), 1);
        Assert.assertTrue(batch.getRejected().get(1).getReason().contains("device clock"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void bodyWithoutSessionsIsRefused() {
        parse("{\"device_id\":1}");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void tooManySessionsAreRefused() {
        StringBuilder sb = new StringBuilder("{\"sessions\":[");
        for (int i = 1; i <= BatchParser.MAX_SESSIONS + 1; i++) {
            sb.append(i > 1 ? "," : "").append("{\"seq\":").append(i).append("}");
        }
        parse(sb.append("]}").toString());
    }
}
