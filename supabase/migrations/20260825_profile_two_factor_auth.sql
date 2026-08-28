-- Migration: Add Two-Factor Authentication (2FA) fields to profiles
-- Date: 2026-08-25
-- Description:
-- 1. Adds two_factor_enabled (boolean, default false) to profiles table
-- 2. Adds two_factor_enabled_at (timestamptz, default null) to profiles table

ALTER TABLE public.profiles
ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS two_factor_enabled_at TIMESTAMPTZ DEFAULT NULL;

COMMENT ON COLUMN public.profiles.two_factor_enabled IS 'Whether 2FA is active for this account. When true, new device logins require approval from an existing signed-in device.';
COMMENT ON COLUMN public.profiles.two_factor_enabled_at IS 'Timestamp when 2FA was enabled or last toggled.';
