package com.lullabyai.app.data.doctor

import com.lullabyai.app.data.model.DoctorProfile

/** True when license, clinic, and specialty are filled (Stitch 44.Regis-Google minimum). */
fun DoctorProfile?.isRegistrationComplete(): Boolean {
    if (this == null) return false
    return !licenseNumber.isNullOrBlank() &&
        !clinicName.isNullOrBlank() &&
        !specialty.isNullOrBlank()
}
