package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class NeonThemesActivity extends BaseFragment {

    private static final String[] NEON_THEME_NAMES = {"Neon Pink", "Neon Cyan", "Neon Green", "Neon Purple"};

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.NeonThemes));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.NeonThemes)));
        for (int a = 0; a < NEON_THEME_NAMES.length; a++) {
            items.add(UItem.asRadio(a, NEON_THEME_NAMES[a]).setChecked(isCurrentTheme(NEON_THEME_NAMES[a])));
        }
        items.add(UItem.asShadow(getString(R.string.NeonThemesInfo)));
    }

    private boolean isCurrentTheme(String name) {
        Theme.ThemeInfo current = Theme.getCurrentTheme();
        return current != null && name.equals(current.getKey());
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 0 || item.id >= NEON_THEME_NAMES.length) {
            return;
        }
        final String themeName = NEON_THEME_NAMES[item.id];
        final Theme.ThemeInfo themeInfo = Theme.getTheme(themeName);
        if (themeInfo == null) {
            return;
        }
        if (themeInfo == Theme.getCurrentTheme()) {
            return;
        }

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("neon_theme_selected", themeName);
        editor.commit();

        Theme.PatternsLoader.createLoader(false);
        Theme.turnOffAutoNight(this);
        EmojiThemes.saveCustomTheme(themeInfo, themeInfo.currentAccentId);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, null, -1);

        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, themeName, getString(R.string.NeonThemeApplied)).show();
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}