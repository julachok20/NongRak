package com.lullabyai.app.data.routine

/**
 * Who is looking after the child right now, for the routine screen's caregiver row.
 *
 * Pure: the caller resolves names, roles and timestamps first. "On duty" is answered from evidence
 * rather than assumed — an accepted handover says so outright, and failing that the person who has
 * been ticking today's routine is the one actually there. A caregiver who is merely listed is shown
 * without the "on duty now" claim, because being on the access list is not the same as being in the
 * room.
 */
object CareRoutineDuty {

    data class Person(
        val userId: String,
        val name: String,
        val role: String,
        val avatarUrl: String? = null
    )

    /** An accepted handover: minutes since midnight, null when the time is unknown. */
    data class Shift(val userId: String, val startMinute: Int?, val endMinute: Int?)

    /** One of today's ticks, reduced to "who" and "when". */
    data class Tick(val actorId: String, val minute: Int?)

    data class OnDuty(
        val person: Person,
        val shiftStart: Int? = null,
        val shiftEnd: Int? = null,
        val lastTickMinute: Int? = null,
        /** True when there is evidence this person holds the child now, not just access to them. */
        val isOnDutyNow: Boolean = false
    )

    /**
     * @param people every member of the child's circle, named.
     * @param caregiverIds those ticked as caregivers ("บุคคลที่ช่วยดูแลเด็ก").
     * @param shift the accepted handover, if one is open.
     * @param ticks today's routine records.
     */
    fun pick(
        people: List<Person>,
        caregiverIds: Set<String>,
        shift: Shift?,
        ticks: List<Tick>
    ): OnDuty? {
        val byId = people.associateBy { it.userId }
        val lastTick = ticks
            .filter { it.actorId.isNotBlank() }
            .maxByOrNull { it.minute ?: -1 }

        shift?.userId?.let(byId::get)?.let { holder ->
            return OnDuty(
                person = holder,
                shiftStart = shift.startMinute,
                shiftEnd = shift.endMinute,
                lastTickMinute = ticks.filter { it.actorId == holder.userId }.maxOfOrNull { it.minute ?: -1 }
                    ?.takeIf { it >= 0 },
                isOnDutyNow = true
            )
        }

        lastTick?.takeIf { it.actorId in caregiverIds }?.let { tick ->
            byId[tick.actorId]?.let { actor ->
                return OnDuty(person = actor, lastTickMinute = tick.minute, isOnDutyNow = true)
            }
        }

        val soleCaregiver = caregiverIds.singleOrNull()?.let(byId::get)
        return soleCaregiver?.let { OnDuty(person = it) }
    }
}
