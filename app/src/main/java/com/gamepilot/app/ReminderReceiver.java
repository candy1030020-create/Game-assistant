package com.gamepilot.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel = "energy_reminders";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, "體力提醒", NotificationManager.IMPORTANCE_DEFAULT));
        String game = intent.getStringExtra("game");
        int target = intent.getIntExtra("target", 0);
        Notification n = new Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle((game == null ? "遊戲" : game) + " 體力提醒")
            .setContentText("預估體力已達 " + target + "，請進入遊戲確認。")
            .setAutoCancel(true).build();
        try { manager.notify(intent.getIntExtra("id", 1), n); }
        catch (SecurityException ignored) { }
    }
}
