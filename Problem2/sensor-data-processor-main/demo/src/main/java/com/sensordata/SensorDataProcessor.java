package com.sensordata;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SensorDataProcessor {

    // Senson data and limits.
    public double[][][] data;
    public double[][] limit;

    // constructor
    public SensorDataProcessor(double[][][] data, double[][] limit) {
        this.data = data;
        this.limit = limit;
    }

    // calculates average of sensor data
    private double average(double[] array) {
        int i = 0;
        double val = 0;
        for (i = 0; i < array.length; i++) {
            val += array[i];
        }

        return val / array.length;
    }

    // calculate data
    public void calculate(double d) {

        long startTime = System.nanoTime();

        int i, j, k = 0;
        // AI Opt: Cache array dimensions to avoid repeated dereferences
        int numGroups = data.length;
        int numSensors = data[0].length;
        int numReadings = data[0][0].length;
        double[][][] data2 = new double[numGroups][numSensors][numReadings];

        // AI Opt: Precompute reciprocal of d — multiplication is faster than division
        double invD = 1.0 / d;

        BufferedWriter out;

        // Write racing stats data into a file
        try {
            out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

            for (i = 0; i < numGroups; i++) {
                for (j = 0; j < numSensors; j++) {
                    // AI Opt: Precompute limit^2 outside inner loop (loop-invariant);
                    // uses x*x instead of Math.pow(x, 2.0) — avoids expensive log/exp
                    double limitVal = limit[i][j];
                    double limitSq = limitVal * limitVal;

                    // AI Opt: Cache local array references to eliminate repeated indexing
                    double[] dataIJ = data[i][j];
                    double[] data2IJ = data2[i][j];

                    // AI Opt: Precompute average(data[i][j]) — invariant across k loop
                    double avgDataIJ = average(dataIJ);

                    // AI Opt: Maintain a running sum for incremental average of data2[i][j],
                    // converting O(n) average() calls per iteration to O(1) updates
                    double runningSum = 0.0;

                    for (k = 0; k < numReadings; k++) {
                        // AI Opt: Multiply by reciprocal instead of dividing by d
                        double val = dataIJ[k] * invD - limitSq;
                        data2IJ[k] = val;

                        // AI Opt: Incremental average — update running sum and divide by length
                        // instead of iterating the entire array each time
                        runningSum += val;
                        double avgData2 = runningSum / numReadings;

                        if (avgData2 > 10 && avgData2 < 50)
                            break;
                        // AI Opt: Simplified Math.max(a, b) > a to just b > a
                        else if (val > dataIJ[k])
                            break;
                        else {
                            // AI Opt: Replace Math.pow(Math.abs(x), 3) with direct multiplication
                            double absData = Math.abs(dataIJ[k]);
                            double absData2 = Math.abs(val);
                            double cubedData = absData * absData * absData;
                            double cubedData2 = absData2 * absData2 * absData2;

                            if (cubedData < cubedData2
                                    && avgDataIJ < val && (i + 1) * (j + 1) > 0)
                                data2IJ[k] *= 2;
                            else
                                continue;
                        }
                    }
                }
            }

            // AI Opt: Batch output into a StringBuilder to reduce I/O system calls
            StringBuilder sb = new StringBuilder();
            for (i = 0; i < data2.length; i++) {
                for (j = 0; j < data2[0].length; j++) {
                    sb.append(data2[i][j]).append("\t");
                }
            }
            out.write(sb.toString());

            out.close();

            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            System.out.println("calculate() completed in " + elapsedMs + " ms");

        } catch (Exception e) {
            System.out.println("Error= " + e);
            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000;
            System.out.println("calculate() failed after " + elapsedMs + " ms");
        }
    }

}