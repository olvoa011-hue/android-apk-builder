package com.example.sampleapp
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nm = getSystemService(NotificationManager::class.java)

        if (!nm.isNotificationPolicyAccessGranted) {
            Toast.makeText(
                this,
                "Gi appen tilgang til Ikke forstyrr, og åpne den på nytt",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } else {
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                Toast.makeText(this, "Ikke forstyrr er på", Toast.LENGTH_SHORT).show()
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Toast.makeText(this, "Ikke forstyrr er av", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
