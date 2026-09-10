package org.telegram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class IzorgramGameActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    private static final String PREFS = "izorgram_game";
    private static final String KEY_COINS = "coins";
    private static final String KEY_AUTOCLICK = "autoclick";
    private static final String KEY_MULTIPLIER = "multiplier";

    private SharedPreferences prefs;

    @Override
    public View createView(Context context) {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.IzorgramGame));
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

    private int coins() {
        return prefs.getInt(KEY_COINS, 0);
    }

    private int multiplier() {
        return Math.max(1, prefs.getInt(KEY_MULTIPLIER, 1));
    }

    private boolean autoclick() {
        return prefs.getBoolean(KEY_AUTOCLICK, false);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.IzorgramGameScoreHead)));
        UItem score = UItem.asButton(-1, formatString(R.string.IzorgramGameCoins, coins()), getString(R.string.IzorgramGameMasochist));
        score.setEnabled(false);
        items.add(score);

        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(1, getString(R.string.IzorgramGameTap)).setEnabled(true));
        items.add(UItem.asButton(2, getString(R.string.IzorgramGameReset)).setEnabled(true));

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.IzorgramGameShop)));
        items.add(UItem.asButton(3, formatString(R.string.IzorgramGameAutoclick, autoclick() ? 500 : 50), getString(R.string.IzorgramGameAutoclickInfo)).setEnabled(true));
        items.add(UItem.asButton(4, formatString(R.string.IzorgramGameMult, 100), formatString(R.string.IzorgramGameMultInfo, multiplier())).setEnabled(true));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        SharedPreferences.Editor e = prefs.edit();
        switch (item.id) {
            case 1:
                int gain = multiplier();
                e.putInt(KEY_COINS, coins() + gain);
                if (autoclick()) {
                    e.putInt(KEY_COINS, coins() + gain + 1);
                }
                e.apply();
                listView.adapter.update(false);
                break;
            case 2:
                e.clear().apply();
                listView.adapter.update(false);
                break;
            case 3:
                if (!autoclick() && coins() >= 50) {
                    e.putBoolean(KEY_AUTOCLICK, true).putInt(KEY_COINS, coins() - 50).apply();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramGameBought)).show(true);
                } else if (autoclick() && coins() >= 500) {
                    e.putInt(KEY_COINS, coins() - 500).putInt(KEY_MULTIPLIER, multiplier() + 1).apply();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramGameBought)).show(true);
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramGameNoMoney)).show(true);
                }
                listView.adapter.update(false);
                break;
            case 4:
                if (coins() >= 100) {
                    e.putInt(KEY_COINS, coins() - 100).putInt(KEY_MULTIPLIER, multiplier() + 1).apply();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramGameBought)).show(true);
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramGameNoMoney)).show(true);
                }
                listView.adapter.update(false);
                break;
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}