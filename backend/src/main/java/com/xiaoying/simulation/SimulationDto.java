package com.xiaoying.simulation;

import java.time.Instant;

public record SimulationDto(
        String failStage,
        int failRemaining,
        int delayMaterialCheckMs,
        int delayGeneratingMs,
        int delayAssemblingMs,
        Instant updatedAt) {

    public static SimulationDto from(SimulationRow row) {
        return new SimulationDto(
                row.getFailStage(),
                row.getFailRemaining() == null ? 0 : row.getFailRemaining(),
                row.getDelayMaterialCheckMs() == null ? 0 : row.getDelayMaterialCheckMs(),
                row.getDelayGeneratingMs() == null ? 0 : row.getDelayGeneratingMs(),
                row.getDelayAssemblingMs() == null ? 0 : row.getDelayAssemblingMs(),
                row.getUpdatedAt());
    }
}