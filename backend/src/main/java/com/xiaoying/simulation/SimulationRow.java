package com.xiaoying.simulation;

import java.time.Instant;

/** simulation_config 单例行（id 固定为 1）。 */
public class SimulationRow {

    private String failStage;
    private Integer failRemaining;
    private Integer delayMaterialCheckMs;
    private Integer delayGeneratingMs;
    private Integer delayAssemblingMs;
    private Instant updatedAt;

    public String getFailStage() {
        return failStage;
    }

    public void setFailStage(String failStage) {
        this.failStage = failStage;
    }

    public Integer getFailRemaining() {
        return failRemaining;
    }

    public void setFailRemaining(Integer failRemaining) {
        this.failRemaining = failRemaining;
    }

    public Integer getDelayMaterialCheckMs() {
        return delayMaterialCheckMs;
    }

    public void setDelayMaterialCheckMs(Integer delayMaterialCheckMs) {
        this.delayMaterialCheckMs = delayMaterialCheckMs;
    }

    public Integer getDelayGeneratingMs() {
        return delayGeneratingMs;
    }

    public void setDelayGeneratingMs(Integer delayGeneratingMs) {
        this.delayGeneratingMs = delayGeneratingMs;
    }

    public Integer getDelayAssemblingMs() {
        return delayAssemblingMs;
    }

    public void setDelayAssemblingMs(Integer delayAssemblingMs) {
        this.delayAssemblingMs = delayAssemblingMs;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}