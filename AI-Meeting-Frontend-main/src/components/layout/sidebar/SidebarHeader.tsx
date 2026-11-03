import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { APP_BRAND_NAME } from "@/lib/branding";
import { cn } from "@/lib/utils";

type SidebarHeaderProps = { isCollapsed?: boolean; onToggleCollapse?: () => void };

export default function SidebarHeader({ isCollapsed, onToggleCollapse }: SidebarHeaderProps) {
  return (
    <div className={cn("mb-4 flex items-center gap-2 border-b border-slate-300 px-3 py-4", isCollapsed && "justify-center")}>
      <img src="/interview-workshop-mark.svg" alt="" className="h-8 w-8" />
      {!isCollapsed && <span className="flex-1 font-bold text-slate-800">{APP_BRAND_NAME}</span>}
      {onToggleCollapse && (
        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={onToggleCollapse} aria-label={isCollapsed ? "展开菜单" : "收起菜单"}>
          {isCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </Button>
      )}
    </div>
  );
}
