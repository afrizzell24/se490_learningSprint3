package com.sensordata;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SensorDataProcessor{

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
    // original
    // public void calculate(double d) {

    //     long startTime = System.nanoTime();

    //     int i, j, k = 0;
    //     double[][][] data2 = new double[data.length][data[0].length][data[0][0].length];

    //     BufferedWriter out;

    //     // Write racing stats data into a file
    //     try {
    //         out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

    //         for (i = 0; i < data.length; i++) {
    //             for (j = 0; j < data[0].length; j++) {
    //                 for (k = 0; k < data[0][0].length; k++) {
    //                     data2[i][j][k] = data[i][j][k] / d - Math.pow(limit[i][j], 2.0);

    //                     if (average(data2[i][j]) > 10 && average(data2[i][j]) < 50)
    //                         break;
    //                     else if (Math.max(data[i][j][k], data2[i][j][k]) > data[i][j][k])
    //                         break;
    //                     else if (Math.pow(Math.abs(data[i][j][k]), 3) < Math.pow(Math.abs(data2[i][j][k]), 3)
    //                             && average(data[i][j]) < data2[i][j][k] && (i + 1) * (j + 1) > 0)
    //                         data2[i][j][k] *= 2;
    //                     else
    //                         continue;
    //                 }
    //             }
    //         }

    //         for (i = 0; i < data2.length; i++) {
    //             for (j = 0; j < data2[0].length; j++) {
    //                 out.write(data2[i][j] + "\t");
    //             }
    //         }

    //         out.close();

    //         long endTime = System.nanoTime();
    //         long elapsedMs = (endTime - startTime) / 1_000_000;
    //         System.out.println("calculate() completed in " + elapsedMs + " ms");

    //     } catch (Exception e) {
    //         System.out.println("Error= " + e);
    //         long endTime = System.nanoTime();
    //         long elapsedMs = (endTime - startTime) / 1_000_000;
    //         System.out.println("calculate() failed after " + elapsedMs + " ms");
    //     }
    // }
    
    // manual optimization
    public void calculate(double d) {

        long startTime = System.nanoTime();

        int i, j, k = 0;
        double[][][] data2 = new double[data.length][data[0].length][data[0][0].length];

        BufferedWriter out;

        // Write racing stats data into a file
        try {
            out = new BufferedWriter(new FileWriter("RacingStatsData.txt"));

            for (i = 0; i < data.length; i++) {
                for (j = 0; j < data[0].length; j++) {
                    double currentLimit = limit[i][j];
                    double currentLimitSquared = currentLimit * currentLimit;

                    for (k = 0; k < data[0][0].length; k++) {
                        double currentData = data[i][j][k];
                        data2[i][j][k] = currentData / d - currentLimitSquared;

                        double data2Avg = average(data2[i][j]);
                        double currentData2 = data2[i][j][k];
                        if (data2Avg > 10 && data2Avg < 50 || currentData2 > currentData)
                            break;
                        else if (Math.abs(currentData) < Math.abs(currentData2) && average(data[i][j]) < currentData2)
                            data2[i][j][k] *= 2;
                    }
                    out.write(data2[i][j] + "\t");
                }
            }

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