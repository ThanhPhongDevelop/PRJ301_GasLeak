package util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SecurityUtilsTest {

    private static final String SEED_HASH = "$2a$10$ifggxOSMrIYAn1ipqEYfuuDKOpPPE.Q/DAbK4dt1Nv/nbgQYIm/HK";

    @Test
    public void sha256MatchesTheStandardTestVector() {
        Assert.assertEquals(SecurityUtils.sha256Hex("abc"),
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    public void deviceKeyHashMatchesTheSeedScript() {
        Assert.assertEquals(SecurityUtils.sha256Hex("device-1-api-key"),
                "73fb4e98f6db0f8a286c82e03e54764c4d8cd556fcfd858c0f6edf61cdaec849");
    }

    @Test
    public void demoPasswordMatchesTheSeedHash() {
        Assert.assertTrue(SecurityUtils.checkPassword("Demo@123", SEED_HASH));
        Assert.assertFalse(SecurityUtils.checkPassword("demo@123", SEED_HASH));
    }

    @Test
    public void newHashesUseTheFormatJbcryptAccepts() {
        String hash = SecurityUtils.hashPassword("Kitchen2026");
        Assert.assertTrue(hash.startsWith("$2a$10$"));
        Assert.assertTrue(SecurityUtils.checkPassword("Kitchen2026", hash));
    }

    @Test
    public void malformedHashIsRejectedWithoutAnException() {
        Assert.assertFalse(SecurityUtils.checkPassword("anything", "not-a-bcrypt-hash"));
        Assert.assertFalse(SecurityUtils.checkPassword("anything", "$2a$"));
    }

    @Test
    public void deviceKeysAreLongRandomAndStoredAsSha256() {
        String a = SecurityUtils.newApiKey();
        String b = SecurityUtils.newApiKey();
        Assert.assertTrue(a.matches("gg_[0-9a-f]{40}"), a);
        Assert.assertNotEquals(a, b);
        Assert.assertEquals(SecurityUtils.sha256Hex(a).length(), 64);
    }

    @Test
    public void temporaryPasswordsMeetThePolicy() {
        for (int i = 0; i < 200; i++) {
            String password = SecurityUtils.newTemporaryPassword();
            Assert.assertEquals(password.length(), 12);
            Assert.assertTrue(SecurityUtils.isStrongPassword(password), password);
            Assert.assertFalse(password.matches(".*[0O1lI].*"), "ambiguous character in " + password);
        }
    }

    @Test
    public void passwordPolicyNeedsLengthLetterAndDigit() {
        Assert.assertTrue(SecurityUtils.isStrongPassword("Demo@123"));
        Assert.assertFalse(SecurityUtils.isStrongPassword("abc123"));
        Assert.assertFalse(SecurityUtils.isStrongPassword("onlyletters"));
        Assert.assertFalse(SecurityUtils.isStrongPassword("12345678"));
    }
}
