package tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts the two whitespace-separated files of the UCI dataset into gas_dataset.csv for
 * sql/04_public_data.sql. Besides the class of the recording (as in the workshop) it writes the
 * phase of every row: before, during or after the stimulus. In HT_Sensor_dataset.dat the time t is
 * in hours from the start of the stimulus, and HT_Sensor_metadata.dat gives its duration dt in hours
 * (columns: id, date, class, t0, dt), so a row is "during" when 0 <= t < dt.
 *
 * Run File in NetBeans with both .dat files in the project folder, or pass their folder as argument.
 * Lines end with \n so that BULK INSERT ... ROWTERMINATOR = '0x0a' reads them the same on every OS.
 */
public class GasToCsv {

    public static final String HEADER = "induction_id,t,r1,r2,r3,r4,r5,r6,r7,r8,temp_c,humidity,class_name,phase";

    public static void main(String[] args) throws IOException {
        Path folder = Paths.get(args.length > 0 ? args[0] : ".");
        Map<String, Recording> recordings;
        try (BufferedReader meta = Files.newBufferedReader(folder.resolve("HT_Sensor_metadata.dat"),
                StandardCharsets.UTF_8)) {
            recordings = readMetadata(meta);
        }
        Path target = folder.resolve("gas_dataset.csv");
        Stats stats;
        try (BufferedReader in = Files.newBufferedReader(folder.resolve("HT_Sensor_dataset.dat"), StandardCharsets.UTF_8);
                BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            stats = convert(in, recordings, out);
        }
        System.out.println("Recordings in metadata: " + recordings.size());
        System.out.println("Rows written: " + stats.written + ", skipped (wrong column count): " + stats.skipped
                + ", rows of recordings missing from metadata: " + stats.unknown);
        System.out.println("Rows per class and phase: " + stats.perClassAndPhase);
        System.out.println("Wrote " + target.toAbsolutePath());
    }

    static Map<String, Recording> readMetadata(BufferedReader meta) throws IOException {
        Map<String, Recording> recordings = new HashMap<String, Recording>();
        meta.readLine(); // header
        String line;
        while ((line = meta.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 5) {
                continue;
            }
            recordings.put(normaliseId(p[0]), new Recording(p[2], Double.parseDouble(p[4])));
        }
        return recordings;
    }

    static Stats convert(BufferedReader in, Map<String, Recording> recordings, Writer out) throws IOException {
        Stats stats = new Stats();
        in.readLine(); // header
        out.write(HEADER + "\n");
        String line;
        while ((line = in.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p.length != 12) {
                stats.skipped++;
                continue;
            }
            Recording recording = recordings.get(normaliseId(p[0]));
            String className = recording == null ? "unknown" : recording.className;
            String phase = recording == null ? "unknown" : phaseOf(Double.parseDouble(p[1]), recording.durationHours);
            if (recording == null) {
                stats.unknown++;
            }
            StringBuilder row = new StringBuilder(normaliseId(p[0]));
            for (int i = 1; i < p.length; i++) {
                row.append(',').append(p[i]);
            }
            row.append(',').append(className).append(',').append(phase).append('\n');
            out.write(row.toString());
            stats.written++;
            String key = className + "/" + phase;
            Integer count = stats.perClassAndPhase.get(key);
            stats.perClassAndPhase.put(key, count == null ? 1 : count + 1);
        }
        return stats;
    }

    static String phaseOf(double tHours, double durationHours) {
        if (tHours < 0) {
            return "before";
        }
        return tHours < durationHours ? "during" : "after";
    }

    /** Ids may be written as 17 or 17.0 depending on the file; keep the integer form. */
    static String normaliseId(String id) {
        return id.endsWith(".0") ? id.substring(0, id.length() - 2) : id;
    }

    static final class Recording {

        final String className;
        final double durationHours;

        Recording(String className, double durationHours) {
            this.className = className;
            this.durationHours = durationHours;
        }
    }

    static final class Stats {

        int written;
        int skipped;
        int unknown;
        final Map<String, Integer> perClassAndPhase = new TreeMap<String, Integer>();
    }
}
