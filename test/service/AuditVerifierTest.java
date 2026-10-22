package service;

import org.testng.Assert;
import org.testng.annotations.Test;

/** Expected hashes were computed independently (Python hashlib over UTF-16LE bytes). */
public class AuditVerifierTest {

    private static final String ZERO = AuditVerifier.ZERO_HASH;

    @Test
    public void nullColumnsBecomeEmptyLikeConcatInSqlServer() {
        String row = AuditVerifier.buildRow("7", "2026-10-03T08:00:00.1234567", null, "SEED", "SYSTEM", null, null, ZERO);
        Assert.assertEquals(row, "7|2026-10-03T08:00:00.1234567||SEED|SYSTEM|||" + ZERO);
    }

    @Test
    public void hashEqualsHashbytesSha256OfNvarchar() {
        Assert.assertEquals(AuditVerifier.hashRow("1|x|" + ZERO),
                "22251d16f79569af08a4ea69ae942d33516b6c0ccb55f4894882808098449fc5");
    }

    @Test
    public void vietnameseTextIsHashedAsUtf16() {
        String row = "9|2026-10-03T08:00:00.0000000|2|UPDATE|KITCHENZONE|1|{\"zoneName\":\"Bếp chính\"}|" + ZERO;
        Assert.assertEquals(AuditVerifier.hashRow(row),
                "84413c75bb4bba111fc942cd03c4b2cc3dc3a4e0feffc471b026abc9fb25cbc1");
    }

    @Test
    public void editingOneValueChangesTheHash() {
        String before = AuditVerifier.hashRow("5|t|1|UPDATE|KITCHENZONE|3|{\"thresholdPpm\":300}|" + ZERO);
        String after = AuditVerifier.hashRow("5|t|1|UPDATE|KITCHENZONE|3|{\"thresholdPpm\":900}|" + ZERO);
        Assert.assertNotEquals(before, after);
    }
}
