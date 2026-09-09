@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.handoff.*
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.viewmodel.HandoffViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun handoffLabel(value: String, thai: Boolean): String = if (!thai) when(value) {
    "pending" -> "Waiting for acknowledgment"; "active" -> "Care acknowledged"
    "accept" -> "I'm taking over"; "complete" -> "Finish and send summary"
    "acknowledge" -> "Acknowledge return"; "closed" -> "Return acknowledged"
    else -> value.replaceFirstChar { it.uppercase() }
} else when(value) {
    "pending" -> "รอผู้ดูแลตอบรับ"; "active" -> "รับช่วงดูแลแล้ว"
    "accept" -> "รับช่วงดูแลแล้ว"; "decline" -> "ไม่สะดวกรับช่วง"
    "declined" -> "ปฏิเสธแล้ว"; "cancel" -> "ยกเลิกคำขอ"; "cancelled" -> "ยกเลิกแล้ว"
    "complete" -> "จบการดูแลและส่งสรุป"; "completed" -> "รอรับทราบสรุป"
    "acknowledge" -> "รับทราบสรุปแล้ว"; "closed" -> "รับทราบสรุปแล้ว"
    else -> value
}

@Composable
fun HandoffScreen(childId: String, navController: NavController, vm: HandoffViewModel = viewModel()) {
    val thai = LocalStrings.current.isThai
    val s by vm.state.collectAsState()
    var compose by rememberSaveable(childId) { mutableStateOf(false) }
    var review by rememberSaveable(childId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun navigateForChild(route:String) {
        com.lullabyai.app.data.settings.ChildSelection.set(childId)
        navController.navigate(route)
    }
    LaunchedEffect(childId) {
        vm.load(childId)
        while (true) { delay(15000); vm.refresh() }
    }
    Scaffold(topBar = { LullabyTopBar(
        title = if(thai) "ส่งต่อการดูแล" else "Caregiver handoff",
        onBack = { navController.popBackStack() }, navController = navController
    ) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(s.childName, style = MaterialTheme.typography.headlineSmall)
                Text(if(thai) "คำขอจะยังไม่ถือว่ารับช่วง จนกว่าผู้ดูแลตอบรับ" else "Care is not acknowledged until the recipient accepts.")
                Button(onClick = { compose = !compose }, enabled = !s.busy && s.people.isNotEmpty()) {
                    Text(if(thai) "ส่งต่อการดูแลใหม่" else "New handoff")
                }
                TextButton(onClick = { scope.launchRefresh(vm) }) { Text(if(thai) "รีเฟรช" else "Refresh") }
            }
            if(s.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            s.context?.let { context -> item { HandoffContextCard(context,thai) } }
            s.error?.let { error -> item {
                Text((if(thai) "ดำเนินการไม่สำเร็จ ข้อมูลตอบกลับ: " else "Request failed. Received: ") + error,
                    color = MaterialTheme.colorScheme.error)
            } }
            if(compose) item {
                val d = s.draft
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if(thai) "เลือกผู้ดูแล" else "Choose a caregiver")
                    s.people.filter { it.userId != s.userId }.forEach { person ->
                        Row { RadioButton(selected = d.recipient == person.userId, enabled = !s.busy,
                            onClick = { vm.draft(d.copy(recipient = person.userId)) })
                            Text(person.displayName, modifier = Modifier.padding(top=12.dp)) }
                    }
                    OutlinedTextField(value=d.notes, onValueChange={ if(it.length<=4000) vm.draft(d.copy(notes=it)) }, enabled=!s.busy,
                        label={ Text(if(thai) "สิ่งที่ต้องรู้ / คำแนะนำ" else "Important notes / instructions") }, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=d.checklist, onValueChange={ vm.draft(d.copy(checklist=it)) }, enabled=!s.busy,
                        label={ Text(if(thai) "รายการสิ่งที่ต้องทำ (บรรทัดละข้อ)" else "Checklist (one task per line)") }, modifier=Modifier.fillMaxWidth())
                    Text(if(thai) "เวลากลับโดยประมาณ (ไม่บังคับ)" else "Expected return (optional)")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(1L,2L,4L).forEach { hours -> TextButton(enabled=!s.busy,onClick={
                            vm.draft(d.copy(expectedEnd=java.time.OffsetDateTime.now().plusHours(hours).toString()))
                        }) { Text("$hours " + if(thai) "ชม." else "hours") } }
                        TextButton(onClick={vm.draft(d.copy(expectedEnd=""))},enabled=!s.busy) { Text(if(thai) "ล้าง" else "Clear") }
                    }
                    if(d.expectedEnd.isNotBlank()) Text(displayHandoffTime(d.expectedEnd))
                    Text(if(thai) "บันทึกฉบับร่างบนอุปกรณ์นี้ การส่งต้องเชื่อมต่ออินเทอร์เน็ต" else "Draft saved on this device. Sending requires a connection.")
                    Button(onClick={review=true}, enabled=!s.busy && d.recipient.isNotBlank()) { Text(if(thai) "ตรวจสอบก่อนส่ง" else "Review handoff") }
                } }
            }
            item { Text(if(thai) "การส่งต่อและประวัติ" else "Handoffs and history",style=MaterialTheme.typography.titleLarge) }
            items(s.handoffs,key={it.id}) { h ->
                val note = s.returnNotes[h.id].orEmpty()
                var confirm by remember { mutableStateOf<String?>(null) }
                Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(handoffLabel(h.status,thai),style=MaterialTheme.typography.titleMedium)
                    Text((s.people.find{it.userId==h.senderId}?.displayName ?: "—") + " → " + (s.people.find{it.userId==h.recipientId}?.displayName ?: "—"))
                    Text(displayHandoffTime(h.createdAt))
                    h.acceptedAt?.let { Text((if(thai) "รับช่วงเมื่อ " else "Accepted: ")+displayHandoffTime(it)) }
                    h.expectedEnd?.let { end ->
                        val overdue = runCatching { java.time.OffsetDateTime.parse(end).toInstant().isBefore(java.time.Instant.now()) }.getOrDefault(false)
                        Text((if(thai) "เวลากลับโดยประมาณ: " else "Expected return: ")+displayHandoffTime(end))
                        if(overdue && h.status=="active") Text(if(thai) "เลยเวลาที่คาดไว้แล้ว ยังไม่ได้จบการดูแล" else "Expected return has passed. Care has not been marked complete.")
                    }
                    Text(h.instructions.ifBlank { if(thai) "ไม่มีคำแนะนำเพิ่มเติม" else "No additional instructions" })
                    h.tasks.forEachIndexed { index,task -> Row {
                        Checkbox(checked=task.done,enabled=!s.busy && h.status=="active" && s.userId==h.recipientId,
                            onCheckedChange={vm.act(h,"task",index=index,done=it)})
                        Text(task.text,Modifier.padding(top=12.dp))
                    } }
                    Text(if(thai) "เช็กรายการไม่ได้บันทึกการกินหรือการนอน" else "Checklist completion does not create a care record.",style=MaterialTheme.typography.bodySmall)
                    if(h.status=="active") {
                        Row { TextButton(onClick={navigateForChild(Screen.MilkRecord.route)}) { Text(if(thai) "นม" else "Milk") }
                            TextButton(onClick={navigateForChild(Screen.SleepTracker.route)}) { Text(if(thai) "นอน" else "Sleep") }
                            TextButton(onClick={navigateForChild(Screen.WasteRecord.route)}) { Text(if(thai) "ขับถ่าย" else "Diaper") } }
                    }
                    TextButton(onClick={navigateForChild(Screen.CareChat.route(if(s.userId==h.senderId) h.recipientId else h.senderId))}) {
                        Text(if(thai) "แชทกับผู้ดูแล" else "Message caregiver")
                    }
                    if(h.returnNote.isNotBlank()) Text((if(thai) "สรุป / หมายเหตุ: " else "Return summary / note: ")+h.returnNote)
                    TextButton(onClick={vm.loadSummary(h)}) { Text(if(thai) "ดูบันทึกช่วงที่ดูแล" else "Review recorded care for this handoff") }
                    s.contexts[h.id]?.let { HandoffContextCard(it,thai) }
                    if(handoffActions(h,s.userId).any { it in listOf("complete","decline") }) {
                        OutlinedTextField(value=note,onValueChange={if(it.length<=4000)vm.returnNote(h.id,it)},enabled=!s.busy,
                            label={Text(if(thai) "ข้อสังเกต / สิ่งที่ต้องติดตาม" else "Observations / follow-up")},modifier=Modifier.fillMaxWidth())
                    }
                    handoffActions(h,s.userId).forEach { action ->
                        Button(onClick={confirm=action},enabled=!s.busy) { Text(handoffLabel(action,thai)) }
                    }
                } }
                confirm?.let { action -> AlertDialog(onDismissRequest={confirm=null},title={Text(handoffLabel(action,thai))},
                    text={Text(if(note.isNotBlank())note else if(thai) "ยืนยันการเปลี่ยนสถานะนี้?" else "Confirm this status change?")},
                    confirmButton={TextButton(onClick={vm.act(h,action,note);confirm=null}){Text(if(thai)"ยืนยัน" else "Confirm")}},
                    dismissButton={TextButton(onClick={confirm=null}){Text(if(thai)"กลับ" else "Back")}}) }
            }
        }
    }
    if(review) AlertDialog(onDismissRequest={review=false},title={Text(if(thai)"ตรวจสอบการส่งต่อ" else "Review handoff")},
        text={Column(Modifier.verticalScroll(rememberScrollState())) { Text(s.childName); Text(s.people.find{it.userId==s.draft.recipient}?.displayName.orEmpty()); Text(s.draft.notes); Text(s.draft.checklist); if(s.draft.expectedEnd.isNotBlank()) Text(displayHandoffTime(s.draft.expectedEnd)) }},
        confirmButton={TextButton(onClick={vm.send();review=false}){Text(if(thai)"ส่งคำขอ" else "Send handoff")}},
        dismissButton={TextButton(onClick={review=false}){Text(if(thai)"แก้ไข" else "Edit")}})
}

