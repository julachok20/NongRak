package com.lullabyai.app.data.model

/** Parent vs doctor are separate product areas; one Supabase auth user may hold both roles. */
object ProfileRole {
    const val PARENT = "parent"
    const val DOCTOR = "doctor"
}

fun Profile.effectiveRoles(): Set<String> {
    if (roles.isNotEmpty()) return roles.map { it.lowercase() }.toSet()
    return when (accountType.lowercase()) {
        ProfileRole.DOCTOR -> setOf(ProfileRole.DOCTOR)
        else -> setOf(ProfileRole.PARENT)
    }
}

fun Profile.hasRole(role: String): Boolean = effectiveRoles().contains(role.lowercase())

fun Profile.hasDoctorRole(): Boolean = hasRole(ProfileRole.DOCTOR)

fun Profile.hasParentRole(): Boolean = hasRole(ProfileRole.PARENT)
