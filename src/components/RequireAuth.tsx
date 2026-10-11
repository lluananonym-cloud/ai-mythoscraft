import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { Loader2 } from "lucide-react";

const RequireAuth = ({ children, adminOnly = false, skipOnboarding = false }: { children: JSX.Element; adminOnly?: boolean; skipOnboarding?: boolean }) => {
  const { user, loading, isAdmin, profile } = useAuth();
  const loc = useLocation();
  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary" /></div>;
  // Nach dem Anmelden zurück zur gewünschten Seite (z. B. /cloud-pc aus der Code-App), nicht immer in den Chat.
  if (!user) return <Navigate to={`/auth?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace state={{ from: loc.pathname }} />;
  if (adminOnly && !isAdmin) return <Navigate to="/app" replace />;
  if (!skipOnboarding && profile && !profile.onboarded && loc.pathname !== "/onboarding") {
    return <Navigate to="/onboarding" replace />;
  }
  return children;
};
export default RequireAuth;
