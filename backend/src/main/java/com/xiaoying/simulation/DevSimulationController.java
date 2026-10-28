package com.xiaoying.simulation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发模拟接口。默认关闭；未开启时该 Bean 不存在，请求落到 404 JSON。
 */
@RestController
@RequestMapping("/api/dev/simulation")
@ConditionalOnProperty(prefix = "app.simulation", name = "dev-api-enabled", havingValue = "true")
public class DevSimulationController {

    private final SimulationService simulationService;

    public DevSimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping
    public SimulationDto get() {
        return simulationService.get();
    }

    @PostMapping
    public SimulationDto update(@RequestBody(required = false) SimulationUpdateRequest request) {
        return simulationService.update(request);
    }
}