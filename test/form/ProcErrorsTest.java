package form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** The error-to-field map stays in step with the THROW statements of the save procedures. */
public class ProcErrorsTest {

    /** Save procedures and the form whose fields their errors refer to. */
    private static final Map<String, String[]> FORM_OF_PROCEDURE = new HashMap<String, String[]>();
    /** Errors that are not about one field: shown as a notice. */
    private static final Set<Integer> NOTICES = new HashSet<Integer>(Arrays.asList(50050, 50071, 50078, 50084, 50085,
            50088, 50094, 50101, 50102));

    static {
        FORM_OF_PROCEDURE.put("ZoneThreshold_Upsert", ZoneThresholdForm.FIELDS);
        FORM_OF_PROCEDURE.put("Home_Save", HomeForm.FIELDS);
        FORM_OF_PROCEDURE.put("GasSensor_Save", GasSensorForm.FIELDS);
        FORM_OF_PROCEDURE.put("EmergencyContact_Save", EmergencyContactForm.FIELDS);
        FORM_OF_PROCEDURE.put("Device_Register", DeviceForm.FIELDS);
        FORM_OF_PROCEDURE.put("Device_Update", DeviceForm.FIELDS);
        FORM_OF_PROCEDURE.put("SensorType_Save", SensorTypeForm.FIELDS);
        FORM_OF_PROCEDURE.put("Calibration_Save", CalibrationForm.FIELDS);
        FORM_OF_PROCEDURE.put("OperatingProfile_Save", OperatingProfileForm.FIELDS);
    }

    @Test
    public void errorCodeSelectsTheField() {
        Assert.assertEquals(ProcErrors.fieldOf(new SQLException("Another sensor already uses this code.", "S0001", 50077)),
                "sensorCode");
        Assert.assertNull(ProcErrors.fieldOf(new SQLException("This sensor no longer exists.", "S0001", 50078)));
        Assert.assertNull(ProcErrors.fieldOf(new SQLException("deadlock", "40001", 1205)));
    }

    @Test
    public void everySaveErrorHasAFieldOrIsANotice() throws IOException {
        Path script = Paths.get(System.getProperty("project.dir", "."), "sql", "02_programmability.sql");
        if (!Files.isRegularFile(script)) {
            throw new SkipException("Run from the project folder to read the SQL scripts");
        }
        Pattern procedure = Pattern.compile("CREATE OR ALTER PROCEDURE (\\w+)");
        Pattern thrown = Pattern.compile("THROW (5\\d{4})");
        List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
        Set<Integer> seen = new HashSet<Integer>();
        String current = null;
        for (String line : lines) {
            Matcher p = procedure.matcher(line);
            if (p.find()) {
                current = p.group(1);
            }
            Matcher t = thrown.matcher(line);
            if (current == null || !FORM_OF_PROCEDURE.containsKey(current) || !t.find()) {
                continue;
            }
            int code = Integer.parseInt(t.group(1));
            seen.add(code);
            String field = ProcErrors.fieldsByError().get(code);
            if (field == null) {
                Assert.assertTrue(NOTICES.contains(code), current + " throws " + code
                        + ": map it to a field in ProcErrors or list it as a notice");
            } else {
                Assert.assertTrue(Arrays.asList(FORM_OF_PROCEDURE.get(current)).contains(field),
                        current + " error " + code + " points to " + field + ", which its form does not have");
            }
        }
        Assert.assertTrue(seen.containsAll(ProcErrors.fieldsByError().keySet()),
                "ProcErrors maps codes no save procedure throws: " + ProcErrors.fieldsByError().keySet());
    }

    @Test
    public void builtInSensorTypesAreTheOnesTheRulesLookUp() throws IOException {
        Path script = Paths.get(System.getProperty("project.dir", "."), "sql", "02_programmability.sql");
        if (!Files.isRegularFile(script)) {
            throw new SkipException("Run from the project folder to read the SQL scripts");
        }
        String sql = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        Matcher view = Pattern.compile("CREATE OR ALTER VIEW v_SensorTypeDetail AS.*?t\\.name IN \\(([^)]*)\\)",
                Pattern.DOTALL).matcher(sql);
        Assert.assertTrue(view.find(), "v_SensorTypeDetail lists the built-in names");
        Set<String> builtIn = new HashSet<String>();
        Matcher name = Pattern.compile("N'([^']+)'").matcher(view.group(1));
        while (name.find()) {
            builtIn.add(name.group(1));
        }
        Assert.assertEquals(builtIn, new HashSet<String>(Arrays.asList(service.DeviceSetup.MQ2,
                service.DeviceSetup.MQ135, service.DeviceSetup.TEMPERATURE, service.DeviceSetup.HUMIDITY)),
                "the names that cannot change must be exactly the ones DeviceSetup looks for");
    }
}
