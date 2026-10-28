package com.xiaoying.simulation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时确保 simulation_config 单例行存在；已存在时不做任何修改。 */
@Component
public class SimulationInitializer implements ApplicationRunner {

    private final SimulationService simulationService;

    public SimulationInitializer(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        simulationService.ensureInitialized();
    }
}