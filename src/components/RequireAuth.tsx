import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { Loader2 } from "lucide-react";

const RequireAuth = ({ children, adminOnly = false }: { children: JSX.Element; adminOnly?: boolean }) => {
  const { user, loading, isAdmin } = useAuth();
  const loc = useLocation();
  if (loading) return <div className="min-h-screen flex items-center justify-center"><Loader2 className="h-8 w-8 animate-spin text-primary" /></div>;
  if (!user) return <Navigate to="/auth" replace state={{ from: loc.pathname }} />;
  if (adminOnly && !isAdmin) return <Navigate to="/app" replace />;
  return children;
};
export default RequireAuth;
