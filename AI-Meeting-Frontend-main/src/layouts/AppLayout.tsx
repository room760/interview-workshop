import { Outlet, useLocation } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { Menu } from "lucide-react";
import { cn } from "@/lib/utils";
import { useState } from "react";
import Sidebar from "@/components/layout/Sidebar";
import { ROUTES } from "@/lib/constants";
import { useIsMobile } from "@/hooks/use-mobile";

export default function AppLayout() {
  const isMobile = useIsMobile();
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const location = useLocation();
  const isAuthPage = location.pathname.startsWith(ROUTES.auth);
  const hideSidebar = location.pathname === ROUTES.home;
  const shouldCollapse = isAuthPage || isSidebarCollapsed;

  return (
    <div className="flex h-screen overflow-hidden bg-slate-100">
      {!isMobile && !hideSidebar && (
        <aside className={cn("relative hidden shrink-0 transition-all md:block", shouldCollapse ? "w-16" : "w-60")}>
          <Sidebar
            isCollapsed={shouldCollapse}
            onToggleCollapse={!isAuthPage ? () => setIsSidebarCollapsed((previous) => !previous) : undefined}
          />
        </aside>
      )}
      {!hideSidebar && (
        <Sheet key={location.pathname}>
          <SheetTrigger asChild>
            <Button variant="outline" size="icon" className="absolute left-3 top-3 z-50 md:hidden" aria-label="打开菜单"><Menu /></Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-72 p-0"><Sidebar /></SheetContent>
        </Sheet>
      )}
      <main className="relative min-w-0 flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}
