package org.exhabere.data.backup

import org.exhabere.data.db.entity.ContextTagEntity
import org.exhabere.data.db.entity.HabitConfigEntity
import org.exhabere.data.db.entity.TriggerLogEntity

/** Portable snapshot of the single current habit and all of its local history. */
data class HabitBackup(
    val formatVersion: Int,
    val exportedAtMillis: Long,
    val habitConfig: HabitConfigEntity,
    val triggerLogs: List<TriggerLogEntity>,
    val contextTags: List<ContextTagEntity>
)
