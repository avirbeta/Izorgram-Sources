package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.Izorgram;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserNameResolver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class GeneralSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    static final String PREF_INVISIBLE_MODE = "invisible_mode";
    static final String PREF_HIDE_ONLINE = "hide_online";
    static final String PREF_DISABLE_TYPING = "disable_typing";
    static final String PREF_DISABLE_READ = "disable_read_receipts";
    static final String PREF_HIDE_AUDIO = "hide_audio_record";
    static final String PREF_HIDE_STORY_VIEWS = "hide_story_views";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.Miscellaneous));
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

    private SharedPreferences prefs() {
        return MessagesController.getInstance(currentAccount).getMainSettings();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        SharedPreferences prefs = prefs();

        UItem ghostMode = UItem.asCheck(0, getString(R.string.GhostMode)).setChecked(prefs.getBoolean("ghostMode", false));
        ghostMode.subtext = getString(R.string.GhostModeInfo);
        items.add(ghostMode);

        UItem invisibleMode = UItem.asCheck(1, getString(R.string.InvisibleMode)).setChecked(prefs.getBoolean(PREF_INVISIBLE_MODE, false));
        invisibleMode.subtext = getString(R.string.InvisibleModeInfo);
        items.add(invisibleMode);

        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.InvisibleMode)));

        UItem hideOnline = UItem.asCheck(2, getString(R.string.HideOnline)).setChecked(prefs.getBoolean(PREF_HIDE_ONLINE, false));
        hideOnline.subtext = getString(R.string.HideOnlineInfo);
        items.add(hideOnline);

        UItem hideTyping = UItem.asCheck(3, getString(R.string.DisableTyping)).setChecked(prefs.getBoolean(PREF_DISABLE_TYPING, false));
        hideTyping.subtext = getString(R.string.DisableTypingInfo);
        items.add(hideTyping);

        UItem hideRead = UItem.asCheck(4, getString(R.string.DisableReadReceipts)).setChecked(prefs.getBoolean(PREF_DISABLE_READ, false));
        hideRead.subtext = getString(R.string.DisableReadReceiptsInfo);
        items.add(hideRead);

        UItem hideAudio = UItem.asCheck(5, getString(R.string.HideAudioRecord)).setChecked(prefs.getBoolean(PREF_HIDE_AUDIO, false));
        hideAudio.subtext = getString(R.string.HideAudioRecordInfo);
        items.add(hideAudio);

        UItem hideStories = UItem.asCheck(6, getString(R.string.HideStoryViews)).setChecked(prefs.getBoolean(PREF_HIDE_STORY_VIEWS, false));
        hideStories.subtext = getString(R.string.HideStoryViewsInfo);
        items.add(hideStories);

        items.add(UItem.asShadow(null));

        UItem autoRead = UItem.asCheck(32, getString(R.string.AutoReadMessages)).setChecked(prefs.getBoolean("autoRead", false));
        autoRead.subtext = getString(R.string.AutoReadMessagesInfo);
        items.add(autoRead);

        UItem saveMessages = UItem.asCheck(33, getString(R.string.SaveMessages)).setChecked(prefs.getBoolean("saveMessages", true));
        saveMessages.subtext = getString(R.string.SaveMessagesInfo);
        items.add(saveMessages);

        items.add(UItem.asButton(34, getString(R.string.MessageArchive), getString(R.string.MessageArchiveInfo)));

        items.add(UItem.asShadow(null));

        UItem disableAds = UItem.asCheck(10, getString(R.string.DisableAds)).setChecked(prefs.getBoolean("disable_ads", false));
        disableAds.subtext = getString(R.string.DisableAdsInfo);
        items.add(disableAds);

        items.add(UItem.asShadow(null));

        SharedPreferences mainConfig = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        UItem displayExtraProfileInfo = UItem.asCheck(7, getString(R.string.DisplayExtraProfileInformation)).setChecked(
            mainConfig.getBoolean("displayExtraProfileInformation", false));
        displayExtraProfileInfo.subtext = getString(R.string.DisplayExtraProfileInformationInfo);
        items.add(displayExtraProfileInfo);

        UItem hideTopChatBars = UItem.asCheck(8, getString(R.string.HideTopChatBars)).setChecked(
            mainConfig.getBoolean("hideTopChatBars", false));
        hideTopChatBars.subtext = getString(R.string.HideTopChatBarsInfo);
        items.add(hideTopChatBars);

        UItem displayAdditionalMessageOptions = UItem.asCheck(9, getString(R.string.DisplayAdditionalMessageOptions)).setChecked(
            mainConfig.getBoolean("displayAdditionalMessageOptions", false));
        displayAdditionalMessageOptions.subtext = getString(R.string.DisplayAdditionalMessageOptionsInfo);
        items.add(displayAdditionalMessageOptions);

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.IzorgramSection)));

        items.add(UItem.asButton(20, getString(R.string.IzorgramBackup), getString(R.string.IzorgramBackupInfo)));
        items.add(UItem.asButton(21, getString(R.string.IzorgramRestore), getString(R.string.IzorgramRestoreInfo)));
        items.add(UItem.asButton(22, getString(R.string.IzorgramExport), getString(R.string.IzorgramExportInfo)));
        items.add(UItem.asButton(23, getString(R.string.IzorgramImport), getString(R.string.IzorgramImportInfo)));

        items.add(UItem.asShadow(null));

        UItem hideBadges = UItem.asCheck(26, getString(R.string.IzorgramHideBadges)).setChecked(prefs.getBoolean("hide_badges", false));
        hideBadges.subtext = getString(R.string.IzorgramHideBadgesInfo);
        items.add(hideBadges);

        UItem hideLastSeen = UItem.asCheck(29, getString(R.string.IzorgramHideLastSeen)).setChecked(prefs.getBoolean("hide_last_seen", false));
        hideLastSeen.subtext = getString(R.string.IzorgramHideLastSeenInfo);
        items.add(hideLastSeen);

        items.add(UItem.asButton(28, getString(R.string.IzorgramStats)));

        items.add(UItem.asButton(27, getString(R.string.IzorgramGame)));
        items.add(UItem.asButton(24, getString(R.string.IzorgramJoinChannel)));
        items.add(UItem.asButton(25, getString(R.string.IzorgramOfficialChannel)));
    }

    private boolean toggle(int id, String key, View view) {
        SharedPreferences prefs = prefs();
        boolean newValue = !prefs.getBoolean(key, false);
        prefs.edit().putBoolean(key, newValue).apply();
        if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(newValue);
        }
        listView.adapter.update(false);
        return newValue;
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case 0:
                if (toggle(0, "ghostMode", view)) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.GhostMode)).show();
                }
                break;
            case 1:
                if (toggle(1, PREF_INVISIBLE_MODE, view)) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.InvisibleMode)).show();
                }
                break;
            case 2:
                toggle(2, PREF_HIDE_ONLINE, view);
                break;
            case 3:
                toggle(3, PREF_DISABLE_TYPING, view);
                break;
            case 4:
                toggle(4, PREF_DISABLE_READ, view);
                break;
            case 5:
                toggle(5, PREF_HIDE_AUDIO, view);
                break;
            case 6:
                toggle(6, PREF_HIDE_STORY_VIEWS, view);
                break;
            case 32:
                toggle(32, "autoRead", view);
                break;
            case 33:
                toggle(33, "saveMessages", view);
                break;
            case 34:
                presentFragment(new IzorgramArchiveActivity());
                break;
            case 10:
                toggle(10, "disable_ads", view);
                break;
            case 20:
                Izorgram.backupToSavedMessages(currentAccount, () -> BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramBackup)).show(true));
                break;
            case 21:
                Izorgram.restoreLastFromSavedMessages(currentAccount, () -> BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramRestore)).show(true));
                break;
            case 22: {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_TITLE, "izorgram-backup.json");
                startActivityForResult(intent, 22);
                break;
            }
            case 23: {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, 23);
                break;
            }
            case 24: {
                UserNameResolver resolver = MessagesController.getInstance(currentAccount).getUserNameResolver();
                resolver.resolve("izorgram", peerId -> {
                    if (peerId != null && peerId < 0) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(peerId);
                        MessagesController.getInstance(currentAccount).addUserToChat(chat != null ? chat.id : peerId, UserConfig.getInstance(currentAccount).getCurrentUser(), 0, null, GeneralSettingsActivity.this, () -> BulletinFactory.of(GeneralSettingsActivity.this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramJoinDone)).show(true));
                    } else {
                        BulletinFactory.of(GeneralSettingsActivity.this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.IzorgramOfficialChannel)).show(true);
                    }
                });
                break;
            }
            case 25:
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/izorgram"));
                    getParentActivity().startActivity(i);
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
                break;
            case 26:
                toggle(26, "hide_badges", view);
                break;
            case 29:
                toggle(29, "hide_last_seen", view);
                break;
            case 28:
                presentFragment(new IzorgramStatsActivity());
                break;
            case 27:
                presentFragment(new IzorgramGameActivity());
                break;
            case 7: {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                boolean newValue = !prefs.getBoolean("displayExtraProfileInformation", false);
                prefs.edit().putBoolean("displayExtraProfileInformation", newValue).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
                listView.adapter.update(false);
                break;
            }
            case 8: {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                boolean newValue = !prefs.getBoolean("hideTopChatBars", false);
                prefs.edit().putBoolean("hideTopChatBars", newValue).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
                listView.adapter.update(false);
                break;
            }
            case 9: {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                boolean newValue = !prefs.getBoolean("displayAdditionalMessageOptions", false);
                prefs.edit().putBoolean("displayAdditionalMessageOptions", newValue).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newValue);
                }
                listView.adapter.update(false);
                break;
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == 22) {
            if (Izorgram.exportToFile(getParentActivity(), uri, currentAccount)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramExport)).show(true);
            }
        } else if (requestCode == 23) {
            String json = Izorgram.readFile(getParentActivity(), uri);
            if (json != null && Izorgram.applyBackupJson(currentAccount, json)) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.IzorgramImport)).show(true);
            }
            listView.adapter.update(false);
        }
    }
}