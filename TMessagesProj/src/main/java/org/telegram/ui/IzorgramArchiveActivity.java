package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.IzorgramMessageArchive;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Просмотр локально сохранённых сообщений (в том числе удалённых в Telegram). */
public class IzorgramArchiveActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private final ArrayList<IzorgramMessageArchive.Entry> entries = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.MessageArchive));
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
        entries.clear();
        entries.addAll(IzorgramMessageArchive.load(200));
        items.add(UItem.asHeader(getString(R.string.MessageArchive)));
        items.add(UItem.asShadow(getString(R.string.MessageArchiveInfo)));
        if (entries.isEmpty()) {
            items.add(UItem.asButton(-1, getString(R.string.MessageArchiveEmpty)));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            IzorgramMessageArchive.Entry e = entries.get(i);
            items.add(UItem.asButton(100 + i, e.title(), e.subtitle()));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 100) return;
        int index = item.id - 100;
        if (index < 0 || index >= entries.size()) return;
        IzorgramMessageArchive.Entry e = entries.get(index);
        new AlertDialog.Builder(getParentActivity())
                .setTitle(e.title())
                .setMessage(e.text)
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
