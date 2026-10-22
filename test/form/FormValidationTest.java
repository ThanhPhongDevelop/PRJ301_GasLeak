package form;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import model.Calibration;
import model.EmergencyContact;
import model.GasSensor;
import model.Home;
import model.OperatingProfile;
import model.SensorType;
import model.ZoneThreshold;
import org.testng.Assert;
import org.testng.annotations.Test;
import util.SecurityUtils;

/** Server-side checks of the phase 3 forms. */
public class FormValidationTest {

    private static Map<String, String> form(String... keyValues) {
        Map<String, String> form = new HashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            form.put(keyValues[i], keyValues[i + 1]);
        }
        return form;
    }

    @Test
    public void homeNeedsAName() {
        Map<String, String> errors = new HashMap<String, String>();
        Home home = HomeForm.validate(form("id", "", "homeName", "", "address", "", "ownerUserId", ""), errors);
        Assert.assertTrue(errors.containsKey("homeName"));
        Assert.assertEquals(home.getHomeId(), 0, "an empty id means create");
        Assert.assertNull(home.getOwnerUserId());
    }

    @Test
    public void sensorCodeDateAndStatusAreChecked() {
        Map<String, String> errors = new HashMap<String, String>();
        GasSensorForm.validate(form("sensorCode", "MQ2 01!", "deviceId", "", "sensorTypeId", "2",
                "installedAt", "2999-01-01", "status", "BROKEN"), errors);
        Assert.assertEquals(errors.keySet(), new java.util.HashSet<String>(Arrays.asList(
                "sensorCode", "deviceId", "installedAt", "status")));
        errors.clear();
        GasSensor ok = GasSensorForm.validate(form("id", "7", "sensorCode", "MQ2-03", "deviceId", "1",
                "sensorTypeId", "1", "kitchenZoneId", "", "pinLabel", "GPIO34", "installedAt", "2026-10-01",
                "status", "ACTIVE"), errors);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertEquals(ok.getGasSensorId(), 7);
        Assert.assertNull(ok.getKitchenZoneId());
    }

    @Test
    public void alarmLevelMustBeAboveWarningLevel() {
        Map<String, String> errors = new HashMap<String, String>();
        ZoneThresholdForm.validate(form("kitchenZoneId", "1", "sensorTypeId", "1", "warnLevel", "2500",
                "alarmLevel", "2400"), errors);
        Assert.assertTrue(errors.containsKey("alarmLevel"));
        errors.clear();
        ZoneThreshold t = ZoneThresholdForm.validate(form("kitchenZoneId", "1", "sensorTypeId", "1",
                "warnLevel", "2150.5", "alarmLevel", "2350"), errors);
        Assert.assertTrue(errors.isEmpty());
        Assert.assertEquals(t.getWarnLevel(), 2150.5);
    }

    @Test
    public void contactPhoneEmailPriorityAndLabelAreChecked() {
        Map<String, String> errors = new HashMap<String, String>();
        EmergencyContactForm.validate(form("homeId", "1", "fullName", "Lan", "phone", "abc", "email", "lan@",
                "priority", "0", "notifyMinLabel", "SAFE"), errors);
        Assert.assertEquals(errors.keySet(), new java.util.HashSet<String>(Arrays.asList(
                "phone", "email", "priority", "notifyMinLabel")));
        errors.clear();
        EmergencyContact c = EmergencyContactForm.validate(form("homeId", "1", "fullName", "Fire and rescue",
                "phone", "114", "email", "", "relation", "", "priority", "2", "notifyMinLabel", "MAJOR_LEAK"), errors);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertNull(c.getEmail());
    }

    @Test
    public void deviceCodeIsCheckedOnlyWhenRegistering() {
        Map<String, String> errors = new HashMap<String, String>();
        DeviceForm.validate(form("deviceCode", "x", "status", "ACTIVE"), errors, true);
        Assert.assertTrue(errors.containsKey("deviceCode"));
        errors.clear();
        DeviceForm.validate(form("deviceCode", "x", "status", "ACTIVE"), errors, false);
        Assert.assertTrue(errors.isEmpty());
    }

    @Test
    public void emptyPasswordOnANewAccountGeneratesAStrongOne() {
        Map<String, String> errors = new HashMap<String, String>();
        Map<String, String> input = form("username", "lan.nguyen", "fullName", "Lan Nguyen", "roleName", "OPERATOR",
                "password", "");
        UserForm.Account account = UserForm.validate(input, errors, Arrays.asList("ADMIN", "OPERATOR"));
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertTrue(account.isGenerated());
        Assert.assertTrue(SecurityUtils.isStrongPassword(account.getPassword()));
        Assert.assertEquals(input.get("password"), "", "the password is never sent back to the page");
        errors.clear();
        UserForm.validate(form("username", "x", "fullName", "", "roleName", "ROOT", "password", "short"), errors,
                Arrays.asList("ADMIN"));
        Assert.assertEquals(errors.size(), 4);
    }

    @Test
    public void sensorTypeRangeNeedsBothEndsInOrder() {
        Map<String, String> errors = new HashMap<String, String>();
        SensorTypeForm.validate(form("name", "", "minValid", "10", "maxValid", ""), errors);
        Assert.assertEquals(errors.keySet(), new java.util.HashSet<String>(Arrays.asList("name", "maxValid")));
        errors.clear();
        SensorTypeForm.validate(form("name", "CO sensor", "minValid", "100", "maxValid", "100"), errors);
        Assert.assertTrue(errors.containsKey("maxValid"), "the lowest value must be below the highest");
        errors.clear();
        SensorType open = SensorTypeForm.validate(form("name", "CO sensor", "unit", "ppm", "minValid", "",
                "maxValid", ""), errors);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertNull(open.getMinValid(), "no range means no range check");
        Assert.assertNull(open.getMaxValid());
    }

    @Test
    public void calibrationNeedsAPositiveScaleAndARealDate() {
        Map<String, String> errors = new HashMap<String, String>();
        CalibrationForm.validate(form("deviceId", "1", "sensorTypeId", "1", "offsetVal", "abc", "scaleVal", "0",
                "validUntil", "31/12/2026"), errors);
        Assert.assertEquals(errors.keySet(), new java.util.HashSet<String>(Arrays.asList(
                "offsetVal", "scaleVal", "validUntil")));
        errors.clear();
        Calibration c = CalibrationForm.validate(form("deviceId", "1", "sensorTypeId", "2", "offsetVal", "-35.5",
                "scaleVal", "1.04", "validUntil", ""), errors);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertEquals(c.getScaleVal(), 1.04);
        Assert.assertNull(c.getValidUntil(), "an empty date means the calibration does not expire");
    }

    @Test
    public void profileVersionStaysWithItsDevice() {
        Map<String, String> errors = new HashMap<String, String>();
        OperatingProfileForm.validate(form("deviceId", "", "tRef", "90", "rhRef", "50", "mq2BT", "12",
                "mq2BRh", "-3", "mq135BT", "5000", "mq135BRh", "1"), errors, null);
        Assert.assertEquals(errors.keySet(), new java.util.HashSet<String>(Arrays.asList(
                "deviceId", "tRef", "mq135BT")));
        errors.clear();
        OperatingProfile base = new OperatingProfile();
        base.setProfileId(7);
        base.setDeviceId(1);
        OperatingProfileForm.Draft draft = OperatingProfileForm.validate(form("deviceId", "2", "tRef", "26.5",
                "rhRef", "55", "mq2BT", "12", "mq2BRh", "-3", "mq135BT", "4", "mq135BRh", "1.5"), errors, base);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        Assert.assertEquals(draft.getDeviceId(), 1, "a new version keeps the device of the one it came from");
        Assert.assertEquals(draft.getModel().compensateMq2(2000.0, 28.5, 55.0), 2000.0 - 12 * 2, 1e-9);
    }

    @Test
    public void newPasswordMustBeStrongDifferentAndConfirmed() {
        Map<String, String> errors = new HashMap<String, String>();
        Assert.assertNull(PasswordForm.validate(form("currentPassword", "Demo@123", "newPassword", "Demo@123",
                "confirmPassword", "Demo@123"), errors));
        Assert.assertTrue(errors.containsKey("newPassword"));
        errors.clear();
        Assert.assertNull(PasswordForm.validate(form("currentPassword", "Demo@123", "newPassword", "Kitchen2026",
                "confirmPassword", "Kitchen2027"), errors));
        Assert.assertTrue(errors.containsKey("confirmPassword"));
        errors.clear();
        Assert.assertEquals(PasswordForm.validate(form("currentPassword", "Demo@123", "newPassword", "Kitchen2026",
                "confirmPassword", "Kitchen2026"), errors), "Kitchen2026");
    }
}
