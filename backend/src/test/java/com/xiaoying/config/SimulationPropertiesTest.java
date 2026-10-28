package com.xiaoying.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 模拟参数只用于初始化 simulation_config；这里固定绑定与默认值。 */
class SimulationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsConfiguredDelaysAndDevSwitch() {
        runner.withPropertyValues(
                        "app.simulation.dev-api-enabled=true",
                        "app.simulation.delays.material-check-ms=100",
                        "app.simulation.delays.generating-ms=8000",
                        "app.simulation.delays.assembling-ms=200")
                .run(context -> {
                    SimulationProperties properties = context.getBean(SimulationProperties.class);
                    assertThat(properties.isDevApiEnabled()).isTrue();
                    assertThat(properties.getDelays().getMaterialCheckMs()).isEqualTo(100);
                    assertThat(properties.getDelays().getGeneratingMs()).isEqualTo(8000);
                    assertThat(properties.getDelays().getAssemblingMs()).isEqualTo(200);
                });
    }

    @Test
    void defaultDelaysMatchSeedValues() {
        // 显式给 dev-api-enabled 赋值：容器环境里的 APP_SIMULATION_DEV_API_ENABLED 会参与绑定
        runner.withPropertyValues("app.simulation.dev-api-enabled=false")
                .run(context -> {
                    SimulationProperties properties = context.getBean(SimulationProperties.class);
                    assertThat(properties.isDevApiEnabled()).isFalse();
                    assertThat(properties.getDelays().getMaterialCheckMs()).isEqualTo(800);
                    assertThat(properties.getDelays().getGeneratingMs()).isEqualTo(2500);
                    assertThat(properties.getDelays().getAssemblingMs()).isEqualTo(800);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SimulationProperties.class)
    static class TestConfig {
    }
}
