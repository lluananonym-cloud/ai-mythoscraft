
## Mythos AI – Plan

Eine eigene KI-Plattform mit Fokus auf Support für **mythoscraft.online**, eigenem API-Key-System (Claude-kompatibel für MythosBrowse), Agent-Modus und Glassmorphism-Design.

### 🎨 Design
- **Glassmorphism** durchgehend: milchige Cards mit `backdrop-blur`, sanfte Border, Glow-Effekte
- Dark Theme als Standard, Mythos-Branding (tiefes Blau/Violett mit cyanem Akzent, Minecraft-tauglich)
- Animierter Hintergrund (Gradient-Blobs / Partikel)
- Custom Font (z. B. Inter + Space Grotesk für Headlines)
- Smooth Animations (Framer-Motion-Style via Tailwind)

### 🤖 KI-Backend (kostenlos & dauerhaft)
- **Lovable AI Gateway** mit Standard-Modell `google/gemini-3-flash-preview` (schnell, multimodal, dauerhaft mit free Tier nutzbar)
- Streaming-Antworten (Token-für-Token) via Edge Function
- System-Prompt zieht Server-Wissen live aus der DB

### 💬 Chat-Interface
- ChatGPT-artiger Chat mit Markdown-Rendering, Code-Blöcken, Streaming
- Conversation-History (gespeichert pro User)
- Mehrere Chats / Sidebar mit Verlauf, Umbenennen & Löschen
- Toggle: **Support-Modus** (Mythoscraft-Fokus) ↔ **Agent-Modus** ↔ **General**

### 🧠 Agent-Modus (mächtig)
- **Web-Suche** als Tool (für aktuelle Infos, z. B. Plugin-Docs)
- **Server-Tools**: Live-Status von mythoscraft.online (Online/Offline, Spielerzahl, MOTD via Minecraft-Server-Status-API)
- **Wissens-Lookup** in der Mythoscraft-Knowledge-Base
- Mehrstufiges Reasoning mit sichtbaren Tool-Calls ("🔧 Suche im Wiki…")

### 🔐 Auth & User-Dashboard
- Email/Passwort-Login (Lovable Cloud, Auto-Confirm an)
- Profile-Tabelle + `user_roles` (user / admin) mit RLS
- Dashboard zeigt: Account, Usage, API-Keys

### 🔑 API-Keys (Claude-kompatibel)
- User generiert Keys im Format **`sk-ant-mythos-…`** (gleiche Struktur wie Anthropic)
- Endpoint **`/v1/messages`** als Edge Function – akzeptiert Claude-API-Payload (`model`, `messages`, `max_tokens`, `system`, Streaming-Support)
- Übersetzt intern auf Lovable AI Gateway → 1:1 kompatibel mit MythosBrowse
- Rate-Limit pro Key (z. B. 100 Requests/Tag kostenlos), Usage-Tracking
- UI: Keys erstellen, benennen, widerrufen, kopieren (mit Doku-Snippet)

### 🛠️ Admin-Panel (nur für Admin-Rolle)
- **Knowledge Base CRUD**: Artikel mit Titel, Kategorie (Regeln, Commands, FAQ, Plugins…), Markdown-Body
- Wissen wird automatisch in den Support-System-Prompt eingebaut
- User-Verwaltung: Rollen ändern, API-Limits anpassen, Usage einsehen

### 📄 Seiten
1. **Landing** – Hero, Features, Glass-Cards, CTA "Try Mythos AI"
2. **Auth** – Login / Signup
3. **Chat** `/app` – Hauptinterface mit Sidebar
4. **Dashboard** `/dashboard` – API-Keys, Usage, Doku
5. **Admin** `/admin` – Knowledge Base & User Management
6. **API Docs** `/docs` – Wie man Mythos-Keys in MythosBrowse / Code nutzt

### 🗄️ Datenbank (Lovable Cloud)
- `profiles`, `user_roles`, `conversations`, `messages`
- `api_keys` (gehashter Key, Prefix sichtbar, Limits, Usage)
- `api_usage` (Request-Logs für Abrechnung)
- `knowledge_articles` (Server-Wissen)
- Alle mit RLS-Policies

### 🚀 Edge Functions
- `chat` – Streaming Chat für die Web-App
- `agent` – Agent-Mode mit Tool-Calling (Web-Suche, Server-Status, Wissen)
- `v1-messages` – Claude-kompatibler öffentlicher API-Endpoint
- `mc-server-status` – Holt Live-Status von mythoscraft.online

Nach deiner Freigabe baue ich Phase 1 (Auth + Chat + Design + Knowledge Base + erste API-Keys) und danach den Agent-Mode + Claude-kompatiblen Endpoint.
