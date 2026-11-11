package com.gamepilot.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final String[] names = {"崩壞：星穹鐵道", "世界計畫"};
    private final int[] defaults = {300, 10};
    // Defaults are editable: game versions/servers may use different regeneration rules.
    private final int[] minutes = {6, 30};
    private android.content.SharedPreferences prefs;
    private LinearLayout root;
    private TextView energyView, statusView;
    private ProgressBar bar;
    private int game = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 30000); } };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("gamepilot_v01", MODE_PRIVATE);
        game = prefs.getInt("selected", 0);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        draw();
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(ticker); handler.post(ticker); }
    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }

    private String key(String field) { return "g" + game + "_" + field; }
    private int max() { return prefs.getInt(key("max"), defaults[game]); }
    private int interval() { return prefs.getInt(key("interval"), minutes[game]); }
    private int stored() { return prefs.getInt(key("energy"), 0); }
    private long timestamp() { return prefs.getLong(key("time"), System.currentTimeMillis()); }
    private long gained() {
        long elapsed = Math.max(0, System.currentTimeMillis() - timestamp());
        return elapsed / (Math.max(1, interval()) * 60000L);
    }
    private int current() { return (int) Math.min(max(), Math.max(0, (long)stored() + gained())); }
    private void saveEnergy(int amount) {
        prefs.edit().putInt(key("energy"), Math.max(0, Math.min(max(), amount)))
            .putLong(key("time"), System.currentTimeMillis()).apply();
        cancelReminder(); draw();
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(30,39,57));
        v.setPadding(0, 10, 0, 10); return v;
    }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b; }
    private EditText number(String hint, int value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setInputType(2); e.setHint(hint); e.setText(String.valueOf(value)); return e;
    }
    private int parse(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; }
    }
    private void draw() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad); root.setBackgroundColor(Color.rgb(246,248,252));
        scroll.addView(root); setContentView(scroll);
        root.addView(label("🎮 GamePilot v0.1", 27));
        root.addView(label("離線體力管家｜估算值，非遊戲即時資料", 13));
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names));
        root.addView(spinner);
        spinner.setSelection(game);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos != game) { game = pos; prefs.edit().putInt("selected", game).apply(); draw(); }
            }
        });
        energyView = label("", 30); root.addView(energyView);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000); root.addView(bar);
        statusView = label("", 14); root.addView(statusView);
        root.addView(label("更新遊戲內實際體力", 18));
        EditText energyInput = number("目前體力", current()); root.addView(energyInput);
        Button save = button("儲存目前體力"); root.addView(save);
        save.setOnClickListener(v -> saveEnergy(parse(energyInput, current())));
        root.addView(label("體力規則（可依伺服器與版本調整）", 18));
        EditText maxInput = number("體力上限", max()); root.addView(maxInput);
        EditText intervalInput = number("每點恢復分鐘", interval()); root.addView(intervalInput);
        Button rules = button("儲存規則"); root.addView(rules);
        rules.setOnClickListener(v -> {
            int oldCurrent = current();
            int m = parse(maxInput, max()), intervalM = parse(intervalInput, interval());
            if (m < 1 || m > 99999 || intervalM < 1 || intervalM > 100000) { toast("請輸入有效的正整數"); return; }
            prefs.edit().putInt(key("max"), m).putInt(key("interval"), intervalM)
                .putInt(key("energy"), Math.min(oldCurrent, m))
                .putLong(key("time"), System.currentTimeMillis()).apply();
            cancelReminder(); draw();
        });
        root.addView(label("體力達標通知", 18));
        EditText targetInput = number("提醒目標體力", max()); root.addView(targetInput);
        Button reminder = button("設定提醒"); root.addView(reminder);
        reminder.setOnClickListener(v -> scheduleReminder(parse(targetInput, max())));
        Button cancel = button("取消提醒"); root.addView(cancel);
        cancel.setOnClickListener(v -> { cancelReminder(); toast("已取消此遊戲的提醒"); });
        root.addView(label("任務清單（只記錄，不會自動操作遊戲）", 18));
        EditText taskInput = new EditText(this); taskInput.setSingleLine(true);
        taskInput.setHint(game == 0 ? "例如：刷信用點" : "例如：打指定歌曲"); root.addView(taskInput);
        Button add = button("新增任務"); root.addView(add);
        add.setOnClickListener(v -> {
            String t = taskInput.getText().toString().trim().replace("\n", " ");
            if (t.length() == 0) return;
            if (t.length() > 100) t = t.substring(0, 100);
            String old = prefs.getString(key("tasks"), "");
            prefs.edit().putString(key("tasks"), old + t + "\n").apply(); draw();
        });
        String all = prefs.getString(key("tasks"), "");
        if (!all.isEmpty()) for (String t : all.split("\n")) if (!t.isEmpty()) root.addView(label("• " + t, 15));
        Button clear = button("清空本遊戲任務"); root.addView(clear);
        clear.setOnClickListener(v -> { prefs.edit().remove(key("tasks")).apply(); draw(); });
        root.addView(label("遠端執行器：尚未連接（未實作）", 13));
        refresh();
    }
    private void refresh() {
        if (energyView == null || statusView == null || bar == null) return;
        int value = current(), max = max();
        energyView.setText(value + " / " + max + " " + (game == 0 ? "開拓力" : "演出能量"));
        bar.setProgress((int)(1000L * value / Math.max(1, max)));
        long remaining = Math.max(0, (long)(max - value) * interval() * 60000L - (System.currentTimeMillis() - timestamp()) % (interval() * 60000L));
        if (value >= max) remaining = 0;
        long min = (remaining + 59999) / 60000;
        statusView.setText(value >= max ? "已達設定上限" : "預估約 " + (min / 60) + " 小時 " + (min % 60) + " 分鐘後恢復滿");
    }
    private int reminderId() { return 2000 + game; }
    private PendingIntent pending(int flags) {
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("game", names[game]); intent.putExtra("id", reminderId());
        return PendingIntent.getBroadcast(this, reminderId(), intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }
    private void cancelReminder() {
        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
        PendingIntent pi = pending(PendingIntent.FLAG_NO_CREATE);
        if (pi != null) { alarm.cancel(pi); pi.cancel(); }
    }
    private void scheduleReminder(int target) {
        if (target < 1 || target > max()) { toast("提醒目標必須在 1 到體力上限之間"); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100); toast("請允許通知後重新設定"); return;
        }
        cancelReminder();
        long time;
        if (current() >= target) time = System.currentTimeMillis() + 1000;
        else {
            long required = (long)(target - stored()) * interval() * 60000L;
            time = Math.max(System.currentTimeMillis() + 1000, timestamp() + required);
        }
        AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
        PendingIntent pi = pending(PendingIntent.FLAG_UPDATE_CURRENT);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        toast("已設定預估提醒；系統省電可能造成延遲");
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
