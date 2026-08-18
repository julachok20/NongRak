-- =====================================================================
-- Migration: 20260818_refrigerator_ingredients.sql
-- Smart Refrigerator & Ingredient Scanner for AI Nutrition
-- 1. Table: fridge_ingredients (per-child available home ingredients)
-- 2. RPC: sync_fridge_ingredients (copy ingredients to sibling children)
-- =====================================================================

CREATE TABLE IF NOT EXISTS public.fridge_ingredients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  child_id uuid NOT NULL REFERENCES public.children(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id),
  ingredient_name text NOT NULL,
  category text NOT NULL DEFAULT 'general', -- 'vegetable', 'fruit', 'meat', 'dairy', 'grain', 'general'
  quantity text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.fridge_ingredients ENABLE ROW LEVEL SECURITY;

CREATE POLICY "fridge_ingredients_select"
ON public.fridge_ingredients FOR SELECT
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "fridge_ingredients_insert"
ON public.fridge_ingredients FOR INSERT
TO authenticated
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "fridge_ingredients_update"
ON public.fridge_ingredients FOR UPDATE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
)
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "fridge_ingredients_delete"
ON public.fridge_ingredients FOR DELETE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

-- RPC to copy/sync ingredients from one child to another sibling child
CREATE OR REPLACE FUNCTION public.sync_fridge_ingredients(
  p_source_child_id uuid,
  p_target_child_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid := auth.uid();
BEGIN
  -- Verify caller has access to both source and target child
  IF NOT EXISTS (
    SELECT 1 FROM public.children WHERE id = p_source_child_id AND user_id = v_user_id
    UNION
    SELECT 1 FROM public.child_members WHERE child_id = p_source_child_id AND user_id = v_user_id
  ) OR NOT EXISTS (
    SELECT 1 FROM public.children WHERE id = p_target_child_id AND user_id = v_user_id
    UNION
    SELECT 1 FROM public.child_members WHERE child_id = p_target_child_id AND user_id = v_user_id
  ) THEN
    RAISE EXCEPTION 'access_denied';
  END IF;

  -- Insert distinct ingredients from source to target that don't already exist
  INSERT INTO public.fridge_ingredients (child_id, user_id, ingredient_name, category, quantity)
  SELECT p_target_child_id, v_user_id, s.ingredient_name, s.category, s.quantity
  FROM public.fridge_ingredients s
  WHERE s.child_id = p_source_child_id
    AND NOT EXISTS (
      SELECT 1 FROM public.fridge_ingredients t
      WHERE t.child_id = p_target_child_id AND LOWER(TRIM(t.ingredient_name)) = LOWER(TRIM(s.ingredient_name))
    );
END;
$$;

REVOKE ALL ON FUNCTION public.sync_fridge_ingredients(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.sync_fridge_ingredients(uuid, uuid) TO authenticated;
