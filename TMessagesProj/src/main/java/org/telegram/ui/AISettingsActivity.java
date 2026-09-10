package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

public class AISettingsActivity extends BaseFragment {

    static final String PREF_PROVIDER = "ai_provider";
    static final String PREF_BASE_URL = "ai_base_url";
    static final String PREF_API_KEY = "ai_api_key";
    static final String PREF_MODEL = "ai_model";

    public static String[][] PROVIDERS = new String[][] {
            {"custom", "Custom", "", ""},
            {"openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"},
            {"groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"},
            {"gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash"},
            {"deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"},
            {"mistral", "Mistral AI", "https://api.mistral.ai/v1", "mistral-small-latest"},
            {"openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"},
            {"anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-haiku-latest"},
    };

    private SharedPreferences prefs;

    @Override
    public View createView(Context context) {
        prefs = MessagesController.getGlobalMainSettings();

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AISettingsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        scrollView.setFillViewport(true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(24));
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        linearLayout.addView(container, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, 16));

        TextView providerLabel = new TextView(context);
        providerLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider));
        providerLabel.setTextSize(14);
        providerLabel.setText(getString(R.string.AIChooseProvider));
        container.addView(providerLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 20, 0, 20));

        RadioGroup radioGroup = new RadioGroup(context);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        String currentProvider = prefs.getString(PREF_PROVIDER, "custom");
        for (int i = 0; i < PROVIDERS.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setId(i);
            rb.setText(PROVIDERS[i][1]);
            rb.setTextSize(16);
            rb.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            rb.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            rb.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(6), AndroidUtilities.dp(20), AndroidUtilities.dp(6));
            rb.setChecked(PROVIDERS[i][0].equals(currentProvider));
            radioGroup.addView(rb, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL));
        }
        container.addView(radioGroup, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText baseUrlBox = new EditText(context);
        baseUrlBox.setBackground(Theme.createEditTextDrawable(context, false));
        baseUrlBox.setSingleLine(true);
        baseUrlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        baseUrlBox.setText(prefs.getString(PREF_BASE_URL, ""));
        baseUrlBox.setHint(getString(R.string.AIBaseUrl));
        baseUrlBox.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        baseUrlBox.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        container.addView(baseUrlBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 16, 20, 0, 20));

        EditText apiKeyBox = new EditText(context);
        apiKeyBox.setBackground(Theme.createEditTextDrawable(context, false));
        apiKeyBox.setSingleLine(true);
        apiKeyBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyBox.setText(prefs.getString(PREF_API_KEY, ""));
        apiKeyBox.setHint(getString(R.string.AIApiKey));
        apiKeyBox.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        apiKeyBox.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        container.addView(apiKeyBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 16, 20, 0, 20));

        EditText modelBox = new EditText(context);
        modelBox.setBackground(Theme.createEditTextDrawable(context, false));
        modelBox.setSingleLine(true);
        modelBox.setInputType(InputType.TYPE_CLASS_TEXT);
        modelBox.setText(prefs.getString(PREF_MODEL, ""));
        modelBox.setHint(getString(R.string.AIModel));
        modelBox.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        modelBox.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        container.addView(modelBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 16, 20, 0, 20));

        final EditText baseUrlF = baseUrlBox, apiKeyF = apiKeyBox, modelF = modelBox;
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String id = checkedId >= 0 && checkedId < PROVIDERS.length ? PROVIDERS[checkedId][0] : "custom";
            if (!"custom".equals(id)) {
                baseUrlF.setText(PROVIDERS[checkedId][2]);
                modelF.setText(PROVIDERS[checkedId][3]);
            }
        });

        Button saveButton = new Button(context);
        saveButton.setText(getString(R.string.Save));
        saveButton.setOnClickListener(v -> {
            int checked = radioGroup.getCheckedRadioButtonId();
            String id = checked >= 0 && checked < PROVIDERS.length ? PROVIDERS[checked][0] : "custom";
            String baseUrl = baseUrlF.getText().toString().trim();
            String apiKey = apiKeyF.getText().toString().trim();
            String model = modelF.getText().toString().trim();
            if (apiKey.isEmpty()) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.AIErrorEmptyToken)).show();
                return;
            }
            if (baseUrl.isEmpty()) {
                baseUrl = "https://api.openai.com/v1";
            }
            if (model.isEmpty()) {
                model = "gpt-4o-mini";
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_PROVIDER, id);
            editor.putString(PREF_BASE_URL, baseUrl);
            editor.putString(PREF_API_KEY, apiKey);
            editor.putString(PREF_MODEL, model);
            editor.commit();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.AISaved)).show();
        });
        container.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 20, 20, 12, 20));

        return fragmentView = scrollView;
    }
}