import { Suspense } from "react";
import { LoginForm } from "@/features/auth/LoginForm";

export default function LoginPage() {
  return (
    <main className="page login-page">
      <Suspense fallback={<p className="state-panel">正在加载登录页…</p>}>
        <LoginForm />
      </Suspense>
    </main>
  );
}