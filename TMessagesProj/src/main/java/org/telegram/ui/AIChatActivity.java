package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIChatActivity extends BaseFragment {

    private final ArrayList<String[]> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout messagesLayout;
    private ScrollView scrollView;
    private EditText inputBox;
    private boolean sending;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AIAssistant));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    presentFragment(new AISettingsActivity());
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.outline_profile_settings);

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        linearLayout.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        messagesLayout = new LinearLayout(context);
        messagesLayout.setOrientation(LinearLayout.VERTICAL);
        messagesLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        scrollView.addView(messagesLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        inputRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        linearLayout.addView(inputRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 54));

        inputBox = new EditText(context);
        inputBox.setSingleLine(true);
        inputBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        inputBox.setHint(getString(R.string.AISendHint));
        inputBox.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        inputBox.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        inputBox.setBackground(null);
        inputBox.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        inputRow.addView(inputBox, LayoutHelper.createLinear(0, 48, 1.0f));

        ChatActivityEnterView.SendButton sendButton = new ChatActivityEnterView.SendButton(context, R.drawable.send_plane_24, resourceProvider);
        sendButton.setOnClickListener(v -> sendMessage());
        inputRow.addView(sendButton, LayoutHelper.createLinear(48, 48));

        return fragmentView = contentView;
    }

    private void addBubble(String role, String text, boolean error) {
        messagesLayout.setVisibility(View.GONE);

        LinearLayout bubbleRow = new LinearLayout(getContext());
        bubbleRow.setOrientation(LinearLayout.HORIZONTAL);
        bubbleRow.setGravity("user".equals(role) ? Gravity.RIGHT : Gravity.LEFT);

        TextView bubble = new TextView(getContext());
        bubble.setTextSize(15);
        bubble.setTextColor(error ? 0xffffffff
                : "user".equals(role) ? 0xffffffff : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        bubble.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(9), AndroidUtilities.dp(12), AndroidUtilities.dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        int color = error ? Theme.getColor(Theme.key_text_RedRegular, resourceProvider)
                : "user".equals(role) ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
        bg.setColor(color);
        bubble.setBackground(bg);
        bubbleRow.addView(bubble, LayoutHelper.createLinear(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, "user".equals(role) ? 14 : 4, 0, 0, 0));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(6);
        messagesLayout.addView(bubbleRow, lp);
        messagesLayout.setVisibility(View.VISIBLE);

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        if (sending) {
            return;
        }
        String text = inputBox.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        inputBox.setText("");
        addBubble("user", text, false);
        history.add(new String[]{"user", text});

        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        String provider = prefs.getString(AISettingsActivity.PREF_PROVIDER, "custom");
        String baseUrl = prefs.getString(AISettingsActivity.PREF_BASE_URL, "");
        String apiKey = prefs.getString(AISettingsActivity.PREF_API_KEY, "");
        String model = prefs.getString(AISettingsActivity.PREF_MODEL, "");
        if (apiKey.isEmpty()) {
            addBubble("ai", getString(R.string.AIErrorEmptyToken), true);
            return;
        }
        sending = true;
        final ArrayList<String[]> messages = new ArrayList<>(history);
        executor.execute(() -> {
            String reply;
            boolean ok;
            try {
                reply = request(provider, baseUrl, apiKey, model, messages);
                ok = true;
            } catch (Exception e) {
                reply = e.getMessage() == null ? getString(R.string.AIErrorMessage) : e.getMessage();
                ok = false;
            }
            final String replyText = reply;
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                sending = false;
                addBubble("ai", replyText, !success);
                if (success) {
                    history.add(new String[]{"assistant", replyText});
                } else {
                    history.add(new String[]{"assistant", getString(R.string.AIErrorMessage)});
                }
                if (history.size() > 40) {
                    history.remove(0);
                }
            });
        });
    }

    private String request(String provider, String baseUrl, String apiKey, String model, ArrayList<String[]> messages) throws Exception {
        JSONArray payload = new JSONArray();
        for (String[] m : messages) {
            JSONObject o = new JSONObject();
            o.put("role", m[0]);
            o.put("content", m[1]);
            payload.put(o);
        }

        String urlString;
        HttpURLConnection conn;
        JSONObject body = new JSONObject();

        if ("gemini".equals(provider)) {
            urlString = baseUrl + "/models/" + model + ":generateContent" + (baseUrl.contains("?") ? "&" : "?") + "key=" + apiKey;
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", messages.get(messages.size() - 1)[1]);
            parts.put(part);
            JSONObject content = new JSONObject();
            content.put("parts", parts);
            JSONArray contents = new JSONArray();
            contents.put(content);
            body.put("contents", contents);
            conn = open(urlString, "application/json", null);
        } else if ("anthropic".equals(provider)) {
            urlString = baseUrl + "/messages";
            body.put("model", model);
            body.put("max_tokens", 2048);
            body.put("messages", payload);
            conn = open(urlString, "application/json", null);
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            urlString = baseUrl + "/chat/completions";
            body.put("model", model);
            body.put("messages", payload);
            body.put("stream", false);
            conn = open(urlString, "application/json", "Bearer " + apiKey);
        }

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is == null ? conn.getInputStream() : is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        if (code >= 200 && code < 300) {
            JSONObject json = new JSONObject(sb.toString());
            if ("gemini".equals(provider)) {
                return json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
            }
            if ("anthropic".equals(provider)) {
                JSONArray content = json.getJSONArray("content");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject item = content.getJSONObject(i);
                    if ("text".equals(item.optString("type"))) {
                        result.append(item.optString("text"));
                    }
                }
                return result.toString().trim();
            }
            return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
        }
        throw new Exception(getString(R.string.AIErrorMessage) + " (" + code + ")");
    }

    private HttpURLConnection open(String urlString, String contentType, String auth) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", contentType);
        if (auth != null) {
            conn.setRequestProperty("Authorization", auth);
        }
        return conn;
    }
}