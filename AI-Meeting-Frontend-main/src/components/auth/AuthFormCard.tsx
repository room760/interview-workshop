import { Link } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ROUTES } from "@/lib/constants";
import type { AuthFormData, AuthMode } from "@/hooks/auth/useAuthPageController";

type AuthFormCardProps = {
  mode: AuthMode;
  formData: AuthFormData;
  errorMessage: string;
  isSubmitting: boolean;
  onSwitchMode: (mode: AuthMode) => void;
  onInputChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  onSubmit: () => void;
};

export default function AuthFormCard({
  mode, formData, errorMessage, isSubmitting,
  onSwitchMode, onInputChange, onSubmit,
}: AuthFormCardProps) {
  const isLogin = mode === "login";
  return (
    <Card className="border-slate-300 p-7">
      <div className="flex gap-2 border-b border-slate-200 pb-4">
        <Button type="button" variant={isLogin ? "default" : "outline"} onClick={() => onSwitchMode("login")}>登录</Button>
        <Button type="button" variant={!isLogin ? "default" : "outline"} onClick={() => onSwitchMode("register")}>注册</Button>
      </div>
      <div className="mt-5 space-y-4">
        <label className="block space-y-1 text-sm font-medium text-slate-700">
          <span>用户名</span>
          <Input name="username" placeholder="输入用户名" value={formData.username} onChange={onInputChange} />
        </label>
        <label className="block space-y-1 text-sm font-medium text-slate-700">
          <span>密码</span>
          <Input name="password" type="password" placeholder="输入密码" value={formData.password} onChange={onInputChange} />
        </label>
        {!isLogin && (
          <label className="block space-y-1 text-sm font-medium text-slate-700">
            <span>确认密码</span>
            <Input name="confirmPassword" type="password" placeholder="再次输入密码" value={formData.confirmPassword} onChange={onInputChange} />
          </label>
        )}
      </div>
      {errorMessage && <p role="alert" className="mt-4 text-sm text-red-700">{errorMessage}</p>}
      <Button className="mt-6 w-full" onClick={onSubmit} disabled={isSubmitting}>
        {isSubmitting && <Loader2 className="h-4 w-4 animate-spin" />}
        {isLogin ? "登录" : "创建账号"}
      </Button>
      <p className="mt-4 text-center text-xs text-slate-500">
        <Link to={ROUTES.home} className="text-blue-700 hover:underline">返回首页</Link>
      </p>
    </Card>
  );
}
