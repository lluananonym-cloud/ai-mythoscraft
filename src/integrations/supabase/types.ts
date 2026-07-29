export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "14.5"
  }
  public: {
    Tables: {
      agent_tasks: {
        Row: {
          created_at: string
          id: string
          last_result: string | null
          last_run_at: string | null
          mode: string
          prompt: string
          recurrence: string
          schedule_at: string
          status: string
          title: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          last_result?: string | null
          last_run_at?: string | null
          mode?: string
          prompt: string
          recurrence?: string
          schedule_at: string
          status?: string
          title: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          last_result?: string | null
          last_run_at?: string | null
          mode?: string
          prompt?: string
          recurrence?: string
          schedule_at?: string
          status?: string
          title?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      ai_personas: {
        Row: {
          avatar_emoji: string | null
          created_at: string
          description: string | null
          id: string
          is_public: boolean
          name: string
          system_prompt: string
          updated_at: string
          use_count: number
          user_id: string
        }
        Insert: {
          avatar_emoji?: string | null
          created_at?: string
          description?: string | null
          id?: string
          is_public?: boolean
          name: string
          system_prompt: string
          updated_at?: string
          use_count?: number
          user_id: string
        }
        Update: {
          avatar_emoji?: string | null
          created_at?: string
          description?: string | null
          id?: string
          is_public?: boolean
          name?: string
          system_prompt?: string
          updated_at?: string
          use_count?: number
          user_id?: string
        }
        Relationships: []
      }
      ai_twins: {
        Row: {
          auto_reply_in_groups: boolean
          avatar_emoji: string | null
          created_at: string
          id: string
          is_trained: boolean
          last_trained_at: string | null
          style_summary: string | null
          tone: string | null
          training_samples: string[]
          twin_name: string
          updated_at: string
          user_id: string
          vocabulary: string[]
        }
        Insert: {
          auto_reply_in_groups?: boolean
          avatar_emoji?: string | null
          created_at?: string
          id?: string
          is_trained?: boolean
          last_trained_at?: string | null
          style_summary?: string | null
          tone?: string | null
          training_samples?: string[]
          twin_name?: string
          updated_at?: string
          user_id: string
          vocabulary?: string[]
        }
        Update: {
          auto_reply_in_groups?: boolean
          avatar_emoji?: string | null
          created_at?: string
          id?: string
          is_trained?: boolean
          last_trained_at?: string | null
          style_summary?: string | null
          tone?: string | null
          training_samples?: string[]
          twin_name?: string
          updated_at?: string
          user_id?: string
          vocabulary?: string[]
        }
        Relationships: []
      }
      api_keys: {
        Row: {
          created_at: string
          daily_limit: number
          id: string
          key_hash: string
          key_prefix: string
          last_used_at: string | null
          name: string
          revoked: boolean
          total_requests: number
          user_id: string
        }
        Insert: {
          created_at?: string
          daily_limit?: number
          id?: string
          key_hash: string
          key_prefix: string
          last_used_at?: string | null
          name: string
          revoked?: boolean
          total_requests?: number
          user_id: string
        }
        Update: {
          created_at?: string
          daily_limit?: number
          id?: string
          key_hash?: string
          key_prefix?: string
          last_used_at?: string | null
          name?: string
          revoked?: boolean
          total_requests?: number
          user_id?: string
        }
        Relationships: []
      }
      api_usage: {
        Row: {
          api_key_id: string
          created_at: string
          id: string
          input_tokens: number | null
          model: string | null
          output_tokens: number | null
          status_code: number | null
          user_id: string
        }
        Insert: {
          api_key_id: string
          created_at?: string
          id?: string
          input_tokens?: number | null
          model?: string | null
          output_tokens?: number | null
          status_code?: number | null
          user_id: string
        }
        Update: {
          api_key_id?: string
          created_at?: string
          id?: string
          input_tokens?: number | null
          model?: string | null
          output_tokens?: number | null
          status_code?: number | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "api_usage_api_key_id_fkey"
            columns: ["api_key_id"]
            isOneToOne: false
            referencedRelation: "api_keys"
            referencedColumns: ["id"]
          },
        ]
      }
      boost_codes: {
        Row: {
          bonus_requests: number | null
          code: string
          created_at: string
          created_by: string | null
          daily_limit: number
          duration_days: number | null
          expires_at: string | null
          id: string
          max_uses: number
          mode: Database["public"]["Enums"]["boost_mode"]
          note: string | null
          used_count: number
        }
        Insert: {
          bonus_requests?: number | null
          code: string
          created_at?: string
          created_by?: string | null
          daily_limit?: number
          duration_days?: number | null
          expires_at?: string | null
          id?: string
          max_uses?: number
          mode?: Database["public"]["Enums"]["boost_mode"]
          note?: string | null
          used_count?: number
        }
        Update: {
          bonus_requests?: number | null
          code?: string
          created_at?: string
          created_by?: string | null
          daily_limit?: number
          duration_days?: number | null
          expires_at?: string | null
          id?: string
          max_uses?: number
          mode?: Database["public"]["Enums"]["boost_mode"]
          note?: string | null
          used_count?: number
        }
        Relationships: []
      }
      boost_redemptions: {
        Row: {
          bonus_remaining: number | null
          code_id: string
          daily_limit: number | null
          expires_at: string | null
          id: string
          mode: Database["public"]["Enums"]["boost_mode"]
          redeemed_at: string
          user_id: string
        }
        Insert: {
          bonus_remaining?: number | null
          code_id: string
          daily_limit?: number | null
          expires_at?: string | null
          id?: string
          mode: Database["public"]["Enums"]["boost_mode"]
          redeemed_at?: string
          user_id: string
        }
        Update: {
          bonus_remaining?: number | null
          code_id?: string
          daily_limit?: number | null
          expires_at?: string | null
          id?: string
          mode?: Database["public"]["Enums"]["boost_mode"]
          redeemed_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "boost_redemptions_code_id_fkey"
            columns: ["code_id"]
            isOneToOne: false
            referencedRelation: "boost_codes"
            referencedColumns: ["id"]
          },
        ]
      }
      conversations: {
        Row: {
          created_at: string
          id: string
          identity_override: string | null
          mode: string
          persona_id: string | null
          title: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          identity_override?: string | null
          mode?: string
          persona_id?: string | null
          title?: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          identity_override?: string | null
          mode?: string
          persona_id?: string | null
          title?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      friend_groups: {
        Row: {
          ai_enabled: boolean
          ai_persona_id: string | null
          created_at: string
          description: string | null
          id: string
          invite_code: string
          name: string
          owner_id: string
          updated_at: string
        }
        Insert: {
          ai_enabled?: boolean
          ai_persona_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          invite_code?: string
          name: string
          owner_id: string
          updated_at?: string
        }
        Update: {
          ai_enabled?: boolean
          ai_persona_id?: string | null
          created_at?: string
          description?: string | null
          id?: string
          invite_code?: string
          name?: string
          owner_id?: string
          updated_at?: string
        }
        Relationships: []
      }
      group_members: {
        Row: {
          group_id: string
          id: string
          joined_at: string
          role: string
          user_id: string
        }
        Insert: {
          group_id: string
          id?: string
          joined_at?: string
          role?: string
          user_id: string
        }
        Update: {
          group_id?: string
          id?: string
          joined_at?: string
          role?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "group_members_group_id_fkey"
            columns: ["group_id"]
            isOneToOne: false
            referencedRelation: "friend_groups"
            referencedColumns: ["id"]
          },
        ]
      }
      group_messages: {
        Row: {
          content: string
          created_at: string
          group_id: string
          id: string
          metadata: Json | null
          role: string
          sender_id: string | null
          sender_name: string
        }
        Insert: {
          content: string
          created_at?: string
          group_id: string
          id?: string
          metadata?: Json | null
          role?: string
          sender_id?: string | null
          sender_name: string
        }
        Update: {
          content?: string
          created_at?: string
          group_id?: string
          id?: string
          metadata?: Json | null
          role?: string
          sender_id?: string | null
          sender_name?: string
        }
        Relationships: [
          {
            foreignKeyName: "group_messages_group_id_fkey"
            columns: ["group_id"]
            isOneToOne: false
            referencedRelation: "friend_groups"
            referencedColumns: ["id"]
          },
        ]
      }
      knowledge_articles: {
        Row: {
          body: string
          category: string
          created_at: string
          created_by: string | null
          id: string
          is_published: boolean
          title: string
          updated_at: string
        }
        Insert: {
          body: string
          category?: string
          created_at?: string
          created_by?: string | null
          id?: string
          is_published?: boolean
          title: string
          updated_at?: string
        }
        Update: {
          body?: string
          category?: string
          created_at?: string
          created_by?: string | null
          id?: string
          is_published?: boolean
          title?: string
          updated_at?: string
        }
        Relationships: []
      }
      mc_events: {
        Row: {
          ai_response: string | null
          content: string | null
          created_at: string
          event_type: string
          id: string
          metadata: Json | null
          player_name: string | null
          player_uuid: string | null
          server_id: string
        }
        Insert: {
          ai_response?: string | null
          content?: string | null
          created_at?: string
          event_type: string
          id?: string
          metadata?: Json | null
          player_name?: string | null
          player_uuid?: string | null
          server_id: string
        }
        Update: {
          ai_response?: string | null
          content?: string | null
          created_at?: string
          event_type?: string
          id?: string
          metadata?: Json | null
          player_name?: string | null
          player_uuid?: string | null
          server_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "mc_events_server_id_fkey"
            columns: ["server_id"]
            isOneToOne: false
            referencedRelation: "mc_servers"
            referencedColumns: ["id"]
          },
        ]
      }
      mc_link_codes: {
        Row: {
          claimed_at: string | null
          claimed_by: string | null
          code: string
          created_at: string
          expires_at: string
          id: string
          mc_name: string
          mc_uuid: string
          server_id: string | null
        }
        Insert: {
          claimed_at?: string | null
          claimed_by?: string | null
          code: string
          created_at?: string
          expires_at?: string
          id?: string
          mc_name: string
          mc_uuid: string
          server_id?: string | null
        }
        Update: {
          claimed_at?: string | null
          claimed_by?: string | null
          code?: string
          created_at?: string
          expires_at?: string
          id?: string
          mc_name?: string
          mc_uuid?: string
          server_id?: string | null
        }
        Relationships: [
          {
            foreignKeyName: "mc_link_codes_server_id_fkey"
            columns: ["server_id"]
            isOneToOne: false
            referencedRelation: "mc_servers"
            referencedColumns: ["id"]
          },
        ]
      }
      mc_players: {
        Row: {
          id: string
          linked_at: string
          mc_name: string
          mc_uuid: string
          server_id: string | null
          user_id: string
        }
        Insert: {
          id?: string
          linked_at?: string
          mc_name: string
          mc_uuid: string
          server_id?: string | null
          user_id: string
        }
        Update: {
          id?: string
          linked_at?: string
          mc_name?: string
          mc_uuid?: string
          server_id?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "mc_players_server_id_fkey"
            columns: ["server_id"]
            isOneToOne: false
            referencedRelation: "mc_servers"
            referencedColumns: ["id"]
          },
        ]
      }
      mc_servers: {
        Row: {
          ai_persona_id: string | null
          chat_trigger: string
          comment_on_death: boolean
          created_at: string
          events_enabled: boolean
          greet_on_join: boolean
          id: string
          ingame_chat_enabled: boolean
          key_hash: string
          key_prefix: string
          last_seen_at: string | null
          name: string
          revoked: boolean
          total_events: number
          updated_at: string
          user_id: string
        }
        Insert: {
          ai_persona_id?: string | null
          chat_trigger?: string
          comment_on_death?: boolean
          created_at?: string
          events_enabled?: boolean
          greet_on_join?: boolean
          id?: string
          ingame_chat_enabled?: boolean
          key_hash: string
          key_prefix: string
          last_seen_at?: string | null
          name: string
          revoked?: boolean
          total_events?: number
          updated_at?: string
          user_id: string
        }
        Update: {
          ai_persona_id?: string | null
          chat_trigger?: string
          comment_on_death?: boolean
          created_at?: string
          events_enabled?: boolean
          greet_on_join?: boolean
          id?: string
          ingame_chat_enabled?: boolean
          key_hash?: string
          key_prefix?: string
          last_seen_at?: string | null
          name?: string
          revoked?: boolean
          total_events?: number
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      messages: {
        Row: {
          content: string
          conversation_id: string
          created_at: string
          id: string
          metadata: Json | null
          role: string
        }
        Insert: {
          content: string
          conversation_id: string
          created_at?: string
          id?: string
          metadata?: Json | null
          role: string
        }
        Update: {
          content?: string
          conversation_id?: string
          created_at?: string
          id?: string
          metadata?: Json | null
          role?: string
        }
        Relationships: [
          {
            foreignKeyName: "messages_conversation_id_fkey"
            columns: ["conversation_id"]
            isOneToOne: false
            referencedRelation: "conversations"
            referencedColumns: ["id"]
          },
        ]
      }
      phone_chats: {
        Row: {
          ai_identity: string | null
          created_at: string
          display_name: string | null
          id: string
          last_message_at: string
          last_support_response_at: string | null
          mode: string
          phone_number: string
          unread_count: number
          updated_at: string
        }
        Insert: {
          ai_identity?: string | null
          created_at?: string
          display_name?: string | null
          id?: string
          last_message_at?: string
          last_support_response_at?: string | null
          mode?: string
          phone_number: string
          unread_count?: number
          updated_at?: string
        }
        Update: {
          ai_identity?: string | null
          created_at?: string
          display_name?: string | null
          id?: string
          last_message_at?: string
          last_support_response_at?: string | null
          mode?: string
          phone_number?: string
          unread_count?: number
          updated_at?: string
        }
        Relationships: []
      }
      phone_messages: {
        Row: {
          channel: string
          chat_id: string
          content: string
          created_at: string
          direction: string
          id: string
          metadata: Json | null
          sender: string
        }
        Insert: {
          channel?: string
          chat_id: string
          content: string
          created_at?: string
          direction: string
          id?: string
          metadata?: Json | null
          sender: string
        }
        Update: {
          channel?: string
          chat_id?: string
          content?: string
          created_at?: string
          direction?: string
          id?: string
          metadata?: Json | null
          sender?: string
        }
        Relationships: [
          {
            foreignKeyName: "phone_messages_chat_id_fkey"
            columns: ["chat_id"]
            isOneToOne: false
            referencedRelation: "phone_chats"
            referencedColumns: ["id"]
          },
        ]
      }
      pro_games: {
        Row: {
          created_at: string
          genre: string | null
          html: string
          id: string
          play_count: number
          prompt: string
          title: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          genre?: string | null
          html: string
          id?: string
          play_count?: number
          prompt: string
          title: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          genre?: string | null
          html?: string
          id?: string
          play_count?: number
          prompt?: string
          title?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      profiles: {
        Row: {
          about: string | null
          age: number | null
          ai_model: string | null
          created_at: string
          display_name: string | null
          email: string | null
          favorite_block: string | null
          id: string
          interests: string[] | null
          mc_username: string | null
          onboarded: boolean
          playstyle: string | null
          referral: string | null
          start_in_chat: boolean
          updated_at: string
          user_id: string
        }
        Insert: {
          about?: string | null
          age?: number | null
          ai_model?: string | null
          created_at?: string
          display_name?: string | null
          email?: string | null
          favorite_block?: string | null
          id?: string
          interests?: string[] | null
          mc_username?: string | null
          onboarded?: boolean
          playstyle?: string | null
          referral?: string | null
          start_in_chat?: boolean
          updated_at?: string
          user_id: string
        }
        Update: {
          about?: string | null
          age?: number | null
          ai_model?: string | null
          created_at?: string
          display_name?: string | null
          email?: string | null
          favorite_block?: string | null
          id?: string
          interests?: string[] | null
          mc_username?: string | null
          onboarded?: boolean
          playstyle?: string | null
          referral?: string | null
          start_in_chat?: boolean
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      subscriptions: {
        Row: {
          created_at: string
          expires_at: string | null
          granted_by: string | null
          id: string
          note: string | null
          source: string
          tier: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          expires_at?: string | null
          granted_by?: string | null
          id?: string
          note?: string | null
          source?: string
          tier?: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          expires_at?: string | null
          granted_by?: string | null
          id?: string
          note?: string | null
          source?: string
          tier?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      user_memories: {
        Row: {
          category: string
          content: string
          created_at: string
          id: string
          source: string
          updated_at: string
          user_id: string
        }
        Insert: {
          category?: string
          content: string
          created_at?: string
          id?: string
          source?: string
          updated_at?: string
          user_id: string
        }
        Update: {
          category?: string
          content?: string
          created_at?: string
          id?: string
          source?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: []
      }
      user_roles: {
        Row: {
          created_at: string
          id: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id?: string
        }
        Relationships: []
      }
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      has_role: {
        Args: {
          _role: Database["public"]["Enums"]["app_role"]
          _user_id: string
        }
        Returns: boolean
      }
      is_group_member: {
        Args: { _group_id: string; _user_id: string }
        Returns: boolean
      }
      is_pro: { Args: { _user_id: string }; Returns: boolean }
    }
    Enums: {
      app_role: "admin" | "user"
      boost_mode: "permanent" | "temporary" | "oneshot"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      app_role: ["admin", "user"],
      boost_mode: ["permanent", "temporary", "oneshot"],
    },
  },
} as const
