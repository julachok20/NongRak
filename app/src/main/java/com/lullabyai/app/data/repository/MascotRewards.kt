package com.lullabyai.app.data.repository

import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Care milestones, credited to whichever companion the carer is raising.
 *
 * One place rather than a call at every write site, because the rules are the same everywhere:
 * find the active companion, name what happened, and never let the answer matter. Bond failures
 * are swallowed on purpose — the same rule the feeding path already follows. The vaccine is
 * already recorded by the time this runs, and a toy must not break a tool.
 *
 * Safe to call every time the thing is observed rather than only the first time: the RPC is
 * idempotent per (user, mascot, kind, refId), so a retry, a re-sync or a second tap counts once.
 */
object MascotRewards {

    // Detached on purpose: awarding must not extend the lifetime of the caller's scope, and must
    // not be cancelled when a screen closes right after saving.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo = MascotBondRepository()

    /**
     * Credits [kind] against [refId], off the caller's thread and without a result.
     *
     * @param refId the row the milestone is about — a vaccine id, a growth id, a date. This is
     *   the idempotency key, so it must identify the EVENT, never the moment: passing a timestamp
     *   would award the same vaccine again on every sync.
     */
    fun award(kind: XpKind, refId: String?) {
        if (refId.isNullOrBlank()) return
        scope.launch {
            runCatching {
                val app = NongRakApp.instance
                // No companion chosen yet (onboarding unfinished) means there is nothing to
                // raise. Not an error, just nothing to do.
                val mascot = SettingsManager(app).selectedMascot.first() ?: return@runCatching
                repo.award(mascot, kind, refId)
            }
        }
    }
}
