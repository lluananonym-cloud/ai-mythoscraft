import { lazy, Suspense, type ComponentType } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/hooks/useAuth";
import RequireAuth from "@/components/RequireAuth";
import MythosBackground from "@/components/MythosBackground";
import SplashScreen from "@/components/SplashScreen";
import { Loader2 } from "lucide-react";
import Home from "./pages/Home";

/** Lädt eine Seite erst bei Bedarf. Fehlt nach einem Update eine alte Datei, wird die Seite einmal neu geladen. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- lazy() erwartet beliebige Props
function lazyPage<T extends { default: ComponentType<any> }>(load: () => Promise<T>) {
  return lazy(() => load().catch((e) => {
    if (!sessionStorage.getItem("mythos.chunk-reload")) {
      sessionStorage.setItem("mythos.chunk-reload", "1");
      window.location.reload();
    }
    throw e;
  }).then((m) => { sessionStorage.removeItem("mythos.chunk-reload"); return m; }));
}
const Auth = lazyPage(() => import("./pages/Auth"));
const ResetPassword = lazyPage(() => import("./pages/ResetPassword"));
const Create = lazyPage(() => import("./pages/Create"));
const Chat = lazyPage(() => import("./pages/Chat"));
const Voice = lazyPage(() => import("./pages/Voice"));
const Dashboard = lazyPage(() => import("./pages/Dashboard"));
const Admin = lazyPage(() => import("./pages/Admin"));
const Docs = lazyPage(() => import("./pages/Docs"));
const Downloads = lazyPage(() => import("./pages/Downloads"));
const Redeem = lazyPage(() => import("./pages/Redeem"));
const CliAuth = lazyPage(() => import("./pages/CliAuth"));
const CodeControl = lazyPage(() => import("./pages/CodeControl"));
const Memories = lazyPage(() => import("./pages/Memories"));
const Personas = lazyPage(() => import("./pages/Personas"));
const McServers = lazyPage(() => import("./pages/McServers"));
const Agents = lazyPage(() => import("./pages/Agents"));
const Analytics = lazyPage(() => import("./pages/Analytics"));
const Groups = lazyPage(() => import("./pages/Groups"));
const Twin = lazyPage(() => import("./pages/Twin"));
const Games = lazyPage(() => import("./pages/Games"));
const Try = lazyPage(() => import("./pages/Try"));
const Werbung = lazyPage(() => import("./pages/Werbung"));
const WerbungVorschau = lazyPage(() => import("./pages/WerbungVorschau"));
const BrowserAgent = lazyPage(() => import("./pages/BrowserAgent"));
const Onboarding = lazyPage(() => import("./pages/Onboarding"));
const NotFound = lazyPage(() => import("./pages/NotFound.tsx"));
const Impressum = lazyPage(() => import("./pages/Impressum"));
const Datenschutz = lazyPage(() => import("./pages/Datenschutz"));
const Nutzungsbedingungen = lazyPage(() => import("./pages/Nutzungsbedingungen"));
const Cookie = lazyPage(() => import("./pages/Cookie"));
const KIRegeln = lazyPage(() => import("./pages/KIRegeln"));
const Entdecken = lazyPage(() => import("./pages/labs/Entdecken"));
const Arena = lazyPage(() => import("./pages/labs/Arena"));
const Welt = lazyPage(() => import("./pages/labs/Welt"));
const FotoApp = lazyPage(() => import("./pages/labs/FotoApp"));
const KiTeam = lazyPage(() => import("./pages/labs/KiTeam"));
const Rueckblick = lazyPage(() => import("./pages/labs/Rueckblick"));
const Marktplatz = lazyPage(() => import("./pages/labs/Marktplatz"));

const queryClient = new QueryClient();

// Seiten werden erst geladen, wenn man sie öffnet: die Startseite kommt so viel schneller.
const PageLoading = () => (
  <div className="min-h-[100dvh] flex items-center justify-center"><Loader2 className="h-7 w-7 animate-spin text-muted-foreground" /></div>
);

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter>
        <AuthProvider>
          <SplashScreen />
          <MythosBackground />
          <Suspense fallback={<PageLoading />}>
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
          </Suspense>
        </AuthProvider>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;
