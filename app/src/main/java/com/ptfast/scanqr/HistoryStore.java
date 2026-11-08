package com.ptfast.scanqr;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 极简历史记录:SharedPreferences 里存一个 JSON 数组,最多 50 条。 */
public final class HistoryStore {

    private static final String PREF = "history";
    private static final String KEY = "items";
    private static final int MAX = 50;

    private HistoryStore() {
    }

    public static final class Item {
        public final String text;
        public final long time;

        Item(String text, long time) {
            this.text = text;
            this.time = time;
        }

        public String timeLabel() {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        String raw = prefs(context).getString(KEY, null);
        if (raw == null) {
            return items;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                items.add(new Item(o.optString("t"), o.optLong("d")));
            }
        } catch (Throwable ignored) {
        }
        return items;
    }

    public static void add(Context context, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<Item> items = load(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (text.equals(items.get(i).text)) {
                items.remove(i);
                break;
            }
        }
        items.add(0, new Item(text, System.currentTimeMillis()));
        while (items.size() > MAX) {
            items.remove(items.size() - 1);
        }
        save(context, items);
    }

    public static void remove(Context context, int index) {
        List<Item> items = load(context);
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            save(context, items);
        }
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static void save(Context context, List<Item> items) {
        try {
            JSONArray array = new JSONArray();
            for (Item item : items) {
                JSONObject o = new JSONObject();
                o.put("t", item.text);
                o.put("d", item.time);
                array.put(o);
            }
            prefs(context).edit().putString(KEY, array.toString()).apply();
        } catch (Throwable ignored) {
        }
    }
}
