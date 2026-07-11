package com.kaon.music.core.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kaon.music.KaonApplication

class DiagnosticsDumper : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.kaon.music.DUMP_DIAGNOSTICS") {
            try {
                val app = context.applicationContext as KaonApplication
                val registry = app.kernel.get(DiagnosticsRegistry::class)
                val dumpString = registry.dump()
                Log.d("KAON_DIAGNOSTICS", dumpString)
            } catch (e: Exception) {
                Log.e("KAON_DIAGNOSTICS", "Failed to dump diagnostics", e)
            }
        }
    }
}
