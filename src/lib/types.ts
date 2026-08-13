export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: SearchResult[];
  searching?: boolean;
  searchQuery?: string;
  createdAt: string;
}

export interface SearchResult {
  title: string;
  url: string;
  snippet: string;
}

export interface VideoJob {
  id: string;
  user_id: string;
  prompt: string;
  status: 'pending' | 'processing' | 'completed' | 'failed';
  video_url: string | null;
  error_message: string | null;
  model: string;
  aspect_ratio: string;
  duration: number;
  created_at: string;
  updated_at: string;
}
