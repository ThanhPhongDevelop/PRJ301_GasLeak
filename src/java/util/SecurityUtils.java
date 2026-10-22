package util;

import dao.DeviceDao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

/** Password hashing, device keys and CSRF tokens. */
public final class SecurityUtils {

    private static final int BCRYPT_COST = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurityUtils() {
    }

    public static String hashPassword(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(BCRYPT_COST));
    }

    /** True when the password matches; false for a wrong password or a malformed hash. */
    public static boolean checkPassword(String plain, String hash) {
        if (plain == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (RuntimeException malformedHash) {
            return false;
        }
    }

    /** Password policy: 8 to 72 characters with at least one letter and one digit. */
    public static boolean isStrongPassword(String plain) {
        if (plain == null || plain.length() < 8 || plain.length() > 72) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char ch : plain.toCharArray()) {
            hasLetter |= Character.isLetter(ch);
            hasDigit |= Character.isDigit(ch);
        }
        return hasLetter && hasDigit;
    }

    /** SHA-256 of the UTF-8 bytes in lower-case hex. Device API keys are stored this way. */
    public static String sha256Hex(String text) {
        return toHex(sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Java 8 has no HexFormat, so convert by hand. */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** New device API key: "gg_" + 40 random hex characters (160 bits). Only its SHA-256 is stored. */
    public static String newApiKey() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return "gg_" + toHex(bytes);
    }

    // no 0/O, 1/l/I: the temporary password is read out or copied by hand
    private static final String PASSWORD_LETTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String PASSWORD_DIGITS = "23456789";

    /** Temporary password for an administrator reset: 12 characters with letters and digits. */
    public static String newTemporaryPassword() {
        String all = PASSWORD_LETTERS + PASSWORD_DIGITS;
        char[] chars = new char[12];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = all.charAt(RANDOM.nextInt(all.length()));
        }
        // guarantee the policy: at least one letter and one digit, at random positions
        int letterAt = RANDOM.nextInt(chars.length);
        int digitAt = (letterAt + 1 + RANDOM.nextInt(chars.length - 1)) % chars.length;
        chars[letterAt] = PASSWORD_LETTERS.charAt(RANDOM.nextInt(PASSWORD_LETTERS.length()));
        chars[digitAt] = PASSWORD_DIGITS.charAt(RANDOM.nextInt(PASSWORD_DIGITS.length()));
        return new String(chars);
    }

    /** Random value for the CSRF token stored in the session at login. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /** Compares secrets in constant time. */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Device that owns this API key, or -1 when the key is unknown or the device is not active. */
    public static int findDeviceIdByApiKey(String apiKey) throws SQLException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.length() > 200) {
            return -1;
        }
        return new DeviceDao().findActiveIdByKeyHash(sha256Hex(apiKey));
    }

    /** Kept so the workshop sample code still compiles. */
    public static boolean isValidDeviceKey(String apiKey) throws SQLException {
        return findDeviceIdByApiKey(apiKey) > 0;
    }
}
