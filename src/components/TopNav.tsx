import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import Logo from "./Logo";
import MinecraftAvatar from "./MinecraftAvatar";
import { useAuth } from "@/hooks/useAuth";
import {
  LogOut,
  MessageSquare,
  Code2,
  BookOpen,
  Menu,
  User as UserIcon,
  Shield,
  Key,
  Brain,
  Users,
  Crown,
  Server,
  Ticket,
  // Discord icon not available in current lucide-react version
} from "lucide-react";

const DISCORD_URL = "https://discord.gg/MewpPph3aw";

const TopNav = () => {
  const { user, isAdmin, profile, signOut } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);

  const displayName = profile?.display_name || user?.email?.split("@")?.[0] || "Account";

  // Primary navigation items (simplified)
  const primaryNav = (
    <>
      {user && (
        <>
          <Link to="/app" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <MessageSquare className="h-4 w-4 mr-1.5" />Chat
            </Button>
          </Link>
          {/* Erstellen – placeholder route */}
          <Link to="/create" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <Code2 className="h-4 w-4 mr-1.5" />Erstellen
            </Button>
          </Link>
          {/* Community – groups */}
          <Link to="/groups" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <Users className="h-4 w-4 mr-1.5" />Community
            </Button>
          </Link>
          {/* Entwickeln – dashboard */}
          <Link to="/dashboard" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <Key className="h-4 w-4 mr-1.5" />Entwickeln
            </Button>
          </Link>
        </>
      )}
    </>
  );

  // "Mehr" dropdown with the rest of the links
  const moreMenu = (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm">Mehr</Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="glass-strong w-56">
        <DropdownMenuLabel className="flex items-center gap-2">
          <UserIcon className="h-4 w-4" /> Mehr Optionen
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer" className="flex items-center">
            <MessageSquare className="h-4 w-4 mr-2" />Discord
          </a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/docs" className="flex items-center"><BookOpen className="h-4 w-4 mr-2" />API Docs</Link>
        </DropdownMenuItem>
        {user && (
          <>
            <DropdownMenuItem asChild>
              <Link to="/dashboard" className="flex items-center"><Key className="h-4 w-4 mr-2" />Dashboard</Link>
            </DropdownMenuItem>
            {isAdmin && (
              <DropdownMenuItem asChild>
                <Link to="/admin" className="flex items-center"><Shield className="h-4 w-4 mr-2" />Admin</Link>
              </DropdownMenuItem>
            )}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );

  return (
    <header className="sticky top-0 z-40 glass border-b border-glass-border pt-[max(env(safe-area-inset-top),0px)]">
      <div className="container flex h-14 sm:h-16 items-center justify-between gap-2">
        <Link to={user ? "/app" : "/"} className="shrink-0" aria-label="Mythos AI"><Logo size="sm" /></Link>

        <nav className="hidden md:flex items-center gap-1">
          {primaryNav}
          {moreMenu}
        </nav>

        <div className="flex items-center gap-2">
          {user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-2 rounded-full pl-1 pr-2 sm:pr-3 py-1 hover:bg-secondary/50 transition-colors">
                  <MinecraftAvatar username={profile?.mc_username} fallback={displayName} size={32} />
                  <span className="hidden sm:inline text-sm font-medium max-w-[120px] truncate">{profile?.mc_username || displayName}</span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="glass-strong w-56">
                <DropdownMenuLabel className="flex items-center gap-2">
                  <MinecraftAvatar username={profile?.mc_username} fallback={displayName} size={36} />
                  <div className="min-w-0">
                    <div className="text-sm font-medium truncate">{displayName}</div>
                    <div className="text-xs text-muted-foreground truncate">{user.email}</div>
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => nav("/dashboard")}> <Key className="h-4 w-4 mr-2" /> Dashboard & API-Keys</DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/dashboard?tab=profile")}> <UserIcon className="h-4 w-4 mr-2" /> Profil & Skin</DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={async () => { await signOut(); nav("/"); }}> <LogOut className="h-4 w-4 mr-2" /> Logout</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <>
              <Link to="/auth"><Button variant="ghost" size="sm">Login</Button></Link>
              <Link to="/auth" className="hidden sm:inline">
                <Button size="sm" className="bg-gradient-primary text-primary-foreground hover:opacity-90">Get Started</Button>
              </Link>
            </>
          )}

          {/* Mobile menu */}
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="md:hidden h-9 w-9">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="glass-strong w-[260px] p-4 pt-[max(env(safe-area-inset-top),1rem)] pb-[max(env(safe-area-inset-bottom),1rem)]">
              <div className="mt-8 flex flex-col gap-1">
                {primaryNav}
                {moreMenu}
              </div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
};

export default TopNav;
