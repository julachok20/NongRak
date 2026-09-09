package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.handoff.CareHandoff
import com.lullabyai.app.data.repository.HandoffRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun HandoffHomeCard(childId:String,thai:Boolean,onOpen:()->Unit) {
    var current by remember(childId) { mutableStateOf<CareHandoff?>(null) }
    var failed by remember(childId) { mutableStateOf(false) }
    LaunchedEffect(childId) {
        val repo=HandoffRepository()
        while(true) {
            try {
                current=repo.list(childId).firstOrNull { it.status in listOf("pending","active","completed") }
                failed=false
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { current=null; failed=true }
            delay(30000)
        }
    }
    OutlinedCard(onClick=onOpen,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(if(thai)"ส่งต่อการดูแล" else "Caregiver handoff",style=MaterialTheme.typography.titleMedium)
            Text(current?.let { handoffLabel(it.status,thai) } ?: if(failed) {
                if(thai)"ยังตรวจสอบสถานะไม่ได้ แตะเพื่อลองอีกครั้ง" else "Status unavailable. Tap to retry."
            } else if(thai)"ส่งคำแนะนำและสิ่งที่ต้องทำให้ผู้ดูแล" else "Share instructions and next tasks with a caregiver")
            current?.acceptedAt?.let { Text(displayHandoffTime(it),style=MaterialTheme.typography.bodySmall) }
        }
    }
}
