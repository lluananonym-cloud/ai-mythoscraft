import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { User, Sparkles, Copy, Check } from 'lucide-react';
import { useState } from 'react';
import type { ChatMessage } from '../lib/types';
import SourcesAccordion from './SourcesAccordion';

interface ChatMessageProps {
  message: ChatMessage;
}

export default function ChatMessageItem({ message }: ChatMessageProps) {
  const [copied, setCopied] = useState(false);
  const isUser = message.role === 'user';

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'} animate-fade-in`}>
      {/* Avatar */}
      <div
        className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg ${
          isUser ? 'bg-bg-elevated' : 'bg-primary-500'
        }`}
      >
        {isUser ? (
          <User className="h-4 w-4 text-text-secondary" />
        ) : (
          <Sparkles className="h-4 w-4 text-white" />
        )}
      </div>

      {/* Message content */}
      <div className={`flex flex-col gap-1 min-w-0 max-w-[85%] ${isUser ? 'items-end' : 'items-start'}`}>
        {/* Sources (shown before the text) */}
        {!isUser && (
          <SourcesAccordion
            sources={message.sources ?? []}
            searchQuery={message.searchQuery}
            searching={message.searching}
          />
        )}

        {/* Message bubble */}
        <div
          className={`rounded-2xl px-4 py-3 ${
            isUser
              ? 'bg-primary-500 text-white rounded-tr-sm'
              : 'bg-bg-surface border border-border-subtle text-text-primary rounded-tl-sm'
          }`}
        >
          {message.searching && !message.content ? (
            <div className="flex items-center gap-2 text-sm text-text-secondary">
              <span className="flex gap-1">
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-text-muted" style={{ animationDelay: '0ms' }} />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-text-muted" style={{ animationDelay: '150ms' }} />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-text-muted" style={{ animationDelay: '300ms' }} />
              </span>
            </div>
          ) : isUser ? (
            <p className="text-sm leading-relaxed whitespace-pre-wrap">{message.content}</p>
          ) : (
            <div className="markdown-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
            </div>
          )}
        </div>

        {/* Copy button for assistant messages */}
        {!isUser && message.content && !message.searching && (
          <button
            onClick={handleCopy}
            className="inline-flex items-center gap-1 text-[10px] text-text-muted hover:text-text-secondary transition-colors"
          >
            {copied ? (
              <>
                <Check className="h-3 w-3" /> Kopiert
              </>
            ) : (
              <>
                <Copy className="h-3 w-3" /> Kopieren
              </>
            )}
          </button>
        )}
      </div>
    </div>
  );
}
