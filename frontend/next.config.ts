import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // M9 容器交付使用 standalone 产物
  output: "standalone",
  eslint: {
    // lint 由独立 npm run lint 执行，避免 build 重复跑
    ignoreDuringBuilds: true,
  },
};

export default nextConfig;