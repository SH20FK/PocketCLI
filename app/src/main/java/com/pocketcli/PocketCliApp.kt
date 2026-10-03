package com.pocketcli

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PocketCliApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low importance channel for background execution status
            val statusChannel = NotificationChannel(
                CHANNEL_AGENT_STATUS,
                "Agent Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and background status of running CLI agents"
            }

            // High importance channel for approval / permission prompts
            val actionChannel = NotificationChannel(
                CHANNEL_AGENT_ACTION,
                "Action Required",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prompts when agent requires tool approval or user confirmation"
            }

            notificationManager.createNotificationChannels(listOf(statusChannel, actionChannel))
        }
    }

    companion object {
        const val CHANNEL_AGENT_STATUS = "channel_agent_status"
        const val CHANNEL_AGENT_ACTION = "channel_agent_action"
    }
}
