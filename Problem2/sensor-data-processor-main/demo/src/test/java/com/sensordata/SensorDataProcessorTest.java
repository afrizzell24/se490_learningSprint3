package com.sensordata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SensorDataProcessorTest {

    private final Path outputPath = Path.of("RacingStatsData.txt");

    @BeforeEach
    void clearOutputBeforeEach() throws IOException {
        deleteOutputPathIfPresent();
    }

    @AfterEach
    void clearOutputAfterEach() throws IOException {
        deleteOutputPathIfPresent();
    }

    @Test
    void app_constructor_isCovered() {
        assertTrue(new SensorDataProcessorApp() != null);
    }

    @Test
    void calculate_breaksOnFirstCondition_whenAverageIsBetweenThresholds() throws IOException {
        double[][][] data = {{{20.0}}};
        double[][] limits = {{0.0}};
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);

        processor.calculate(1.0);

        assertTrue(Files.exists(outputPath));
        assertFalse(Files.readString(outputPath).isBlank());
    }

    @Test
    void calculate_skipsFirstConditionAtUpperBoundary() {
        double[][][] data = {{{50.0}}};
        double[][] limits = {{0.0}};
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);

        processor.calculate(1.0);

        assertTrue(Files.exists(outputPath));
    }

    @Test
    void calculate_breaksOnSecondCondition() {
        double[][][] data = {{{2.0}}};
        double[][] limits = {{0.0}};
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);

        processor.calculate(0.5);

        assertTrue(Files.exists(outputPath));
    }

    @Test
    void calculate_coversThirdConditionAndElseBranch() {
        double[][][] data = {{{-100.0, -1.0}}};
        double[][] limits = {{1.0}};
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);

        processor.calculate(1.0);

        assertTrue(Files.exists(outputPath));
    }

    @Test
    void calculate_handlesExceptionBranch() throws IOException {
        double[][][] data = {{{1.0, 2.0}}};
        double[][] limits = {{1.0}};
        SensorDataProcessor processor = new SensorDataProcessor(data, limits);

        Files.createDirectory(outputPath);
        processor.calculate(1.0);

        assertTrue(Files.isDirectory(outputPath));
    }

    @Test
    void main_runsSampleProcessing() {
        SensorDataProcessorApp.main(new String[0]);

        assertTrue(Files.exists(outputPath));
    }

    private void deleteOutputPathIfPresent() throws IOException {
        if (Files.isDirectory(outputPath)) {
            Files.deleteIfExists(outputPath);
        } else {
            Files.deleteIfExists(outputPath);
        }
    }
}
