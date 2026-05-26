package com.hridoy.fcmsender;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;
import com.google.appinventor.components.runtime.util.YailProcedure;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@DesignerComponent(
		version = 7,
		versionName = "1.0.0",
		description = "FCM Sender Extension — sends push notifications via your server backend using HTTP v1 API layouts.",
		nonVisible = true,
		iconName = "icon.png"
)
public class FCMSender extends AndroidNonvisibleComponent {

	private static final String TAG = "FCMSender";
	private static final int CONNECT_TIMEOUT_MS = 10000;
	private static final int READ_TIMEOUT_MS    = 15000;

	private String serverUrl = "";
	private String secretKey = "";

	// Single-thread executor — one request at a time, no thread pool explosion
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler   = new Handler(Looper.getMainLooper());

	// ----------------------------------------------------------------
	// Constructor
	// ----------------------------------------------------------------
	public FCMSender(ComponentContainer container) {
		super(container.$form());
	}

	// ================================================================
	// DESIGNER PROPERTIES
	// ================================================================

	@DesignerProperty(
			editorType = "string",
			defaultValue = ""
	)
	@SimpleProperty(description = "Deployed web app url of google app-script")
	public void ServerUrl(String url) {
		this.serverUrl = url.trim();
	}

	@SimpleProperty(description = "Returns the current server URL.")
	public String ServerUrl() {
		return serverUrl;
	}

	@DesignerProperty(
			editorType = "string",
			defaultValue = ""
	)
	@SimpleProperty(description = "Secret key that matches SECRET_KEY in your script")
	public void SecretKey(String key) {
		this.secretKey = key.trim();
	}

	@SimpleProperty(description = "Returns the current secret key.")
	public String SecretKey() {
		return secretKey;
	}

	// ================================================================
	// INITIALIZE (runtime alternative to designer properties)
	// ================================================================

	@SimpleFunction(description =
			"Initialize the sender with your server URL and secret key. " +
			"Call this once at app startup. " +
			"serverUrl: full URL to deployed web-app. " +
			"secretKey: must match SECRET_KEY in your script.")
	public void Initialize(String serverUrl, String secretKey) {
		this.serverUrl = serverUrl.trim();
		this.secretKey = secretKey.trim();
	}

	// ================================================================
	// SEND DATA TO TOKEN
	// ================================================================

