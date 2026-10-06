// Einstellungen des Web-Chats, die nur im Browser gespeichert werden:
// Projekte, eigene Anweisungen und MCP-Connectoren.

export type McpServer = {
  id: string;
  name: string;
  url: string;
  /** optionaler Authorization-Header, z. B. "Bearer abc…" */
  auth?: string;
  enabled: boolean;
};

export type Project = {
  id: string;
  name: string;
  instructions: string;
  chatIds: string[];
  createdAt: number;
};

const KEYS = {
  mcp: "mythos.mcpServers",
  projects: "mythos.projects",
  instructions: "mythos.instructions",
};

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch { return fallback; }
}
function write(key: string, value: unknown) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* Speicher voll oder blockiert */ }
  window.dispatchEvent(new Event("mythos-prefs"));
}

export const newId = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36);

export const getMcpServers = () => read<McpServer[]>(KEYS.mcp, []);
export const setMcpServers = (s: McpServer[]) => write(KEYS.mcp, s);

export const getProjects = () => read<Project[]>(KEYS.projects, []);
export const setProjects = (p: Project[]) => write(KEYS.projects, p);
export const projectOfChat = (chatId: string | null) =>
  chatId ? getProjects().find(p => p.chatIds.includes(chatId)) : undefined;

export const getInstructions = () => read<string>(KEYS.instructions, "");
export const setInstructions = (t: string) => write(KEYS.instructions, t);
