import { useState } from 'react';
import { ChevronDown, ExternalLink, Globe, Search } from 'lucide-react';
import type { SearchResult } from '../lib/types';

interface SourcesAccordionProps {
  sources: SearchResult[];
  searchQuery?: string;
  searching?: boolean;
}

export default function SourcesAccordion({ sources, searchQuery, searching }: SourcesAccordionProps) {
  const [expanded, setExpanded] = useState(false);

  if (searching) {
    return (
      <div className="mb-3 animate-fade-in">
        <div className="inline-flex items-center gap-2 rounded-full border border-primary-500/30 bg-primary-500/10 px-3 py-1.5 text-xs text-primary-400">
          <Search className="h-3.5 w-3.5 animate-pulse" />
          <span>Sucht im Web nach "{searchQuery}"</span>
        </div>
      </div>
    );
  }

  if (!sources || sources.length === 0) return null;

  return (
    <div className="mb-3 animate-fade-in">
      <button
        onClick={() => setExpanded(!expanded)}
        className="inline-flex items-center gap-2 rounded-full border border-border bg-bg-elevated px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors hover:bg-bg-hover"
      >
        <Globe className="h-3.5 w-3.5 text-accent-400" />
        <span>{sources.length} {sources.length === 1 ? 'Quelle' : 'Quellen'}</span>
        <ChevronDown
          className={`h-3.5 w-3.5 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`}
        />
      </button>

      {expanded && (
        <div className="mt-2 space-y-2 animate-slide-down">
          {sources.map((source, i) => (
            <a
              key={i}
              href={source.url}
              target="_blank"
              rel="noopener noreferrer"
              className="group flex items-start gap-3 rounded-xl border border-border-subtle bg-bg-surface p-3 transition-colors hover:border-primary-500/30 hover:bg-bg-elevated"
            >
              <div className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-lg bg-bg-elevated text-[10px] font-semibold text-text-muted">
                {i + 1}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="truncate text-sm font-medium text-text-primary group-hover:text-primary-400">
                    {source.title}
                  </span>
                  <ExternalLink className="h-3 w-3 flex-shrink-0 text-text-muted opacity-0 transition-opacity group-hover:opacity-100" />
                </div>
                <p className="mt-0.5 line-clamp-2 text-xs text-text-secondary">
                  {source.snippet}
                </p>
                <p className="mt-1 truncate text-[10px] text-text-muted">
                  {source.url}
                </p>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