	@SimpleFunction(description =
			"Sends a data-only message to all devices subscribed to a topic.\n" +
			"No notification is shown automatically — the app handles display.\n" +
			"Delivered to MessageReceived event on all subscribed devices.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • topic    — FCM topic name (without /topics/ prefix)\n" +
			"  • data     — Dictionary of key-value pairs\n" +
			"  • callback — procedure(success, messageId, error)")
	public void SendDataToTopic(
			final String topic,
			final YailDictionary data,
			final YailProcedure callback
	) {
		if (!validateConfig("SendDataToTopic", callback)) return;
		if (topic.isEmpty()) { fireCallback(callback, false, "", "Topic cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildLegacyDataPayload("topic", topic, data);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	@SimpleFunction(description =
			"Sends a data-only message to a single device token.\n" +
			"No notification is shown automatically — the app handles display.\n" +
			"Delivered to MessageReceived event on the target device.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • token    — FCM registration token of target device\n" +
			"  • data     — Dictionary of key-value pairs\n" +
			"  • callback — procedure(success, messageId, error)")
	public void SendDataToToken(
			final String token,
			final YailDictionary data,
			final YailProcedure callback
	) {
		if (!validateConfig("SendDataToToken", callback)) return;
		if (token.isEmpty()) { fireCallback(callback, false, "", "Token cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildLegacyDataPayload("token", token, data);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	@SimpleFunction(description =
			"Sends a data-only message to multiple device tokens (up to 500).\n" +
			"No notification is shown automatically — the app handles display.\n" +
			"Delivered to MessageReceived event on each target device.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • tokens   — List of FCM registration token strings\n" +
			"  • data     — Dictionary of key-value pairs\n" +
			"  • callback — procedure(success, messageId, error)\n" +
			"               messageId contains summary e.g. '5/5 sent'")
	public void SendDataToMultipleTokens(
			final YailList tokens,
			final YailDictionary data,
			final YailProcedure callback
	) {
		if (!validateConfig("SendDataToMultipleTokens", callback)) return;
		if (tokens.size() == 0) { fireCallback(callback, false, "", "Token list cannot be empty"); return; }
		if (tokens.size() > 500) { fireCallback(callback, false, "", "Maximum 500 tokens per batch"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildLegacyDataPayload("multicast", "", data);
				JSONArray tokenArray = new JSONArray();
				for (int i = 1; i <= tokens.size(); i++) tokenArray.put(tokens.getString(i));
				payload.put("tokens", tokenArray);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// SEND NOTIFICATION FUNCTIONS
	// ================================================================

	@SimpleFunction(description =
			"Sends a notification message to all devices subscribed to a topic.\n" +
			"Notification is built and shown by the receiver extension.\n" +
			"Delivered to NotificationReceived event on all subscribed devices.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • topic              — FCM topic name (without /topics/ prefix)\n" +
			"  • smallIcon          — Small icon for status bar (Full HTTPS image URL or image in asset)\n" +
			"  • notificationStyle  — Notification style (basic, big text, big picture, individual/group message)\n" +
			"  • extraData          — Dictionary of extra key-value pairs\n" +
			"  • callback           — procedure(success, messageId, error)")
	public void SendNotificationToTopic(
			final String topic,
			final String smallIcon,
			final YailDictionary notificationStyle,
			final YailDictionary extraData,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToTopic", callback)) return;
		if (topic.isEmpty()) { fireCallback(callback, false, "", "Topic cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildNotificationPayload("topic", topic, smallIcon, notificationStyle, extraData);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	@SimpleFunction(description =
			"Sends a notification to a single device token.\n" +
			"Notification is built and shown by the receiver extension.\n" +
			"Delivered to NotificationReceived event on the target device.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • token        — FCM registration token of target device\n" +
			"  • smallIcon          — Small icon for status bar (Full HTTPS image URL or image in asset)\n" +
			"  • notificationStyle  — Notification style (basic, big text, big picture, individual/group message)\n" +
			"  • extraData          — Dictionary of extra key-value pairs\n" +
			"  • callback           — procedure(success, messageId, error)")
	public void SendNotificationToToken(
			final String token,
			final String smallIcon,
			final YailDictionary notificationStyle,
			final YailDictionary extraData,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToToken", callback)) return;
		if (token.isEmpty()) { fireCallback(callback, false, "", "Token cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildNotificationPayload("token", token, smallIcon, notificationStyle, extraData);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	@SimpleFunction(description =
			"Sends a notification to multiple device tokens (up to 500).\n" +
			"Notification is built and shown by the receiver extension on each device.\n" +
			"Delivered to NotificationReceived event on each target device.\n" +
			".\n===============================================================\n.\n" +
			"Parameters:\n" +
			"  • tokens             — List of FCM registration token strings\n" +
			"  • smallIcon          — Small icon for status bar (Full HTTPS image URL or image in asset)\n" +
			"  • notificationStyle  — Notification style (basic, big text, big picture, individual/group message)\n" +
			"  • extraData          — Dictionary of extra key-value pairs\n" +
			"  • callback           — procedure(success, messageId, error)")
	public void SendNotificationToMultipleTokens(
			final YailList tokens,
			final String smallIcon,
			final YailDictionary notificationStyle,
			final YailDictionary extraData,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToMultipleTokens", callback)) return;
		if (tokens.size() == 0) { fireCallback(callback, false, "", "Token list cannot be empty"); return; }
		if (tokens.size() > 500) { fireCallback(callback, false, "", "Maximum 500 tokens per batch"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildNotificationPayload("multicast", "", smallIcon, notificationStyle, extraData);
				JSONArray tokenArray = new JSONArray();
				for (int i = 1; i <= tokens.size(); i++) tokenArray.put(tokens.getString(i));
				payload.put("tokens", tokenArray);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// COMPACT STYLE BUILDER BLOCKS
	// ================================================================

	@SimpleFunction(description = "Creates a basic notification style dictionary configuration payload.")
	public YailDictionary CreateBasicStyle(String title, String body) {
		YailDictionary dict = new YailDictionary();
		dict.put("style", "basic");
		dict.put("title", title);
		dict.put("body", body);
		return dict;
	}

	@SimpleFunction(description = "Creates an expanded big text style dictionary configuration payload.")
	public YailDictionary CreateBigTextStyle(String title, String body) {
		YailDictionary dict = new YailDictionary();
		dict.put("style", "bigText");
		dict.put("title", title);
		dict.put("body", body);
		return dict;
	}

	@SimpleFunction(description = "Creates an expanded big picture style dictionary configuration payload.")
	public YailDictionary CreateBigPictureStyle(String title, String body, String largeIcon, String bigPicture) {
		YailDictionary dict = new YailDictionary();
		dict.put("style", "bigPicture");
		dict.put("title", title);
		dict.put("body", body);
		dict.put("largeIcon", largeIcon);
		dict.put("bigPicture", bigPicture);
		return dict;
	}

	@SimpleFunction(description = "Creates an individual 1-on-1 MessagingStyle notification dictionary configuration layout.")
	public YailDictionary CreateIndividualMessageStyle(String personId, String personName, String personIcon, String message) {
		YailDictionary dict = new YailDictionary();
		dict.put("style", "individualMessage");
		dict.put("personId", personId);
		dict.put("personName", personName);
		dict.put("personIcon", personIcon);
		dict.put("message", message);
		return dict;
	}

	@SimpleFunction(description = "Creates a persistent group MessagingStyle notification dictionary configuration layout.")
	public YailDictionary CreateGroupMessageStyle(String groupId, String groupName, String groupIcon, String personId, String personName, String personIcon, String message) {
		YailDictionary dict = new YailDictionary();
		dict.put("style", "groupMessage");
		dict.put("groupId", groupId);
		dict.put("groupName", groupName);
		dict.put("groupIcon", groupIcon);
		dict.put("personId", personId);
		dict.put("personName", personName);
		dict.put("personIcon", personIcon);
		dict.put("message", message);
		return dict;
	}

	// ================================================================
	// PAYLOAD BUILD ENGINE ARCHITECTURE
	// ================================================================

	private JSONObject buildNotificationPayload(
			String targetType,
			String target,
			String smallIcon,
			YailDictionary styleDict,
			YailDictionary extraData) throws Exception {

		JSONObject payload = new JSONObject();
		payload.put("secret", secretKey);
		payload.put("action", "send_notification");
		payload.put("target_type", targetType);
		payload.put("target", target);
		payload.put("small_icon", smallIcon);

		// Encode the style dictionary nested mapping component
		payload.put("notificationStyle", yailDictToJson(styleDict));

		// Encode the root data property blocks
		payload.put("data", yailDictToJson(extraData));
		return payload;
	}

	private JSONObject buildLegacyDataPayload(String targetType, String target, YailDictionary data) throws Exception {
		JSONObject payload = new JSONObject();
		payload.put("secret", secretKey);
		payload.put("action", "send_data");
		payload.put("target_type", targetType);
		payload.put("target", target);
		payload.put("data", yailDictToJson(data));
		return payload;
	}

	// ================================================================
	// JSON RECURSIVE CONVERTERS / HTTP / CALLBACKS
	// ================================================================

	private JSONObject yailDictToJson(YailDictionary dict) throws Exception {
		JSONObject json = new JSONObject();
		if (dict == null) return json;

		for (Object keyObj : dict.keySet()) {
			String key = keyObj.toString();
			Object val = dict.get(keyObj);

			if (val instanceof YailDictionary) {
				json.put(key, yailDictToJson((YailDictionary) val));
			} else if (val instanceof YailList) {
				json.put(key, yailListToJsonArray((YailList) val));
			} else if (val == null) {
				json.put(key, JSONObject.NULL);
			} else {
				if (val instanceof Integer || val instanceof Long) {
					json.put(key, ((Number) val).longValue());
				} else if (val instanceof Double || val instanceof Float) {
					json.put(key, ((Number) val).doubleValue());
				} else if (val instanceof Boolean) {
					json.put(key, (Boolean) val);
				} else {
					json.put(key, val.toString());
				}
			}
		}
		return json;
	}

	private JSONArray yailListToJsonArray(YailList list) throws Exception {
		JSONArray arr = new JSONArray();
		for (int i = 1; i <= list.size(); i++) {
			Object val = list.getObject(i);
			if (val instanceof YailDictionary) {
				arr.put(yailDictToJson((YailDictionary) val));
			} else if (val instanceof YailList) {
				arr.put(yailListToJsonArray((YailList) val));
			} else {
				arr.put(val.toString());
			}
		}
		return arr;
	}

	private void performRequest(JSONObject payload, YailProcedure callback) {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(serverUrl);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			conn.setRequestProperty("Accept", "application/json");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setDoOutput(true);
			conn.setDoInput(true);

			byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(bodyBytes.length);
			OutputStream os = conn.getOutputStream();
			os.write(bodyBytes);
			os.flush();
			os.close();

			int responseCode = conn.getResponseCode();
			BufferedReader reader;
			if (responseCode >= 200 && responseCode < 300) {
				reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
			} else {
				reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
			}

			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			reader.close();

			parseAndFireCallback(responseCode, sb.toString(), callback);

		} catch (Exception e) {
			Log.e(TAG, "HTTP request failed", e);
			fireCallback(callback, false, "", "Network error: " + e.getMessage());
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	private void parseAndFireCallback(int httpCode, String responseBody, YailProcedure callback) {
		try {
			JSONObject response = new JSONObject(responseBody);
			boolean success   = response.optBoolean("success", false);
			String messageId  = response.optString("message_id", "");
			String error      = response.optString("error", "");

			if (httpCode == 401) {
				fireCallback(callback, false, "", "Unauthorized: secret key mismatch");
			} else if (httpCode == 400) {
				fireCallback(callback, false, "", "Bad request: " + error);
			} else if (httpCode >= 500) {
				fireCallback(callback, false, "", "Server error " + httpCode + ": " + error);
			} else {
				fireCallback(callback, success, messageId, success ? "" : (error.isEmpty() ? "Unknown error" : error));
			}
		} catch (Exception e) {
			String snippet = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
			fireCallback(callback, false, "", "Invalid server response (HTTP " + httpCode + "): " + snippet);
		}
	}

	private void fireCallback(final YailProcedure callback, final boolean success, final String messageId, final String error) {
		if (callback == null) return;
		mainHandler.post(() -> {
			try {
				callback.call(success, messageId, error);
			} catch (Exception e) {
				Log.e(TAG, "Callback dispatch failed", e);
			}
		});
	}

	private boolean validateConfig(String operation, YailProcedure callback) {
		if (serverUrl.isEmpty() || (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://"))) {
			fireCallback(callback, false, "", operation + ": Invalid or empty ServerUrl — must start with https://");
			return false;
		}
		if (secretKey.isEmpty()) {
			fireCallback(callback, false, "", operation + ": SecretKey not set. Call Initialize() first.");
			return false;
		}
		return true;
	}
}