private fun kotlinx.coroutines.CoroutineScope.launchRefresh(vm:HandoffViewModel) = launch { vm.refresh() }

@Composable
private fun HandoffContextCard(context:HandoffContext,thai:Boolean) {
    Card { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(if(thai)"บันทึกล่าสุดและสิ่งที่ต้องทำต่อ" else "Recent records and upcoming activities",style=MaterialTheme.typography.titleMedium)
        Text((if(thai)"ข้อมูล ณ " else "As of ")+displayHandoffTime(context.asOf),style=MaterialTheme.typography.bodySmall)
        if(!context.healthAllowed) Text(if(thai)"ไม่มีสิทธิ์ดูข้อมูลสุขภาพ" else "Health records are not available with your current permissions.")
        else if(context.recent.isEmpty()) Text(if(thai)"ไม่มีบันทึกในช่วงนี้ ไม่ได้หมายความว่าไม่มีการดูแล" else "No records in this period; this does not mean care did not happen.")
        if(context.healthAllowed) listOf("milk","sleep","waste").forEach { kind ->
            val label = if(thai) when(kind) { "milk"->"นม"; "sleep"->"นอน"; else->"ขับถ่าย" } else kind
            val last=context.recent.firstOrNull { it.kind==kind }
            Text(label+": "+(last?.let { displayHandoffTime(it.occurredAt)+" · "+it.detail.orEmpty() }
                ?: if(thai)"ไม่มีบันทึกในช่วงนี้" else "No record in this period"))
        }
        context.recent.take(12).forEach { record ->
            Text("${displayHandoffTime(record.occurredAt)} · ${record.kind} · ${record.detail.orEmpty()} · ${record.authorName.orEmpty()}")
        }
        if(context.recent.size>12) Text(if(thai)"แสดง 12 รายการล่าสุด" else "Showing the latest 12 records")
        context.upcoming.forEach { Text("${displayHandoffTime(it.startsAt)} · ${it.title}") }
    } }
}

fun displayHandoffTime(value:String):String = runCatching {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm"))
}.getOrDefault(value)
