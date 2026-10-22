package service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.Locale;

/**
 * Temperature and humidity compensation of the two gas sensors.
 *
 * In clean air a MOX sensor still drifts with temperature (T) and relative humidity (RH). The drift
 * is learned from SAFE sessions with a least squares fit raw = baseline + bT * (T - tRef) + bRh * (RH - rhRef),
 * and removed from every reading: compensated = raw - bT * (T - tRef) - bRh * (RH - rhRef).
 * A gas then shows up as a rise of the compensated value above the baseline, whatever the weather.
 * This is a simplified, linear form of the decorrelation idea of Huerta et al. (2016).
 */
public final class CompensationModel {

    /** Source of a profile typed in on the profile page instead of fitted from data. */
    public static final String MANUAL = "manual";

    private final boolean fitted;
    private final double tRef;
    private final double rhRef;
    private final Channel mq2;
    private final Channel mq135;
    private final int safeRows;
    private final String source;
    private final String fittedAt;

    private CompensationModel(boolean fitted, double tRef, double rhRef, Channel mq2, Channel mq135,
            int safeRows, String source, String fittedAt) {
        this.fitted = fitted;
        this.tRef = tRef;
        this.rhRef = rhRef;
        this.mq2 = mq2;
        this.mq135 = mq135;
        this.safeRows = safeRows;
        this.source = source;
        this.fittedAt = fittedAt;
    }

    /** No correction: compensated values equal raw values. Used until a profile has been fitted. */
    public static CompensationModel identity() {
        return new CompensationModel(false, 0, 0, Channel.NONE, Channel.NONE, 0, "not fitted", null);
    }

    /**
     * Coefficients typed in by a technician, for example after a bench test or to correct a fitted
     * profile. Same formula as a fitted profile; baseline, noise and R² are unknown and left at 0.
     */
    public static CompensationModel manual(double tRef, double rhRef, double mq2BT, double mq2BRh,
            double mq135BT, double mq135BRh) {
        return new CompensationModel(true, tRef, rhRef, new Channel(mq2BT, mq2BRh, 0, 0, 0),
                new Channel(mq135BT, mq135BRh, 0, 0, 0), 0, MANUAL, Instant.now().toString());
    }

    /** Fits both channels on SAFE sessions only (gas present would bias the clean-air drift). */
    public static CompensationModel fit(double[] mq2Safe, double[] mq135Safe, double[] tempSafe, double[] rhSafe,
            String source) {
        double[] fit2 = SignalMath.fitTwoPredictors(mq2Safe, tempSafe, rhSafe);
        double[] fit135 = SignalMath.fitTwoPredictors(mq135Safe, tempSafe, rhSafe);
        double tRef = fit2[3];
        double rhRef = fit2[4];
        Channel c2 = new Channel(fit2[1], fit2[2], fit2[0], 0, fit2[5]);
        Channel c135 = new Channel(fit135[1], fit135[2], fit135[0], 0, fit135[5]);
        // noise left after compensation, measured on the same SAFE rows
        c2 = c2.withNoise(SignalMath.sd(applyAll(c2, mq2Safe, tempSafe, rhSafe, tRef, rhRef)));
        c135 = c135.withNoise(SignalMath.sd(applyAll(c135, mq135Safe, tempSafe, rhSafe, tRef, rhRef)));
        return new CompensationModel(true, tRef, rhRef, c2, c135, mq2Safe.length, source, Instant.now().toString());
    }

    public Double compensateMq2(Double raw, Double tempC, Double rhPct) {
        return apply(mq2, raw, tempC, rhPct);
    }

    public Double compensateMq135(Double raw, Double tempC, Double rhPct) {
        return apply(mq135, raw, tempC, rhPct);
    }

    /** Null when the raw value is missing, or when a fitted model lacks temperature or humidity. */
    private Double apply(Channel channel, Double raw, Double tempC, Double rhPct) {
        if (raw == null || raw.isNaN()) {
            return null;
        }
        if (!fitted) {
            return raw;
        }
        if (tempC == null || rhPct == null || tempC.isNaN() || rhPct.isNaN()) {
            return null;
        }
        return raw - channel.bT * (tempC - tRef) - channel.bRh * (rhPct - rhRef);
    }

