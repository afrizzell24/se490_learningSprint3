package com.sensordata;

public class SensorDataProcessorApp {
    
    public static void main(String[] args) {
        // Large sample 3D sensor data: [sensorGroup][sensor][readings]
        int groups = 100;
        int sensorsPerGroup = 100;
        int readingsPerSensor = 100;

        double[][][] sampleData = new double[groups][sensorsPerGroup][readingsPerSensor];
        double[][] limits = new double[groups][sensorsPerGroup];

        for (int i = 0; i < groups; i++) {
            for (int j = 0; j < sensorsPerGroup; j++) {
                limits[i][j] = 1.0 + (i + j) % 5 * 0.5; // varied limits
                for (int k = 0; k < readingsPerSensor; k++) {
                    sampleData[i][j][k] = 10.0 + ((i * j * k) % 100) / 2.0;
                }
            }
        }

        SensorDataProcessor processor = new SensorDataProcessor(sampleData, limits);

        System.out.println("Running sample sensor data processing...");

        // Invoke calculate with divisor value 2.0
        processor.calculate(2.0);

        System.out.println("Sample processing complete.");
    }
}
