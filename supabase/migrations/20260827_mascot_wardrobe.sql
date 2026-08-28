-- Migration: Mascot wardrobe and care-milestone XP, built ON the existing bond system
-- Date: 2026-08-27
-- Description:
--   1. mascot_bonds.equipped  — what this companion is wearing.
--   2. mascot_xp_kinds        — server-owned point values for care milestones.
--   3. mascot_xp_events       — append-only ledger, unique per (user, mascot, kind, ref).
--   4. award_mascot_xp()      — grants milestone XP; idempotent, server-priced.
--
-- Why it sits on mascot_bonds rather than a new per-user table:
--   Progression here is per (user, mascot) by design — each carer raises their own companion and
--   switching companion switches relationship. The wardrobe is part of that relationship, so it
--   belongs on the bond. An earlier attempt in this session created a parallel mascot_profiles
--   table before noticing mascot_bonds existed; that duplicate is dropped here.
--
-- Why award_mascot_xp is separate from feed_mascot:
--   feed_mascot     — the interaction loop. Small values, 20-minute cooldown, repeatable.
--   award_mascot_xp — a thing that happened to the CHILD. Server-owned points, idempotent per
--                     row, no cooldown, because a vaccine is not a tap.
--
-- Two properties the shape enforces:
--   * The CLIENT NAMES THE EVENT, THE SERVER DECIDES THE POINTS, so a modified client cannot
--     award itself a wardrobe.
--   * NO STREAKS AND XP NEVER DECREASES. A streak that breaks punishes the parent whose child was
--     in hospital. This is a keepsake of the child's journey, not a performance score.

DROP FUNCTION IF EXISTS public.award_mascot_xp(TEXT, TEXT);
DROP TABLE IF EXISTS public.mascot_profiles;

-- ---------------------------------------------------------------------------------------------
-- 1. Wardrobe on the bond
-- ---------------------------------------------------------------------------------------------
-- RLS on mascot_bonds is already FOR ALL on user_id = auth.uid(), so equipping needs no new
-- policy and no RPC.
ALTER TABLE public.mascot_bonds
    ADD COLUMN IF NOT EXISTS equipped JSONB NOT NULL DEFAULT '{}'::JSONB;

COMMENT ON COLUMN public.mascot_bonds.equipped IS 'Slot -> item id worn by this companion, e.g. {"shirt":"tee_star"}. Validated by the app against its own catalogue; unlocks are gated by mascot_level(xp).';

-- ---------------------------------------------------------------------------------------------
-- 2. Server-owned point values
-- ---------------------------------------------------------------------------------------------
-- A table rather than a CASE in the function, so values can be tuned with an UPDATE instead of
-- a migration.
CREATE TABLE IF NOT EXISTS public.mascot_xp_kinds (
    kind        TEXT PRIMARY KEY,
    points      INTEGER NOT NULL CHECK (points > 0),
    description TEXT
);

ALTER TABLE public.mascot_xp_kinds ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS mascot_xp_kinds_read ON public.mascot_xp_kinds;
CREATE POLICY mascot_xp_kinds_read ON public.mascot_xp_kinds
    FOR SELECT USING (auth.role() = 'authenticated');

-- Scaled against the existing curve (mascot_level = floor(sqrt(xp/25))+1, so L2 at 25, L3 at 100)
-- and against feeding, which gives 10. Care milestones outweigh routine interaction, so a carer
-- who looks after the child out-levels one who only plays with the toy.
DELETE FROM public.mascot_xp_kinds;
INSERT INTO public.mascot_xp_kinds (kind, points, description) VALUES
    ('child_added',        50, 'A child profile created'),
    ('vaccine_completed',  40, 'A vaccine dose recorded as given'),
    ('doctor_visit',       30, 'A completed doctor visit logged'),
    ('illness_recovered',  30, 'An illness episode marked recovered'),
    ('milestone_achieved', 25, 'A development milestone ticked'),
    ('growth_recorded',    20, 'A weight/height measurement'),
    ('daily_care',          5, 'Any care logged on a given day (once per day)');

