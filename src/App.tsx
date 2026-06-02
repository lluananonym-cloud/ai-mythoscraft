import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/hooks/useAuth";
import RequireAuth from "@/components/RequireAuth";
import MythosBackground from "@/components/MythosBackground";
import SplashScreen from "@/components/SplashScreen";
import Landing from "./pages/Landing";
import Auth from "./pages/Auth";
import Chat from "./pages/Chat";
import Voice from "./pages/Voice";
import Dashboard from "./pages/Dashboard";
import Admin from "./pages/Admin";
import Docs from "./pages/Docs";
import Redeem from "./pages/Redeem";
import Memories from "./pages/Memories";
import Personas from "./pages/Personas";
import McServers from "./pages/McServers";
import Agents from "./pages/Agents";
import Analytics from "./pages/Analytics";
import Groups from "./pages/Groups";
import NotFound from "./pages/NotFound.tsx";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter>
        <AuthProvider>
          <SplashScreen />
          <MythosBackground />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/auth" element={<Auth />} />
            <Route path="/docs" element={<Docs />} />
            <Route path="/app" element={<RequireAuth><Chat /></RequireAuth>} />
            <Route path="/voice" element={<RequireAuth><Voice /></RequireAuth>} />
            <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>} />
            <Route path="/admin" element={<RequireAuth adminOnly><Admin /></RequireAuth>} />
            <Route path="/redeem" element={<Redeem />} />
            <Route path="/memories" element={<RequireAuth><Memories /></RequireAuth>} />
            <Route path="/personas" element={<RequireAuth><Personas /></RequireAuth>} />
            <Route path="/mc-servers" element={<RequireAuth><McServers /></RequireAuth>} />
            <Route path="/agents" element={<RequireAuth><Agents /></RequireAuth>} />
            <Route path="/groups" element={<RequireAuth><Groups /></RequireAuth>} />
            <Route path="/analytics" element={<RequireAuth><Analytics /></RequireAuth>} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
