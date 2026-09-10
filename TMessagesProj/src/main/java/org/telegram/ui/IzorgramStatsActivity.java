package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class IzorgramStatsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private static final String PREFS = "izorgram_stats_cache";
    private long messagesCount;
    private long dialogsCount;
    private long dataSize;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.IzorgramStats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SharedPreferences cached = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        messagesCount = cached.getLong("messages", 0);
        dialogsCount = cached.getLong("dialogs", 0);
        dataSize = cached.getLong("data", 0);

        loadCounts();

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    private void loadCounts() {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            long messages = 0;
            long dialogs = 0;
            try {
                SQLiteDatabase db = MessagesStorage.getInstance(currentAccount).getDatabase();
                SQLiteCursor c = db.queryFinalized("SELECT COUNT(*) FROM messages_v2");
                if (c.next()) {
                    messages = c.intValue(0);
                }
                c.dispose();
                SQLiteCursor c2 = db.queryFinalized("SELECT COUNT(*) FROM dialogs");
                if (c2.next()) {
                    dialogs = c2.intValue(0);
                }
                c2.dispose();
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
            }
            final long fMessages = messages;
            final long fDialogs = dialogs;
            AndroidUtilities.runOnUIThread(() -> {
                messagesCount = fMessages;
                dialogsCount = fDialogs;
                dataSize = dirSize(ApplicationLoader.applicationContext.getFilesDir()) + dirSize(ApplicationLoader.applicationContext.getCacheDir());
                ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("messages", fMessages).putLong("dialogs", fDialogs).putLong("data", dataSize).apply();
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(false);
                }
            });
        });
    }

    private static long dirSize(File dir) {
        long size = 0;
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    size += file.isDirectory() ? dirSize(file) : file.length();
                }
            }
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
        return size;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.IzorgramStats)));
        items.add(UItem.asButton(-1, getString(R.string.IzorgramStatsMessages), String.valueOf(messagesCount)));
        items.add(UItem.asButton(-1, getString(R.string.IzorgramStatsDialogs), String.valueOf(dialogsCount)));
        items.add(UItem.asButton(-1, getString(R.string.IzorgramStatsData), formatSize(dataSize)));
        items.add(UItem.asButton(-1, getString(R.string.IzorgramStatsVersion), BuildConfig.BUILD_VERSION_STRING));
        items.add(UItem.asButton(-1, getString(R.string.IzorgramStatsAccount), String.valueOf(UserConfig.getInstance(currentAccount).getClientUserId())));

        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(1, getString(R.string.IzorgramStatsRefresh)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            loadCounts();
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}