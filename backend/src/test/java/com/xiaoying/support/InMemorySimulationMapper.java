package com.xiaoying.support;

import com.xiaoying.simulation.SimulationMapper;
import com.xiaoying.simulation.SimulationRow;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemorySimulationMapper implements SimulationMapper {

    public SimulationRow row;
    public final AtomicInteger updates = new AtomicInteger();

    public InMemorySimulationMapper(SimulationRow row) {
        this.row = copy(row);
    }

    @Override
    public SimulationRow find() {
        return row == null ? null : copy(row);
    }

    @Override
    public SimulationRow findForUpdate() {
        return find();
    }

    @Override
    public void insert(SimulationRow row) {
        this.row = copy(row);
        updates.incrementAndGet();
    }

    @Override
    public int update(SimulationRow row) {
        this.row = copy(row);
        updates.incrementAndGet();
        return 1;
    }

    public static SimulationRow row(String failStage, int failRemaining, int materialCheckMs,
                                    int generatingMs, int assemblingMs) {
        SimulationRow row = new SimulationRow();
        row.setFailStage(failStage);
        row.setFailRemaining(failRemaining);
        row.setDelayMaterialCheckMs(materialCheckMs);
        row.setDelayGeneratingMs(generatingMs);
        row.setDelayAssemblingMs(assemblingMs);
        row.setUpdatedAt(java.time.Instant.parse("2026-10-28T00:00:00Z"));
        return row;
    }

    private static SimulationRow copy(SimulationRow source) {
        if (source == null) {
            return null;
        }
        SimulationRow target = new SimulationRow();
        target.setFailStage(source.getFailStage());
        target.setFailRemaining(source.getFailRemaining());
        target.setDelayMaterialCheckMs(source.getDelayMaterialCheckMs());
        target.setDelayGeneratingMs(source.getDelayGeneratingMs());
        target.setDelayAssemblingMs(source.getDelayAssemblingMs());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }
}