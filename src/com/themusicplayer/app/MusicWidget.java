package com.themusicplayer.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget showing the current track and transport controls. */
public class MusicWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids, "The music player", "Tap to play", false);
    }

    static void refresh(Context context, String title, String sub, boolean playing) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MusicWidget.class));
        if (ids != null && ids.length > 0) update(context, manager, ids, title, sub, playing);
    }

    private static void update(Context c, AppWidgetManager manager, int[] ids,
                               String title, String sub, boolean playing) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget);
            views.setTextViewText(R.id.widget_title, title == null ? "The music player" : title);
            views.setTextViewText(R.id.widget_sub, sub == null ? "Tap to play" : sub);
            views.setTextViewText(R.id.widget_toggle, playing ? "\u275A\u275A" : "\u25B6");

            int flag = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent open = PendingIntent.getActivity(c, 1,
                    new Intent(c, MainActivity.class), flag);
            views.setOnClickPendingIntent(R.id.widget_title, open);
            views.setOnClickPendingIntent(R.id.widget_sub, open);
            views.setOnClickPendingIntent(R.id.widget_prev, pi(c, MusicService.ACTION_PREV, 2));
            views.setOnClickPendingIntent(R.id.widget_toggle, pi(c, MusicService.ACTION_TOGGLE, 3));
            views.setOnClickPendingIntent(R.id.widget_next, pi(c, MusicService.ACTION_NEXT, 4));
            manager.updateAppWidget(id, views);
        }
    }

    private static PendingIntent pi(Context c, String action, int requestCode) {
        return PendingIntent.getService(c, requestCode,
                new Intent(c, MusicService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
