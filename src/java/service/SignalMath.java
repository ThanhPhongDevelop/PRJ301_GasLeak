package service;

import java.util.Arrays;

/** Small statistics helpers used by the compensation fit and its report (no external library). */
public final class SignalMath {

    private SignalMath() {
    }

    public static double mean(double[] xs) {
        requireData(xs);
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        return sum / xs.length;
    }

    /** Sample standard deviation (n - 1); 0 for a single value. */
    public static double sd(double[] xs) {
        requireData(xs);
        if (xs.length < 2) {
            return 0;
        }
        double m = mean(xs);
        double sum = 0;
        for (double x : xs) {
            sum += (x - m) * (x - m);
        }
        return Math.sqrt(sum / (xs.length - 1));
    }

    /**
     * Least squares fit of y = a + b1 * (x1 - mean1) + b2 * (x2 - mean2).
     * Centring the predictors keeps the 2 x 2 system well conditioned.
     * Returns {a, b1, b2, mean1, mean2, r2}; a equals the mean of y.
     */
    public static double[] fitTwoPredictors(double[] y, double[] x1, double[] x2) {
        requireData(y);
        if (x1.length != y.length || x2.length != y.length) {
            throw new IllegalArgumentException("All arrays must have the same length");
        }
        if (y.length < 4) {
            throw new IllegalArgumentException("At least 4 rows are needed to fit the compensation");
        }
        double my = mean(y);
        double m1 = mean(x1);
        double m2 = mean(x2);
        double s11 = 0;
        double s22 = 0;
        double s12 = 0;
        double s1y = 0;
        double s2y = 0;
        double syy = 0;
        for (int i = 0; i < y.length; i++) {
            double d1 = x1[i] - m1;
            double d2 = x2[i] - m2;
            double dy = y[i] - my;
            s11 += d1 * d1;
            s22 += d2 * d2;
            s12 += d1 * d2;
            s1y += d1 * dy;
            s2y += d2 * dy;
            syy += dy * dy;
        }
        double det = s11 * s22 - s12 * s12;
        if (s11 <= 0 || s22 <= 0 || det <= 1e-9 * s11 * s22) {
            throw new IllegalArgumentException(
                    "Temperature and humidity must both vary and must not move together to fit the compensation");
        }
        double b1 = (s1y * s22 - s2y * s12) / det;
        double b2 = (s2y * s11 - s1y * s12) / det;
        double ssRes = 0;
        for (int i = 0; i < y.length; i++) {
            double fitted = my + b1 * (x1[i] - m1) + b2 * (x2[i] - m2);
            ssRes += (y[i] - fitted) * (y[i] - fitted);
        }
        double r2 = syy <= 0 ? 0 : 1 - ssRes / syy;
        return new double[]{my, b1, b2, m1, m2, r2};
    }

    /** Percentile with linear interpolation, p between 0 and 1. */
    public static double percentile(double[] xs, double p) {
        requireData(xs);
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("p must be between 0 and 1");
        }
        double[] sorted = xs.clone();
        Arrays.sort(sorted);
        double pos = p * (sorted.length - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (pos - lower);
    }

    /** Share of values at or above the threshold. */
    public static double fractionAtOrAbove(double[] xs, double threshold) {
        if (xs.length == 0) {
            return 0;
        }
        int count = 0;
        for (double x : xs) {
            if (x >= threshold) {
                count++;
            }
        }
        return (double) count / xs.length;
    }

    /**
     * Area under the ROC curve: the chance that a random positive scores higher than a random
     * negative (ties count half). 0.5 means no separation, 1.0 means perfect separation.
     */
    public static double auc(double[] positives, double[] negatives) {
        if (positives.length == 0 || negatives.length == 0) {
            return Double.NaN;
        }
        double wins = 0;
        for (double p : positives) {
            for (double n : negatives) {
                if (p > n) {
                    wins += 1;
                } else if (p == n) {
                    wins += 0.5;
                }
            }
        }
        return wins / ((double) positives.length * negatives.length);
    }

    private static void requireData(double[] xs) {
        if (xs == null || xs.length == 0) {
            throw new IllegalArgumentException("No data");
        }
    }
}
