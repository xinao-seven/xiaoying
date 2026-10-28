package com.xiaoying.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模拟参数初始值。
 *
 * <p>运行期的权威来源是 {@code simulation_config} 单例表（见 D4）；本类只提供首次初始化该表所需的值，
 * 以及开发接口开关，任何阶段执行逻辑都不得直接读取这里的耗时。
 */
@ConfigurationProperties(prefix = "app.simulation")
public class SimulationProperties {

    /** 是否开放 /api/dev/simulation；默认关闭，compose 演示环境通过环境变量打开。 */
    private boolean devApiEnabled;

    private final Delays delays = new Delays();

    public boolean isDevApiEnabled() {
        return devApiEnabled;
    }

    public void setDevApiEnabled(boolean devApiEnabled) {
        this.devApiEnabled = devApiEnabled;
    }

    public Delays getDelays() {
        return delays;
    }

    public static class Delays {

        /** 素材检查阶段初始耗时（毫秒）。 */
        private long materialCheckMs = 800;

        /** 模拟生成阶段初始耗时（毫秒）。 */
        private long generatingMs = 2500;

        /** 结果整理阶段初始耗时（毫秒）。 */
        private long assemblingMs = 800;

        public long getMaterialCheckMs() {
            return materialCheckMs;
        }

        public void setMaterialCheckMs(long materialCheckMs) {
            this.materialCheckMs = materialCheckMs;
        }

        public long getGeneratingMs() {
            return generatingMs;
        }

        public void setGeneratingMs(long generatingMs) {
            this.generatingMs = generatingMs;
        }

        public long getAssemblingMs() {
            return assemblingMs;
        }

        public void setAssemblingMs(long assemblingMs) {
            this.assemblingMs = assemblingMs;
        }
    }
}
