import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { MessageSquare, Video, LogOut, Sparkles } from 'lucide-react';
import { useAuth } from '../lib/auth';
import { useKeyboardHandler } from '../lib/useKeyboardHandler';

export default function AppLayout() {
  const { signOut, user } = useAuth();
  const navigate = useNavigate();

  useKeyboardHandler();

  const handleSignOut = async () => {
    await signOut();
    navigate('/auth');
  };

  return (
    <div className="app-shell">
      {/* Top bar */}
      <header className="flex items-center justify-between px-4 py-3 border-b border-border-subtle flex-shrink-0">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-500">
            <Sparkles className="h-4 w-4 text-white" />
          </div>
          <span className="font-semibold text-text-primary">AI Studio</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-text-muted hidden sm:inline truncate max-w-[150px]">
            {user?.email}
          </span>
          <button onClick={handleSignOut} className="btn-ghost" aria-label="Sign out">
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>

      {/* Main content area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Desktop sidebar */}
        <nav className="hidden md:flex flex-col gap-1 p-3 border-r border-border-subtle w-56 flex-shrink-0">
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-text-secondary hover:bg-bg-elevated hover:text-text-primary'
              }`
            }
          >
            <MessageSquare className="h-4 w-4" />
            Chat
          </NavLink>
          <NavLink
            to="/video"
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-text-secondary hover:bg-bg-elevated hover:text-text-primary'
              }`
            }
          >
            <Video className="h-4 w-4" />
            Video
          </NavLink>
        </nav>

        {/* Page content */}
        <main className="flex-1 flex flex-col overflow-hidden min-w-0">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom nav */}
      <nav className="md:hidden flex items-center justify-around border-t border-border-subtle py-2 flex-shrink-0 bg-bg-surface">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            `flex flex-col items-center gap-0.5 px-6 py-1 rounded-lg transition-colors ${
              isActive ? 'text-primary-400' : 'text-text-muted'
            }`
          }
        >
          <MessageSquare className="h-5 w-5" />
          <span className="text-[10px] font-medium">Chat</span>
        </NavLink>
        <NavLink
          to="/video"
          className={({ isActive }) =>
            `flex flex-col items-center gap-0.5 px-6 py-1 rounded-lg transition-colors ${
              isActive ? 'text-primary-400' : 'text-text-muted'
            }`
          }
        >
          <Video className="h-5 w-5" />
          <span className="text-[10px] font-medium">Video</span>
        </NavLink>
      </nav>
    </div>
  );
}