    private static double[] applyAll(Channel c, double[] raw, double[] t, double[] rh, double tRef, double rhRef) {
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            out[i] = raw[i] - c.bT * (t[i] - tRef) - c.bRh * (rh[i] - rhRef);
        }
        return out;
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("fitted", fitted);
        json.addProperty("source", source);
        json.addProperty("fittedAt", fittedAt);
        json.addProperty("safeRows", safeRows);
        json.addProperty("tRef", tRef);
        json.addProperty("rhRef", rhRef);
        json.add("mq2", mq2.toJson());
        json.add("mq135", mq135.toJson());
        return json.toString();
    }

    /** Reads params_json of OperatingProfile; anything unreadable falls back to no correction. */
    public static CompensationModel fromJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return identity();
        }
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("fitted") || !json.get("fitted").getAsBoolean()) {
                return identity();
            }
            return new CompensationModel(true, number(json, "tRef"), number(json, "rhRef"),
                    Channel.fromJson(json.getAsJsonObject("mq2")), Channel.fromJson(json.getAsJsonObject("mq135")),
                    (int) number(json, "safeRows"), text(json, "source"), text(json, "fittedAt"));
        } catch (RuntimeException e) {
            return identity();
        }
    }

    /** One-line description for pages, for example "MQ-2 -12.3 per C, +4.1 per %RH". */
    public String describe() {
        if (!fitted) {
            return "Not fitted yet: compensated values equal raw values. Run tool.CompensationFitter.";
        }
        if (MANUAL.equals(source)) {
            return String.format(Locale.ROOT, "Set by hand: MQ-2 %+.1f per °C, %+.1f per %%RH; "
                    + "MQ-135 %+.1f per °C, %+.1f per %%RH; reference %.1f °C, %.1f %%RH",
                    mq2.bT, mq2.bRh, mq135.bT, mq135.bRh, tRef, rhRef);
        }
        return String.format(Locale.ROOT, "MQ-2 %+.1f per °C, %+.1f per %%RH (R² %.2f); "
                + "MQ-135 %+.1f per °C, %+.1f per %%RH (R² %.2f); reference %.1f °C, %.1f %%RH",
                mq2.bT, mq2.bRh, mq2.r2, mq135.bT, mq135.bRh, mq135.r2, tRef, rhRef);
    }

    public boolean isFitted() {
        return fitted;
    }

    public double getTRef() {
        return tRef;
    }

    public double getRhRef() {
        return rhRef;
    }

    public Channel getMq2() {
        return mq2;
    }

    public Channel getMq135() {
        return mq135;
    }

    public int getSafeRows() {
        return safeRows;
    }

    public String getSource() {
        return source;
    }

    public String getFittedAt() {
        return fittedAt;
    }

    private static double number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** Coefficients of one gas sensor. */
    public static final class Channel {

        static final Channel NONE = new Channel(0, 0, 0, 0, 0);

        private final double bT;
        private final double bRh;
        private final double baseline;
        private final double noiseSd;
        private final double r2;

        Channel(double bT, double bRh, double baseline, double noiseSd, double r2) {
            this.bT = bT;
            this.bRh = bRh;
            this.baseline = baseline;
            this.noiseSd = noiseSd;
            this.r2 = r2;
        }

        Channel withNoise(double sd) {
            return new Channel(bT, bRh, baseline, sd, r2);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("bT", bT);
            json.addProperty("bRh", bRh);
            json.addProperty("baseline", baseline);
            json.addProperty("noiseSd", noiseSd);
            json.addProperty("r2", r2);
            return json;
        }

        static Channel fromJson(JsonObject json) {
            if (json == null) {
                return NONE;
            }
            return new Channel(number(json, "bT"), number(json, "bRh"), number(json, "baseline"),
                    number(json, "noiseSd"), number(json, "r2"));
        }

        public double getBT() {
            return bT;
        }

        public double getBRh() {
            return bRh;
        }

        /** Mean clean-air value at the reference temperature and humidity. */
        public double getBaseline() {
            return baseline;
        }

        /** Spread of clean-air values left after compensation. */
        public double getNoiseSd() {
            return noiseSd;
        }

        /** Share of the clean-air variation explained by temperature and humidity. */
        public double getR2() {
            return r2;
        }
    }
}
