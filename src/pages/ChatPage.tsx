import { useState, useRef, useEffect } from 'react';
import { Send, Globe, Sparkles } from 'lucide-react';
import type { ChatMessage } from '../lib/types';
import ChatMessageItem from '../components/ChatMessageItem';
import { useAuth } from '../lib/auth';

const FUNCTION_URL = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/ai-chat`;

export default function ChatPage() {
  const { session } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [webSearchEnabled, setWebSearchEnabled] = useState(true);
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userMsg: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: input.trim(),
      createdAt: new Date().toISOString(),
    };

    const placeholderId = crypto.randomUUID();
    const assistantMsg: ChatMessage = {
      id: placeholderId,
      role: 'assistant',
      content: '',
      searching: webSearchEnabled,
      searchQuery: input.trim(),
      sources: [],
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMsg, assistantMsg]);
    setInput('');
    setLoading(true);

    try {
      const response = await fetch(FUNCTION_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_ANON_KEY}`,
        },
        body: JSON.stringify({
          messages: [...messages, { role: 'user', content: input.trim() }],
          webSearch: webSearchEnabled,
        }),
      });

      if (!response.ok) {
        throw new Error(`Request failed (${response.status})`);
      }

      const data = await response.json();

      if (data.error) {
        throw new Error(data.error);
      }

      setMessages((prev) =>
        prev.map((m) =>
          m.id === placeholderId
            ? {
                ...m,
                content: data.content ?? '',
                sources: data.sources ?? [],
                searching: false,
              }
            : m
        )
      );
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : 'Ein Fehler ist aufgetreten';
      setMessages((prev) =>
        prev.map((m) =>
          m.id === placeholderId
            ? {
                ...m,
                content: `Entschuldigung, es ist ein Fehler aufgetreten: ${errorMsg}`,
                searching: false,
                sources: [],
              }
            : m
        )
      );
    } finally {
      setLoading(false);
      inputRef.current?.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  return (
    <div className="flex flex-col h-full">
      {/* Chat scroll area */}
      <div ref={scrollRef} className="chat-scroll px-4 py-6">
        <div className="mx-auto max-w-2xl space-y-6">
          {messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-center">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-primary-500 shadow-lg shadow-primary-500/20">
                <Sparkles className="h-8 w-8 text-white" />
              </div>
              <h2 className="text-xl font-semibold text-text-primary mb-2">AI Chat</h2>
              <p className="text-sm text-text-secondary max-w-xs">
                Stell eine Frage und die KI sucht im Web nach aktuellen Informationen, um dir zu antworten.
              </p>
            </div>
          ) : (
            messages.map((msg) => <ChatMessageItem key={msg.id} message={msg} />)
          )}
        </div>
      </div>

      {/* Input area */}
      <div className="input-anchor border-t border-border-subtle bg-bg-surface px-4 py-3">
        <div className="mx-auto max-w-2xl">
          <div className="mb-2 flex items-center gap-2">
            <button
              onClick={() => setWebSearchEnabled(!webSearchEnabled)}
              className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                webSearchEnabled
                  ? 'bg-primary-500/15 text-primary-400 border border-primary-500/30'
                  : 'bg-bg-elevated text-text-muted border border-border'
              }`}
            >
              <Globe className="h-3.5 w-3.5" />
              Websuche {webSearchEnabled ? 'an' : 'aus'}
            </button>
          </div>

          <form onSubmit={handleSubmit} className="flex items-end gap-2">
            <div className="flex-1 relative">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={1}
                placeholder="Nachricht schreiben..."
                disabled={loading}
                className="input-field resize-none max-h-32 pr-12"
                style={{ minHeight: '48px' }}
              />
            </div>
            <button
              type="submit"
              disabled={!input.trim() || loading}
              className="btn-primary h-12 w-12 !p-0 flex-shrink-0"
              aria-label="Senden"
            >
              <Send className="h-5 w-5" />
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
