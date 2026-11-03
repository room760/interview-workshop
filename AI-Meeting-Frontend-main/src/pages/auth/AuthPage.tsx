import AuthMarketingPanel from "@/components/auth/AuthMarketingPanel";
import AuthFormCard from "@/components/auth/AuthFormCard";
import { useAuthPageController } from "@/hooks/auth/useAuthPageController";

export default function AuthPage() {
  const {
    mode, formData, loading, error, registerLoading, localError,
    switchMode, handleInputChange, handleSubmit,
  } = useAuthPageController();

  return (
    <div className="h-full overflow-y-auto bg-slate-100 px-5 py-10">
      <div className="mx-auto grid max-w-4xl items-center gap-8 md:mt-10 md:grid-cols-2">
        <AuthMarketingPanel />
        <AuthFormCard
          mode={mode}
          formData={formData}
          errorMessage={error || localError}
          isSubmitting={loading || registerLoading}
          onSwitchMode={switchMode}
          onInputChange={handleInputChange}
          onSubmit={handleSubmit}
        />
      </div>
    </div>
  );
}
