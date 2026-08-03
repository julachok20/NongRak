-- Child diary / special memories (#10) — applied remotely as add_diary_memories
CREATE TABLE IF NOT EXISTS public.diary_memories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  child_id uuid NOT NULL REFERENCES public.children(id) ON DELETE CASCADE,
  title text,
  description text,
  memory_date date NOT NULL DEFAULT CURRENT_DATE,
  media_type text NOT NULL DEFAULT 'photo'
    CHECK (media_type IN ('photo', 'video', 'audio', 'note')),
  media_path text,
  mime_type text,
  duration_sec integer,
  milestone_tag text,
  is_favorite boolean NOT NULL DEFAULT false,
  is_milestone boolean NOT NULL DEFAULT false,
  category text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz
);

CREATE INDEX IF NOT EXISTS diary_memories_child_date_idx
  ON public.diary_memories (child_id, memory_date DESC);

ALTER TABLE public.diary_memories ENABLE ROW LEVEL SECURITY;

CREATE POLICY diary_memories_select ON public.diary_memories
  FOR SELECT TO authenticated
  USING (public.is_child_member(child_id));
CREATE POLICY diary_memories_insert ON public.diary_memories
  FOR INSERT TO authenticated
  WITH CHECK (public.is_child_member(child_id));
CREATE POLICY diary_memories_update ON public.diary_memories
  FOR UPDATE TO authenticated
  USING (public.is_child_member(child_id))
  WITH CHECK (public.is_child_member(child_id));
CREATE POLICY diary_memories_delete ON public.diary_memories
  FOR DELETE TO authenticated
  USING (public.is_child_member(child_id));

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'diary-media', 'diary-media', false, 10485760,
  ARRAY[
    'image/jpeg', 'image/png', 'image/webp',
    'video/mp4', 'video/quicktime',
    'audio/mpeg', 'audio/mp4', 'audio/aac', 'audio/wav',
    'application/pdf'
  ]
)
ON CONFLICT (id) DO NOTHING;