-- ---------------------------------------------------------------------------------------------
-- 3. Ledger
-- ---------------------------------------------------------------------------------------------
DROP TABLE IF EXISTS public.mascot_xp_events;
CREATE TABLE public.mascot_xp_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    mascot     TEXT NOT NULL,
    kind       TEXT NOT NULL REFERENCES public.mascot_xp_kinds(kind),
    -- The row the event was about: a vaccine id, a growth id, or for daily_care the date.
    -- With kind and mascot this makes awarding idempotent, so a retry, a re-sync or a double tap
    -- cannot inflate a level.
    ref_id     TEXT NOT NULL,
    points     INTEGER NOT NULL CHECK (points > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, mascot, kind, ref_id)
);

COMMENT ON TABLE public.mascot_xp_events IS 'Append-only care-milestone XP ledger. UNIQUE(user_id, mascot, kind, ref_id) is what makes award_mascot_xp idempotent.';

CREATE INDEX IF NOT EXISTS mascot_xp_events_user_idx
    ON public.mascot_xp_events (user_id, mascot, created_at DESC);

ALTER TABLE public.mascot_xp_events ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS mascot_xp_events_read_own ON public.mascot_xp_events;
CREATE POLICY mascot_xp_events_read_own ON public.mascot_xp_events
    FOR SELECT USING (auth.uid() = user_id);

-- ---------------------------------------------------------------------------------------------
-- 4. Care milestones
-- ---------------------------------------------------------------------------------------------
-- Returns the same shape as feed_mascot so the UI can show one banner either way. The mascot name
-- is validated by the existing mascot_bonds CHECK (nongrak|coco|bunny|pingu).
CREATE OR REPLACE FUNCTION public.award_mascot_xp(p_mascot TEXT, p_kind TEXT, p_ref_id TEXT)
RETURNS TABLE(xp INTEGER, level INTEGER, leveled_up BOOLEAN, awarded BOOLEAN)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
    v_user   UUID    := auth.uid();
    v_points INTEGER;
    v_added  INTEGER := 0;
    v_gain   INTEGER := 0;
    v_after  INTEGER := 0;
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'not_authenticated';
    END IF;

    -- Name what was actually received: an unknown kind is a caller bug, and a silent zero would
    -- hide it until someone wondered why a level never moved.
    SELECT k.points INTO v_points FROM mascot_xp_kinds k WHERE k.kind = p_kind;
    IF v_points IS NULL THEN
        RAISE EXCEPTION 'award_mascot_xp: unknown kind %', p_kind;
    END IF;
    IF p_ref_id IS NULL OR length(trim(p_ref_id)) = 0 THEN
        RAISE EXCEPTION 'award_mascot_xp: ref_id is required for kind %', p_kind;
    END IF;
    IF p_mascot IS NULL OR length(trim(p_mascot)) = 0 THEN
        RAISE EXCEPTION 'award_mascot_xp: mascot is required';
    END IF;

    INSERT INTO mascot_xp_events (user_id, mascot, kind, ref_id, points)
    VALUES (v_user, p_mascot, p_kind, p_ref_id, v_points)
    ON CONFLICT (user_id, mascot, kind, ref_id) DO NOTHING;
    GET DIAGNOSTICS v_added = ROW_COUNT;

    v_gain := CASE WHEN v_added > 0 THEN v_points ELSE 0 END;

    INSERT INTO mascot_bonds (user_id, mascot, xp)
    VALUES (v_user, p_mascot, v_gain)
    ON CONFLICT (user_id, mascot) DO UPDATE
        SET xp = mascot_bonds.xp + v_gain;

    SELECT b.xp INTO v_after FROM mascot_bonds b
    WHERE b.user_id = v_user AND b.mascot = p_mascot;

    RETURN QUERY SELECT
        v_after,
        mascot_level(v_after),
        mascot_level(v_after) > mascot_level(GREATEST(v_after - v_gain, 0)),
        v_added > 0;
END;
$function$;

COMMENT ON FUNCTION public.award_mascot_xp(TEXT, TEXT, TEXT) IS 'Grants care-milestone XP to a companion. The caller says WHAT happened; the point value comes from mascot_xp_kinds, so a client cannot award itself an arbitrary amount. Idempotent per (user, mascot, kind, ref_id) — awarded=false means it had already been counted.';

REVOKE ALL ON FUNCTION public.award_mascot_xp(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.award_mascot_xp(TEXT, TEXT, TEXT) TO authenticated;
