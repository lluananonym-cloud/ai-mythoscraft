/*
# Create video_jobs table for SkyReels-V2 video generation

1. New Tables
- `video_jobs`
  - `id` (uuid, primary key)
  - `user_id` (uuid, not null, defaults to authenticated user, references auth.users)
  - `prompt` (text, not null - the text prompt for video generation)
  - `status` (text, not null, default 'pending' - pending/processing/completed/failed)
  - `video_url` (text, nullable - URL to the generated video)
  - `error_message` (text, nullable - error details if generation failed)
  - `model` (text, not null, default 'skyreels-v2' - which model was used)
  - `aspect_ratio` (text, not null, default '16:9' - output aspect ratio)
  - `duration` (integer, not null, default 5 - requested video duration in seconds)
  - `created_at` (timestamptz, default now)
  - `updated_at` (timestamptz, default now)

2. Security
- Enable RLS on `video_jobs`.
- Owner-scoped CRUD: each authenticated user can only access their own video jobs.
- user_id defaults to auth.uid() so inserts work without the client passing it.

3. Important Notes
- This table stores video generation job records. The actual video generation
  is handled by the `video-generate` edge function which calls the SkyReels-V2
  model via NVIDIA NIM API. The edge function creates a job row, calls the API,
  and updates the row with the result.
*/

CREATE TABLE IF NOT EXISTS video_jobs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL DEFAULT auth.uid() REFERENCES auth.users(id) ON DELETE CASCADE,
  prompt text NOT NULL,
  status text NOT NULL DEFAULT 'pending',
  video_url text,
  error_message text,
  model text NOT NULL DEFAULT 'skyreels-v2',
  aspect_ratio text NOT NULL DEFAULT '16:9',
  duration integer NOT NULL DEFAULT 5,
  created_at timestamptz DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

ALTER TABLE video_jobs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "select_own_video_jobs" ON video_jobs;
CREATE POLICY "select_own_video_jobs" ON video_jobs FOR SELECT
  TO authenticated USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "insert_own_video_jobs" ON video_jobs;
CREATE POLICY "insert_own_video_jobs" ON video_jobs FOR INSERT
  TO authenticated WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "update_own_video_jobs" ON video_jobs;
CREATE POLICY "update_own_video_jobs" ON video_jobs FOR UPDATE
  TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "delete_own_video_jobs" ON video_jobs;
CREATE POLICY "delete_own_video_jobs" ON video_jobs FOR DELETE
  TO authenticated USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_video_jobs_user_created ON video_jobs (user_id, created_at DESC);
