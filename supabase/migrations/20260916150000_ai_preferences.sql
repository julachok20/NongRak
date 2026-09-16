-- Migration: 20260916150000_ai_preferences.sql  (applied 2026-09-16)
--
-- Settings › ตั้งค่า AI เอง (Custom AI Preferences): the parent picks NongRak's tone and how long
-- its answers are. Stored next to the learnt note in parent_ai_style (same RLS: own row only).
-- Null = automatic (the learnt note decides). ai-chat applies a choice above the learnt note.

alter table public.parent_ai_style
  add column if not exists tone text
    check (tone in ('warm', 'cheerful', 'professional')),
  add column if not exists answer_length text
    check (answer_length in ('short', 'balanced', 'detailed'));
