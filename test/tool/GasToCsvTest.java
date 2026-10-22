package tool;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GasToCsvTest {

    private static final String META = "id date class t0 dt\n"
            + "0 07-04-15 banana 13.49 1.64\n"
            + "1 07-05-15 wine 19.61 0.54\n"
            + "2 07-06-15 background 7.20 1.00\n";
    private static final String DATA = "id time R1 R2 R3 R4 R5 R6 R7 R8 Temp. Humidity\n"
            + "0 -0.5 12.8 10.3 10.4 11.1 7.9 4.5 3.7 2.6 27.2 58.1\n"
            + "0 0.25 9.1 8.0 10.4 11.1 7.9 4.5 3.7 2.6 27.4 59.0\n"
            + "1.0 0.60 9.9 9.0 10.4 11.1 7.9 4.5 3.7 2.6 26.9 61.4\n"
            + "2 0.10 12.1 10.0 10.4 11.1 7.9 4.5 3.7 2.6 25.0 55.0\n"
            + "9 0.10 12.1 10.0 10.4 11.1 7.9 4.5 3.7 2.6 25.0 55.0\n"
            + "broken line\n";

    @Test
    public void writesClassAndStimulusPhase() throws Exception {
        Map<String, GasToCsv.Recording> meta = GasToCsv.readMetadata(new BufferedReader(new StringReader(META)));
        StringWriter out = new StringWriter();
        GasToCsv.Stats stats = GasToCsv.convert(new BufferedReader(new StringReader(DATA)), meta, out);
        String[] lines = out.toString().split("\n", -1);
        Assert.assertEquals(lines[0], GasToCsv.HEADER);
        Assert.assertTrue(lines[1].endsWith(",banana,before"));
        Assert.assertTrue(lines[2].endsWith(",banana,during"));
        Assert.assertTrue(lines[3].startsWith("1,0.60,"), "id 1.0 is written as 1");
        Assert.assertTrue(lines[3].endsWith(",wine,after"), "0.60 h is after a 0.54 h stimulus");
        Assert.assertTrue(lines[4].endsWith(",background,during"));
        Assert.assertTrue(lines[5].endsWith(",unknown,unknown"));
        Assert.assertEquals(stats.written, 5);
        Assert.assertEquals(stats.skipped, 1);
        Assert.assertEquals(stats.unknown, 1);
        Assert.assertFalse(out.toString().contains("\r"), "lines must end with \\n only");
    }
}
