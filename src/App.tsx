import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/hooks/useAuth";
import RequireAuth from "@/components/RequireAuth";
import MythosBackground from "@/components/MythosBackground";
import SplashScreen from "@/components/SplashScreen";
import Home from "./pages/Home";
import Auth from "./pages/Auth";
import ResetPassword from "./pages/ResetPassword";
import Create from "./pages/Create";
import Chat from "./pages/Chat";
import Voice from "./pages/Voice";
import Dashboard from "./pages/Dashboard";
import Admin from "./pages/Admin";
import Docs from "./pages/Docs";
import Downloads from "./pages/Downloads";
import Redeem from "./pages/Redeem";
import CliAuth from "./pages/CliAuth";
import CodeControl from "./pages/CodeControl";
import Memories from "./pages/Memories";
import Personas from "./pages/Personas";
import McServers from "./pages/McServers";
import Agents from "./pages/Agents";
import Analytics from "./pages/Analytics";
import Groups from "./pages/Groups";
import Twin from "./pages/Twin";
import Games from "./pages/Games";
import Try from "./pages/Try";
import Werbung from "./pages/Werbung";
import WerbungVorschau from "./pages/WerbungVorschau";
import BrowserAgent from "./pages/BrowserAgent";
import Onboarding from "./pages/Onboarding";
import NotFound from "./pages/NotFound.tsx";
import Impressum from "./pages/Impressum";
import Datenschutz from "./pages/Datenschutz";
import Nutzungsbedingungen from "./pages/Nutzungsbedingungen";
import Cookie from "./pages/Cookie";
import KIRegeln from "./pages/KIRegeln";
import Entdecken from "./pages/labs/Entdecken";
import Arena from "./pages/labs/Arena";
import Welt from "./pages/labs/Welt";
import FotoApp from "./pages/labs/FotoApp";
import KiTeam from "./pages/labs/KiTeam";
import Rueckblick from "./pages/labs/Rueckblick";
import Marktplatz from "./pages/labs/Marktplatz";

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
            <Route path="/" element={<Home />} />
            <Route path="/auth" element={<Auth />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/forgot-password" element={<ResetPassword />} />
            <Route path="/onboarding" element={<RequireAuth skipOnboarding><Onboarding /></RequireAuth>} />
            <Route path="/try" element={<Try />} />
            <Route path="/werbung" element={<Werbung />} />
            <Route path="/werbung/vorschau/:id" element={<RequireAuth><WerbungVorschau /></RequireAuth>} />
            <Route path="/docs" element={<Docs />} />
            <Route path="/downloads" element={<Downloads />} />
            <Route path="/app/*" element={<RequireAuth><Chat /></RequireAuth>} />
            <Route path="/create" element={<RequireAuth><Create /></RequireAuth>} />
            <Route path="/voice" element={<RequireAuth><Voice /></RequireAuth>} />
            <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>} />
            <Route path="/admin" element={<RequireAuth adminOnly><Admin /></RequireAuth>} />
            <Route path="/redeem" element={<Redeem />} />
            <Route path="/cli-auth" element={<CliAuth />} />
            <Route path="/code" element={<CodeControl />} />
            <Route path="/memories" element={<RequireAuth><Memories /></RequireAuth>} />
            <Route path="/personas" element={<RequireAuth><Personas /></RequireAuth>} />
            <Route path="/mc-servers" element={<RequireAuth><McServers /></RequireAuth>} />
            <Route path="/agents" element={<RequireAuth><Agents /></RequireAuth>} />
            <Route path="/browser" element={<RequireAuth><BrowserAgent /></RequireAuth>} />
            <Route path="/groups" element={<RequireAuth><Groups /></RequireAuth>} />
            <Route path="/twin" element={<RequireAuth><Twin /></RequireAuth>} />
            <Route path="/games" element={<RequireAuth><Games /></RequireAuth>} />
            <Route path="/analytics" element={<RequireAuth><Analytics /></RequireAuth>} />
            <Route path="/entdecken" element={<RequireAuth><Entdecken /></RequireAuth>} />
            <Route path="/arena" element={<RequireAuth><Arena /></RequireAuth>} />
            <Route path="/welt" element={<RequireAuth><Welt /></RequireAuth>} />
            <Route path="/foto-app" element={<RequireAuth><FotoApp /></RequireAuth>} />
            <Route path="/ki-team" element={<RequireAuth><KiTeam /></RequireAuth>} />
            <Route path="/rueckblick" element={<RequireAuth><Rueckblick /></RequireAuth>} />
            <Route path="/marktplatz" element={<RequireAuth><Marktplatz /></RequireAuth>} />
            <Route path="/impressum" element={<Impressum />} />
            <Route path="/datenschutz" element={<Datenschutz />} />
            <Route path="/nutzungsbedingungen" element={<Nutzungsbedingungen />} />
            <Route path="/cookie" element={<Cookie />} />
            <Route path="/ki-regeln" element={<KIRegeln />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
