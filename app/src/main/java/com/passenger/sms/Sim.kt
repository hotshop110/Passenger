package com.passenger.sms

import android.Manifest
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.compose.runtime.mutableStateOf

data class SimInfo(val subId: Int, val slot: Int, val label: String)

object Sim {
    val sims = mutableStateOf<List<SimInfo>>(emptyList())

    fun list(ctx: Context): List<SimInfo> {
        if (!Sms.granted(ctx, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            val sm = ctx.getSystemService(SubscriptionManager::class.java)
            val l = sm.activeSubscriptionInfoList ?: emptyList()
            l.map {
                SimInfo(it.subscriptionId, it.simSlotIndex, it.displayName?.toString() ?: "SIM ${it.simSlotIndex + 1}")
            }.sortedBy { it.slot }
        } catch (e: Exception) { emptyList() }
    }

    fun refresh(ctx: Context) { sims.value = list(ctx) }

    fun defaultSub(): Int = try { SubscriptionManager.getDefaultSmsSubscriptionId() } catch (e: Exception) { -1 }

    fun slotLabel(subId: Int): String? {
        val l = sims.value
        if (l.size < 2 || subId < 0) return null
        val s = l.firstOrNull { it.subId == subId } ?: return null
        return "SIM ${s.slot + 1}"
    }
}
