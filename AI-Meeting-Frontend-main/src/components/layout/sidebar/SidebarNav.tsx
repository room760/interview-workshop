import { Link, useLocation } from "react-router-dom";
import { BookOpen, Home, MessageSquare, ClipboardList } from "lucide-react";
import { ROUTES } from "@/lib/constants";
import { cn } from "@/lib/utils";

type SidebarNavProps = { isCollapsed?: boolean };

const items = [
  { label: "项目首页", path: ROUTES.home, icon: Home },
  { label: "AI 对话", path: ROUTES.chat, icon: MessageSquare },
  { label: "模拟面试", path: ROUTES.interviewIntro, icon: BookOpen },
  { label: "面试报告", path: ROUTES.interviewReport, icon: ClipboardList },
];

export default function SidebarNav({ isCollapsed }: SidebarNavProps) {
  const { pathname } = useLocation();
  return (
    <nav className="space-y-1 px-3" aria-label="系统导航">
      {items.map(({ label, path, icon: Icon }) => {
        const active = path === ROUTES.home
          ? pathname === path
          : path === ROUTES.interviewIntro
            ? pathname === path || pathname.startsWith(`${ROUTES.interviewRoom}/`) || pathname === ROUTES.interviewRoom
            : pathname === path || pathname.startsWith(`${path}/`);
        return (
          <Link
            key={path}
            to={path}
            title={isCollapsed ? label : undefined}
            className={cn(
              "flex min-h-10 items-center gap-3 rounded-md px-3 text-sm text-slate-700 hover:bg-slate-200",
              active && "bg-blue-100 font-semibold text-blue-800",
              isCollapsed && "justify-center px-2",
            )}
          >
            <Icon className="h-4 w-4 shrink-0" />
            {!isCollapsed && label}
          </Link>
        );
      })}
    </nav>
  );
}
