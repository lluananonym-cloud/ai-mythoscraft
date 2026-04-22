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
  LogOut, MessageSquare, Key, Shield, BookOpen, Menu, User as UserIcon, Ticket,
  Brain, Drama, Server,
} from "lucide-react";

const DISCORD_URL = "https://discord.gg/MewpPph3aw";

const DiscordIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
    <path d="M20.317 4.369A19.79 19.79 0 0 0 16.558 3a14.7 14.7 0 0 0-.665 1.36 18.27 18.27 0 0 0-5.487 0A14.7 14.7 0 0 0 9.74 3a19.74 19.74 0 0 0-3.76 1.37C2.45 9.59 1.49 14.66 1.97 19.66a19.93 19.93 0 0 0 6.04 3.05c.49-.66.92-1.36 1.29-2.1a13 13 0 0 1-2.03-.97c.17-.13.34-.26.5-.39a14.27 14.27 0 0 0 12.46 0c.17.14.34.27.5.4a13 13 0 0 1-2.04.97c.37.74.8 1.44 1.29 2.1a19.92 19.92 0 0 0 6.04-3.05c.57-5.79-.97-10.81-4.04-15.29ZM8.52 16.36c-1.2 0-2.18-1.1-2.18-2.45s.96-2.45 2.18-2.45c1.22 0 2.2 1.11 2.18 2.45 0 1.35-.97 2.45-2.18 2.45Zm6.96 0c-1.2 0-2.18-1.1-2.18-2.45s.96-2.45 2.18-2.45c1.22 0 2.2 1.11 2.18 2.45 0 1.35-.96 2.45-2.18 2.45Z"/>
  </svg>
);

const TopNav = () => {
  const { user, isAdmin, profile, signOut } = useAuth();
  const nav = useNavigate();
  const [open, setOpen] = useState(false);

  const displayName = profile?.display_name || user?.email?.split("@")[0] || "Account";

  const navLinks = (
    <>
      <Link to="/docs" onClick={() => setOpen(false)}>
        <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
          <BookOpen className="h-4 w-4 mr-1.5" />API Docs
        </Button>
      </Link>
      <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer" onClick={() => setOpen(false)}>
        <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
          <DiscordIcon className="h-4 w-4 mr-1.5" />Discord
        </Button>
      </a>
      {user && (
        <>
          <Link to="/app" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <MessageSquare className="h-4 w-4 mr-1.5" />Chat
            </Button>
          </Link>
          <Link to="/dashboard" onClick={() => setOpen(false)}>
            <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
              <Key className="h-4 w-4 mr-1.5" />Dashboard
            </Button>
          </Link>
          {isAdmin && (
            <Link to="/admin" onClick={() => setOpen(false)}>
              <Button variant="ghost" size="sm" className="w-full justify-start md:w-auto">
                <Shield className="h-4 w-4 mr-1.5" />Admin
              </Button>
            </Link>
          )}
        </>
      )}
    </>
  );

  return (
    <header className="sticky top-0 z-40 glass border-b border-glass-border">
      <div className="container flex h-14 sm:h-16 items-center justify-between gap-2">
        <Link to="/" className="shrink-0"><Logo size="sm" /></Link>

        <nav className="hidden md:flex items-center gap-1">{navLinks}</nav>

        <div className="flex items-center gap-2">
          {user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-2 rounded-full pl-1 pr-2 sm:pr-3 py-1 hover:bg-secondary/50 transition-colors">
                  <MinecraftAvatar
                    username={profile?.mc_username}
                    fallback={displayName}
                    size={32}
                  />
                  <span className="hidden sm:inline text-sm font-medium max-w-[120px] truncate">
                    {profile?.mc_username || displayName}
                  </span>
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
                <DropdownMenuItem onClick={() => nav("/dashboard")}>
                  <Key className="h-4 w-4 mr-2" /> Dashboard & API-Keys
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/dashboard?tab=profile")}>
                  <UserIcon className="h-4 w-4 mr-2" /> Profil & Skin
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/memories")}>
                  <Brain className="h-4 w-4 mr-2" /> Memories
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/personas")}>
                  <Drama className="h-4 w-4 mr-2" /> Personas
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/mc-servers")}>
                  <Server className="h-4 w-4 mr-2" /> Minecraft-Server
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => nav("/redeem")}>
                  <Ticket className="h-4 w-4 mr-2" /> Boost Code einlösen
                </DropdownMenuItem>
                {isAdmin && (
                  <DropdownMenuItem onClick={() => nav("/admin")}>
                    <Shield className="h-4 w-4 mr-2" /> Admin Panel
                  </DropdownMenuItem>
                )}
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={async () => { await signOut(); nav("/"); }}>
                  <LogOut className="h-4 w-4 mr-2" /> Logout
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <>
              <Link to="/auth"><Button variant="ghost" size="sm">Login</Button></Link>
              <Link to="/auth" className="hidden sm:inline">
                <Button size="sm" className="bg-gradient-primary text-primary-foreground hover:opacity-90">
                  Get Started
                </Button>
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
            <SheetContent side="right" className="glass-strong w-[260px] p-4">
              <div className="mt-8 flex flex-col gap-1">{navLinks}</div>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
};

export default TopNav;
