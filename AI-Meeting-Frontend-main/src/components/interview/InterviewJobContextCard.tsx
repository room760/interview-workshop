import { BriefcaseBusiness } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { InterviewJobContext } from "@/services/interviewService";

type InterviewJobContextCardProps = {
  value: InterviewJobContext;
  disabled?: boolean;
  onChange: (value: InterviewJobContext) => void;
};

export default function InterviewJobContextCard({
  value,
  disabled = false,
  onChange,
}: InterviewJobContextCardProps) {
  const updateField = (field: keyof InterviewJobContext, nextValue: string) => {
    onChange({
      ...value,
      [field]: nextValue,
    });
  };

  return (
    <Card className="border-slate-200 p-4">
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
          <BriefcaseBusiness className="h-4 w-4" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-slate-900">目标岗位信息</p>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            建议填写岗位名称以启用岗位匹配分析；不填写也可继续原有面试流程。内容会在本次登录会话中自动保存。
          </p>
        </div>
      </div>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="interview-job-title">岗位名称（建议填写）</Label>
          <Input
            id="interview-job-title"
            value={value.jobTitle}
            maxLength={200}
            disabled={disabled}
            placeholder="例如：Java 后端开发工程师"
            onChange={(event) => updateField("jobTitle", event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor="interview-company-name">公司名称（可选）</Label>
          <Input
            id="interview-company-name"
            value={value.companyName}
            maxLength={200}
            disabled={disabled}
            placeholder="例如：某科技公司"
            onChange={(event) => updateField("companyName", event.target.value)}
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="interview-job-requirement">岗位要求（可选）</Label>
          <Textarea
            id="interview-job-requirement"
            value={value.jobRequirement}
            maxLength={3000}
            disabled={disabled}
            className="min-h-[88px] resize-y"
            placeholder="粘贴职位描述、技能要求或工作职责"
            onChange={(event) =>
              updateField("jobRequirement", event.target.value)
            }
          />
        </div>
        <div className="space-y-2 sm:col-span-2">
          <Label htmlFor="interview-official-domain">
            企业官网域名（可选）
          </Label>
          <Input
            id="interview-official-domain"
            value={value.officialDomain}
            maxLength={253}
            disabled={disabled}
            placeholder="例如：example.com"
            onChange={(event) =>
              updateField("officialDomain", event.target.value)
            }
          />
          <p className="text-[11px] text-slate-400">
            仅填写域名，不需要包含 http://、https:// 或具体路径。
          </p>
        </div>
      </div>
    </Card>
  );
}
