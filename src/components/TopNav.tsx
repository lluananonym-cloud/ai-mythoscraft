import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import Logo from "./Logo";
import { useAuth } from "@/hooks/useAuth";
import { LogOut, MessageSquare, Key, Shield, BookOpen } from "lucide-react";

const TopNav = () => {
  const { user, isAdmin, signOut } = useAuth();
  const nav = useNavigate();

  return (
    <header className="sticky top-0 z-40 glass border-b border-glass-border">
      <div className="container flex h-16 items-center justify-between">
        <Link to="/"><Logo size="sm" /></Link>
        <nav className="hidden md:flex items-center gap-1">
          <Link to="/docs"><Button variant="ghost" size="sm"><BookOpen className="h-4 w-4 mr-1.5" />API Docs</Button></Link>
          {user && (
            <>
              <Link to="/app"><Button variant="ghost" size="sm"><MessageSquare className="h-4 w-4 mr-1.5" />Chat</Button></Link>
              <Link to="/dashboard"><Button variant="ghost" size="sm"><Key className="h-4 w-4 mr-1.5" />API Keys</Button></Link>
              {isAdmin && <Link to="/admin"><Button variant="ghost" size="sm"><Shield className="h-4 w-4 mr-1.5" />Admin</Button></Link>}
            </>
          )}
        </nav>
        <div className="flex items-center gap-2">
          {user ? (
            <Button variant="ghost" size="sm" onClick={async () => { await signOut(); nav("/"); }}>
              <LogOut className="h-4 w-4 mr-1.5" />Logout
            </Button>
          ) : (
            <>
              <Link to="/auth"><Button variant="ghost" size="sm">Login</Button></Link>
              <Link to="/auth"><Button size="sm" className="bg-gradient-primary text-primary-foreground hover:opacity-90">Get Started</Button></Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
};
export default TopNav;
