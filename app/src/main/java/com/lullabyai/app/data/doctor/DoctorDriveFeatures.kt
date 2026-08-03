package com.lullabyai.app.data.doctor

/**
 * Google Drive patient-detail backup readiness gate.
 *
 * Drive API requires Google OAuth app verification for production scopes. Until verified,
 * show "coming soon" instead of surfacing verification errors to doctors.
 */
object DoctorDriveFeatures {
    const val IS_READY = false
}
