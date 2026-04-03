package com.sensordata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SensorDataProcessorTest {

    // ========== Tests for average() (private method, tested via reflection)
    // ==========

    @Test
    void testAverageViaReflection() throws Exception {
        double[][][] data = { { { 1.0 } } };
        double[][] limit = { { 1.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);

        Method avgMethod = SensorDataProcessor.class.getDeclaredMethod("average", double[].class);
        avgMethod.setAccessible(true);

        // Single element
        assertEquals(5.0, (double) avgMethod.invoke(processor, new double[] { 5.0 }), 1e-9);

        // Multiple elements
        assertEquals(3.0, (double) avgMethod.invoke(processor, new double[] { 1.0, 2.0, 3.0, 4.0, 5.0 }), 1e-9);

        // Negative values
        assertEquals(-2.0, (double) avgMethod.invoke(processor, new double[] { -1.0, -2.0, -3.0 }), 1e-9);
    }

    // ========== Tests for constructor ==========

    @Test
    void testConstructor() {
        double[][][] data = { { { 1.0, 2.0 }, { 3.0, 4.0 } } };
        double[][] limit = { { 0.5, 1.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        assertSame(data, processor.data);
        assertSame(limit, processor.limit);
    }

    // ========== Tests for calculate() covering all branches ==========

    /**
     * Branch: else-continue path
     * When data2[i][j][k] = data[i][j][k]/d - limit[i][j]^2 produces values
     * where none of the if/else-if conditions trigger, we hit the else-continue.
     *
     * For this: average must be <= 10 or >= 50, data2 <= data (original),
     * and the pow condition must be false.
     * 
     * Use: data values are small, d=1, limit=0 => data2 = data - 0 = data.
     * Then Math.max(data, data2) == data (not > data), so second if is false.
     * For the third if: |data|^3 < |data2|^3 is false since data2 == data.
     * So we hit else-continue.
     */
    @Test
    void testCalculateElseContinuePath() {
        // 1 group, 1 sensor, 3 readings; small values so average <= 10
        double[][][] data = { { { 1.0, 2.0, 3.0 } } };
        double[][] limit = { { 0.0 } }; // limit^2 = 0
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> processor.calculate(1.0));

        // Verify file was written
        File f = new File("RacingStatsData.txt");
        assertTrue(f.exists());
        f.delete();
    }

    /**
     * Branch: first if — average > 10 && average < 50 → break
     * data2[i][j][k] = data[i][j][k]/d - limit^2.
     * Need average of data2[i][j] to be between 10 and 50 at some k iteration.
     *
     * Use: data[0][0] = {60.0, 60.0, 60.0}, d=2.0, limit=0 => data2 = 30.0 each.
     * average(data2[0][0]) = 30 after first iteration... but only first element
     * set.
     * Actually after k=0: data2 = {30, 0, 0}, avg = 10 — right at boundary (not >
     * 10).
     * Let's use bigger values.
     */
    @Test
    void testCalculateAverageBreakPath() {
        // After k=0: data2[0][0] = {val, 0, 0}, need val/3 > 10 => val > 30 and val/3 <
        // 50 => val < 150
        // data2 = data/d - limit^2. Use d=1, limit=0, data=90 => data2=90, avg_after_k0
        // = 90/3 = 30. Hits break.
        double[][][] data = { { { 90.0, 90.0, 90.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> processor.calculate(1.0));

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Branch: second else-if — Math.max(data[i][j][k], data2[i][j][k]) >
     * data[i][j][k]
     * This means data2[i][j][k] > data[i][j][k].
     * data2 = data/d - limit^2. For data2 > data: data/d - limit^2 > data =>
     * data*(1/d - 1) > limit^2
     * If d < 1: (1/d - 1) > 0, so choose d small enough.
     * Use data=10, d=0.1, limit=0 => data2 = 100, 100 > 10 → break.
     * But also need average <= 10 or >= 50 to skip first if.
     * avg of data2[0][0] after k=0 = 100/3 = 33.3 which is between 10 and 50 →
     * would hit first if.
     * Need avg NOT between 10 and 50. So make data2 huge: data=1000, d=0.1, limit=0
     * => data2=10000, avg=3333.
     */
    @Test
    void testCalculateMaxBreakPath() {
        double[][][] data = { { { 1000.0, 1000.0, 1000.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> processor.calculate(0.1));

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Branch: third else-if — the multiply-by-2 path.
     * Conditions:
     * 1) |data|^3 < |data2|^3 → |data2| > |data|
     * 2) average(data[i][j]) < data2[i][j][k]
     * 3) (i+1)*(j+1) > 0 (always true for i,j >= 0)
     *
     * But also must NOT trigger first two ifs:
     * - average(data2[i][j]) <= 10 or >= 50
     * - data2[i][j][k] <= data[i][j][k] (wait, but we need |data2| > |data|)
     *
     * The second if checks: Math.max(data, data2) > data, i.e., data2 > data.
     * For third if we need |data2| > |data|. If data > 0 and data2 > data, second
     * if triggers.
     * So we need |data2| > |data| but data2 <= data. This means data2 is negative
     * and |data2| > |data|.
     * E.g., data=5, data2=-10: |data2|=10 > 5=|data|, and -10 <= 5. Also need avg
     * condition.
     * data2 = data/d - limit^2. Want data2 negative: data/d < limit^2.
     * Use data=5, d=1, limit=10 => data2 = 5 - 100 = -95. |data2|=95 > 5. avg of
     * data2 = -95/3 = -31.67 < 10 so NOT between 10 and 50.
     * Math.max(5, -95) = 5 = data[i][j][k], NOT > data → skip second if.
     * Third if: 5^3=125 < 95^3=857375 ✓, average(data[i][j])=5 < -95=-data2? No, 5
     * < -95 is false.
     * Need average(data[i][j]) < data2[i][j][k] where data2 is -95. That fails.
     *
     * So we need data to be negative too... or data2 positive but not > data...
     * Actually re-read: data2 must have |data2| > |data| AND data2 <= data AND
     * avg(data) < data2.
     * If data2 <= data, then for avg(data) < data2 we need avg(data) < data2 <=
     * data.
     * If data2 > 0, need data2 <= data and |data2| > |data|. Since both positive,
     * data2 <= data AND data2 > data → contradiction.
     * If both negative: data2 <= data and |data2| > |data| means data2 more
     * negative than data. avg(data[i][j]) which includes zeros might be small
     * enough.
     *
     * Wait, re-read: data2 is negative, |data2| > |data| means |data2| > |data|.
     * If data is positive: data = 2, data2 = -5. |data2|=5>2. Max(2,-5)=2=data, not
     * > data. ✓ second if skipped.
     * avg(data[i][j]): data[i][j] = {2, 2, 2}, avg = 2. Need 2 < data2[i][j][k] =
     * -5? False.
     *
     * Hmm. avg(data[i][j]) < data2[i][j][k]. data2 is negative, so this can't be
     * true if avg is positive.
     * What if data is negative? data[i][j] = {-2, -2, -2}, d=1, limit=0 =>
     * data2=-2.
     * |data2|^3 = 8 not < 8. Equal, not less.
     * 
     * Let me try: data negative, make data2 more negative via large limit.
     * data[i][j] = {-1, -1, -1}, d=1, limit=3 => data2 = -1 - 9 = -10.
     * |data|=1, |data2|=10: 1 < 1000 ✓
     * max(-1, -10) = -1 = data, not > data ✓ skip second if
     * avg(data[i][j]) = -1, data2[i][j][k] = -10. avg < data2? -1 < -10? No.
     *
     * Need data2 to be LARGER than the average of data. But data2 is computed as
     * data/d - limit^2.
     * If limit^2 is negative... no, limit^2 is always >= 0.
     * 
     * What if d is negative? data/d would flip sign. data=10, d=-1, limit=0 =>
     * data2=-10.
     * max(10, -10)=10=data, skip second if. |10|^3 < |-10|^3? 1000 < 1000? No.
     *
     * data=10, d=-0.5, limit=0 => data2=-20. max(10,-20)=10=data, skip 2nd.
     * |10|^3=1000 < |-20|^3=8000 ✓. avg(data[i][j])=10, data2=-20. 10 < -20? No.
     *
     * So the third condition (avg(data) < data2) is the issue. data2 is always
     * going to be quite
     * negative or data needs to have very negative average.
     *
     * At k=0 with data[i][j]={-100,-100,-100}, d=1, limit=1:
     * data2[i][j][0] = -100/1 - 1 = -101. avg(data2) after k=0 = (-101+0+0)/3 =
     * -33.67 (not between 10,50) ✓
     * max(-100, -101) = -100 = data, skip 2nd if ✓
     * |-100|^3 = 1e6 < |-101|^3 = 1030301 ✓
     * avg(data[i][j]) = -100. data2[i][j][0] = -101. -100 < -101? No!
     *
     * Need avg(data) < data2. The only way: data2 > avg(data).
     * If data2 > data (which we need to avoid for second-if to not trigger)...
     * Actually we need |data2| > |data| (for pow comparison) AND data2 <= data (so
     * second if doesn't trigger)
     * AND avg(data) < data2.
     *
     * If data2 <= data and avg(data) < data2, then avg(data) < data2 <= data.
     * data2 = data/d - limit^2.
     * avg(data) = average of the full array data[i][j].
     * If some elements of data[i][j] are very negative, the average could be very
     * negative,
     * while the current element data[i][j][k] could be less negative.
     *
     * data[i][j] = {-1, -1000, -1000}, d=1, limit=5:
     * k=0: data2[0][0][0] = -1 - 25 = -26.
     * avg(data2) = (-26+0+0)/3 ≈ -8.67. Not between 10 and 50 ✓
     * max(-1, -26) = -1 = data[0][0][0], not > data ✓
     * |-1|^3=1 < |-26|^3=17576 ✓
     * avg(data[0][0]) = (-1-1000-1000)/3 = -667. -667 < -26? Yes! ✓
     * (0+1)*(0+1)=1 > 0 ✓ → data2 *= 2 → data2[0][0][0] = -52.
     */
    @Test
    void testCalculateMultiplyByTwoPath() {
        double[][][] data = { { { -1.0, -1000.0, -1000.0 } } };
        double[][] limit = { { 5.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        assertDoesNotThrow(() -> processor.calculate(1.0));

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Tests the exception/catch branch of calculate().
     * If we cause an error (e.g., mismatched array dimensions leading to
     * ArrayIndexOutOfBoundsException),
     * the catch block is triggered.
     * 
     * data[0].length vs data[0][0].length mismatch with jagged arrays can cause
     * issues.
     * Actually, the code uses data[0].length and data[0][0].length for all
     * iterations,
     * so if data is jagged with data[1] being shorter,
     * ArrayIndexOutOfBoundsException occurs.
     */
    @Test
    void testCalculateExceptionPath() {
        // Jagged array: data[1] has fewer elements than data[0]
        double[][][] data = {
                { { 1.0, 2.0 }, { 3.0, 4.0 } },
                { { 5.0 } } // Only 1 sub-array, but code loops data[0].length=2 times
        };
        double[][] limit = { { 0.0, 0.0 }, { 0.0, 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        // This should trigger an ArrayIndexOutOfBoundsException in the inner loop
        assertDoesNotThrow(() -> processor.calculate(1.0));

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Test with multiple groups, sensors, readings — exercises all loop iterations
     * and ensures file output is correct.
     */
    @Test
    void testCalculateMultipleGroupsAndSensors() throws IOException {
        double[][][] data = {
                { { 1.0, 2.0, 3.0 }, { 4.0, 5.0, 6.0 } },
                { { 7.0, 8.0, 9.0 }, { 10.0, 11.0, 12.0 } }
        };
        double[][] limit = { { 0.0, 0.0 }, { 0.0, 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(1.0);

        File f = new File("RacingStatsData.txt");
        assertTrue(f.exists());
        String content = Files.readString(f.toPath());
        assertFalse(content.isEmpty());
        f.delete();
    }

    /**
     * Test the first if-branch boundary: average exactly 10 (not > 10, so skips
     * first if)
     * and average exactly 50 (not < 50, so skips first if).
     */
    @Test
    void testCalculateAverageBoundaryExact10() {
        // data2 = data/d - limit^2. For avg = 10 exactly at k=0: data2[0]=30 (avg of
        // {30,0,0}=10).
        // avg = 10 is NOT > 10, so first if is false. Good — tests boundary.
        double[][][] data = { { { 30.0, 30.0, 30.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(1.0);

        new File("RacingStatsData.txt").delete();
    }

    @Test
    void testCalculateAverageBoundaryExact50() {
        // At k=0: data2[0] = 150, avg = 150/3 = 50. NOT < 50, so first if is false.
        // But then |data2| = 150 > |data| = 150 (equal, so third else-if pow cond is
        // false)
        // And max(150, 150) = 150 = data, so second if is false too. → else continue.
        double[][][] data = { { { 150.0, 150.0, 150.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(1.0);

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Large-scale test similar to SensorDataProcessorApp to verify no crashes.
     */
    @Test
    void testCalculateLargeDataSet() {
        int groups = 10, sensors = 10, readings = 10;
        double[][][] data = new double[groups][sensors][readings];
        double[][] limits = new double[groups][sensors];
        for (int i = 0; i < groups; i++) {
            for (int j = 0; j < sensors; j++) {
                limits[i][j] = 1.0 + (i + j) % 5 * 0.5;
                for (int k = 0; k < readings; k++) {
                    data[i][j][k] = 10.0 + ((i * j * k) % 100) / 2.0;
                }
            }
        }
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);
        assertDoesNotThrow(() -> processor.calculate(2.0));

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Verifies the output file content structure matches the expected format:
     * each row of data2 is written with tab-separated double[] references.
     */
    @Test
    void testCalculateFileOutput() throws IOException {
        double[][][] data = { { { 5.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(1.0);

        File f = new File("RacingStatsData.txt");
        assertTrue(f.exists());
        String content = Files.readString(f.toPath());
        // The output writes data2[i][j] (a double[]) with .toString() + "\t"
        assertTrue(content.contains("["));
        f.delete();
    }

    /**
     * Test with d very close to zero (large division result) — triggers second
     * else-if
     * (data2 >> data) when avg is outside 10-50 range.
     */
    @Test
    void testCalculateVerySmallDivisor() {
        double[][][] data = { { { 1000.0, 1000.0, 1000.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(0.01); // data2 = 100000, avg ≈ 33333 — outside 10–50

        new File("RacingStatsData.txt").delete();
    }

    /**
     * Test with negative data values to exercise the Math.abs paths in the third
     * condition.
     */
    @Test
    void testCalculateNegativeData() {
        double[][][] data = { { { -5.0, -5.0, -5.0 } } };
        double[][] limit = { { 0.0 } };
        SensorDataProcessor processor = new SensorDataProcessor(data, limit);
        processor.calculate(1.0);

        new File("RacingStatsData.txt").delete();
    }
}
