export default function HomePage() {
  return (
    <main className="page">
      <section className="hero" aria-labelledby="hero-title">
        <h1 id="hero-title">把照片交给模板，剩下的交给我们</h1>
        <p>
          浏览模板、按槽位上传素材，提交后在后台完成素材检查、模拟生成与结果整理。
          任务状态会持久化，刷新页面或重新登录后仍可继续跟踪。
        </p>
        <div className="hero-badges">
          <span className="badge" data-tone="accent">
            M0 骨架已就绪
          </span>
          <span className="badge">Next.js 15 · App Router</span>
          <span className="badge">Spring Boot 3.5 · MyBatis · MySQL 8.4</span>
          <span className="badge" data-tone="muted">
            模板页与登录页在 M5 接入
          </span>
        </div>
      </section>
    </main>
  );
}