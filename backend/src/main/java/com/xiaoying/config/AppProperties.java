package com.xiaoying.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 素材与结果文件的根目录，容器内为数据卷挂载点。 */
    private Path dataDir = Path.of("./data");

    private final Upload upload = new Upload();
    private final Pipeline pipeline = new Pipeline();
    private final Session session = new Session();

    public Path getDataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public Upload getUpload() {
        return upload;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public Session getSession() {
        return session;
    }

    public static class Upload {
        /** 全局单文件上限，默认 10 MB。 */
        private long maxBytes = 10L * 1024 * 1024;

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    public static class Pipeline {
        /** worker 线程数，默认 1（串行）。 */
        private int concurrency = 1;
        /** 调度器轮询 PENDING 的间隔。 */
        private long pollIntervalMs = 1000;

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }

    public static class Session {
        /** 会话有效期，默认 7 天。 */
        private int ttlDays = 7;

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }
    }
}