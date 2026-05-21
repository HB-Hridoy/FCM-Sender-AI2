package com.hridoy.fcmsender;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
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
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@DesignerComponent(
		version = 3,
		description = "FCM Sender Extension — sends push notifications via your PHP backend. " +
				"Requires fcm_sender.php hosted on your server.",
		category = ComponentCategory.EXTENSION,
		nonVisible = true,
		iconName = "icon.png"
)
public class FCMSender extends AndroidNonvisibleComponent {

	private static final String TAG = "FCMSender";
	private static final int CONNECT_TIMEOUT_MS = 10000;  // 10 seconds
	private static final int READ_TIMEOUT_MS    = 15000;  // 15 seconds

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
	@SimpleProperty(description = "Base URL of your fcm_sender.php server. " +
			"Example: https://yourserver.com/fcm_sender.php")
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
	@SimpleProperty(description = "Secret key that matches SECRET_KEY in your fcm_sender.php. " +
			"Never share this publicly.")
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
					"serverUrl: full URL to fcm_sender.php. " +
					"secretKey: must match SECRET_KEY in your PHP file.")
	public void Initialize(String serverUrl, String secretKey) {
		this.serverUrl = serverUrl.trim();
		this.secretKey = secretKey.trim();
	}

	// ================================================================
	// SEND TO SINGLE TOKEN
	// ================================================================

	// ================================================================
	// SEND DATA TO TOKEN
	// ================================================================

	@SimpleFunction(description =
			"Sends a data-only message to a single device token.\n" +
					"No notification is shown automatically — the app handles display.\n" +
					"Delivered to MessageReceived event on the target device.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • token    — FCM registration token of target device\n" +
					"  • data     — AI2 dictionary of key-value pairs\n" +
					"  • callback — procedure(success, messageId, error)")
	public void SendDataToToken(
			final String token,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendDataToToken", callback)) return;
		if (token.isEmpty()) { fireCallback(callback, false, "", "Token cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_data", "token", token, "", "", data, "");
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// SEND NOTIFICATION TO TOKEN
	// ================================================================

	@SimpleFunction(description =
			"Sends a notification message to a single device token.\n" +
					"Notification is built and shown by the receiver extension.\n" +
					"Delivered to NotificationReceived event on the target device.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • token        — FCM registration token of target device\n" +
					"  • title        — notification title\n" +
					"  • body         — notification body text\n" +
					"  • imageUrl     — full HTTPS image URL, or empty string\n" +
					"  • targetScreen — screen to open on tap (e.g. Screen2), or empty for Screen1\n" +
					"  • data         — AI2 dictionary of extra key-value pairs\n" +
					"  • callback     — procedure(success, messageId, error)")
	public void SendNotificationToToken(
			final String token,
			final String title,
			final String body,
			final String imageUrl,
			final String targetScreen,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToToken", callback)) return;
		if (token.isEmpty()) { fireCallback(callback, false, "", "Token cannot be empty"); return; }
		if (title.isEmpty()) { fireCallback(callback, false, "", "Title cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_notification", "token", token,
						title, body, data, imageUrl);
				if (!targetScreen.isEmpty()) payload.put("screen", targetScreen);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// SEND DATA TO TOPIC
	// ================================================================

	@SimpleFunction(description =
			"Sends a data-only message to all devices subscribed to a topic.\n" +
					"No notification is shown automatically — the app handles display.\n" +
					"Delivered to MessageReceived event on all subscribed devices.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • topic    — FCM topic name (without /topics/ prefix)\n" +
					"  • data     — AI2 dictionary of key-value pairs\n" +
					"  • callback — procedure(success, messageId, error)")
	public void SendDataToTopic(
			final String topic,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendDataToTopic", callback)) return;
		if (topic.isEmpty()) { fireCallback(callback, false, "", "Topic cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_data", "topic", topic, "", "", data, "");
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// SEND NOTIFICATION TO TOPIC
	// ================================================================

	@SimpleFunction(description =
			"Sends a notification message to all devices subscribed to a topic.\n" +
					"Notification is built and shown by the receiver extension.\n" +
					"Delivered to NotificationReceived event on all subscribed devices.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • topic        — FCM topic name (without /topics/ prefix)\n" +
					"  • title        — notification title\n" +
					"  • body         — notification body text\n" +
					"  • imageUrl     — full HTTPS image URL, or empty string\n" +
					"  • targetScreen — screen to open on tap (e.g. Screen2), or empty for Screen1\n" +
					"  • data         — AI2 dictionary of extra key-value pairs\n" +
					"  • callback     — procedure(success, messageId, error)")
	public void SendNotificationToTopic(
			final String topic,
			final String title,
			final String body,
			final String imageUrl,
			final String targetScreen,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToTopic", callback)) return;
		if (topic.isEmpty()) { fireCallback(callback, false, "", "Topic cannot be empty"); return; }
		if (title.isEmpty()) { fireCallback(callback, false, "", "Title cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_notification", "topic", topic,
						title, body, data, imageUrl);
				if (!targetScreen.isEmpty()) payload.put("screen", targetScreen);
				performRequest(payload, callback);
			} catch (Exception e) {
				fireCallback(callback, false, "", "Build error: " + e.getMessage());
			}
		});
	}

	// ================================================================
	// SEND DATA TO MULTIPLE TOKENS
	// ================================================================

	@SimpleFunction(description =
			"Sends a data-only message to multiple device tokens (up to 500).\n" +
					"No notification is shown automatically — the app handles display.\n" +
					"Delivered to MessageReceived event on each target device.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • tokens   — AI2 list of FCM registration token strings\n" +
					"  • data     — AI2 dictionary of key-value pairs\n" +
					"  • callback — procedure(success, messageId, error)\n" +
					"               messageId contains summary e.g. '5/5 sent'")
	public void SendDataToMultipleTokens(
			final YailList tokens,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendDataToMultipleTokens", callback)) return;
		if (tokens.size() == 0) { fireCallback(callback, false, "", "Token list cannot be empty"); return; }
		if (tokens.size() > 500) { fireCallback(callback, false, "", "Maximum 500 tokens per batch"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_data", "multicast", "", "", "", data, "");
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
	// SEND NOTIFICATION TO MULTIPLE TOKENS
	// ================================================================

	@SimpleFunction(description =
			"Sends a notification message to multiple device tokens (up to 500).\n" +
					"Notification is built and shown by the receiver extension on each device.\n" +
					"Delivered to NotificationReceived event on each target device.\n" +
					".\n===============================================================\n.\n" +
					"Parameters:\n" +
					"  • tokens       — AI2 list of FCM registration token strings\n" +
					"  • title        — notification title\n" +
					"  • body         — notification body text\n" +
					"  • imageUrl     — full HTTPS image URL, or empty string\n" +
					"  • targetScreen — screen to open on tap, or empty for Screen1\n" +
					"  • data         — AI2 dictionary of extra key-value pairs\n" +
					"  • callback     — procedure(success, messageId, error)\n" +
					"                   messageId contains summary e.g. '5/5 sent'")
	public void SendNotificationToMultipleTokens(
			final YailList tokens,
			final String title,
			final String body,
			final String imageUrl,
			final String targetScreen,
			final YailDictionary data,
			final YailProcedure callback) {

		if (!validateConfig("SendNotificationToMultipleTokens", callback)) return;
		if (tokens.size() == 0) { fireCallback(callback, false, "", "Token list cannot be empty"); return; }
		if (tokens.size() > 500) { fireCallback(callback, false, "", "Maximum 500 tokens per batch"); return; }
		if (title.isEmpty()) { fireCallback(callback, false, "", "Title cannot be empty"); return; }

		executor.execute(() -> {
			try {
				JSONObject payload = buildPayload("send_notification", "multicast", "",
						title, body, data, imageUrl);
				if (!targetScreen.isEmpty()) payload.put("screen", targetScreen);
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
	// INTERNAL — Build payload JSON
	// ================================================================

	/**
	 * Builds the JSON payload sent to the backend.
	 * action: 'send_data' | 'send_notification'
	 * {
	 *   "secret":      "...",
	 *   "action":      "send_data|send_notification",
	 *   "target_type": "token|topic|multicast|condition",
	 *   "target":      "...",
	 *   "title":       "...",   // send_notification only
	 *   "body":        "...",   // send_notification only
	 *   "image":       "...",   // send_notification only, optional
	 *   "screen":      "...",   // optional
	 *   "data":        { "key": "value", ... }
	 * }
	 */
	private JSONObject buildPayload(
			String action,
			String targetType,
			String target,
			String title,
			String body,
			YailDictionary data,
			String imageUrl) throws Exception {

		JSONObject payload = new JSONObject();
		payload.put("secret",      secretKey);
		payload.put("action",      action);
		payload.put("target_type", targetType);
		payload.put("target",      target);

		if (!title.isEmpty())    payload.put("title", title);
		if (!body.isEmpty())     payload.put("body",  body);
		if (imageUrl != null && !imageUrl.isEmpty()) payload.put("image", imageUrl);

		payload.put("data", yailDictToJson(data));
		return payload;
	}

	/**
	 * Converts a YailDictionary to a JSONObject.
	 * Handles nested dictionaries and lists recursively.
	 */
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
				// Preserve numeric types — don't stringify everything
				if (val instanceof Integer || val instanceof Long) {
					json.put(key, ((Number) val).longValue());
				} else if (val instanceof Double || val instanceof Float) {
					json.put(key, ((Number) val).doubleValue());
				} else if (val instanceof Boolean) {
					json.put(key, (Boolean) val);
				} else {
					// FCM data payload values MUST be strings
					// per FCM v1 API spec — convert everything else
					json.put(key, val.toString());
				}
			}
		}
		return json;
	}

	/**
	 * Converts a YailList to a JSONArray recursively.
	 */
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

	// ================================================================
	// INTERNAL — HTTP Request
	// ================================================================

	/**
	 * Performs the HTTP POST to fcm_sender.php.
	 * Always runs on background thread.
	 * Fires callback on main thread.
	 */
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

			// Write request body
			byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(bodyBytes.length);
			OutputStream os = conn.getOutputStream();
			os.write(bodyBytes);
			os.flush();
			os.close();

			int responseCode = conn.getResponseCode();
			Log.d(TAG, "HTTP response code: " + responseCode);

			// Read response — use error stream if HTTP error
			BufferedReader reader;
			if (responseCode >= 200 && responseCode < 300) {
				reader = new BufferedReader(
						new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
			} else {
				reader = new BufferedReader(
						new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
			}

			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			reader.close();

			String responseBody = sb.toString();
			Log.d(TAG, "Response: " + responseBody);

			// Parse PHP response JSON
			// Expected: { "success": true/false, "message_id": "...", "error": "..." }
			parseAndFireCallback(responseCode, responseBody, callback);

		} catch (Exception e) {
			Log.e(TAG, "HTTP request failed", e);
			fireCallback(callback, false, "", "Network error: " + e.getMessage());
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	/**
	 * Parses the PHP JSON response and fires the callback.
	 *
	 * PHP response format:
	 * Success: { "success": true,  "message_id": "projects/xxx/messages/yyy" }
	 * Failure: { "success": false, "error": "description of what went wrong" }
	 * Multicast: { "success": true, "message_id": "5/5 sent", "failed": 0 }
	 */
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
				fireCallback(callback, success, messageId,
						success ? "" : (error.isEmpty() ? "Unknown error" : error));
			}

		} catch (Exception e) {
			// Response was not valid JSON — likely a PHP fatal error page
			String snippet = responseBody.length() > 200
					? responseBody.substring(0, 200) + "..."
					: responseBody;
			fireCallback(callback, false, "",
					"Invalid server response (HTTP " + httpCode + "): " + snippet);
		}
	}

	// ================================================================
	// INTERNAL — Callback dispatch
	// ================================================================

	/**
	 * Fires the YailProcedure callback on the main thread.
	 * Signature: callback(Boolean success, String messageId, String error)
	 */
	private void fireCallback(
			final YailProcedure callback,
			final boolean success,
			final String messageId,
			final String error) {

		if (callback == null) return;
		mainHandler.post(new Runnable() {
			@Override public void run() {
				try {
					callback.call(success, messageId, error);
				} catch (Exception e) {
					Log.e(TAG, "Callback dispatch failed", e);
				}
			}
		});
	}

	// ================================================================
	// INTERNAL — Validation
	// ================================================================

	private boolean validateConfig(String operation, YailProcedure callback) {
		if (serverUrl.isEmpty()) {
			fireCallback(callback, false, "",
					operation + ": ServerUrl not set. Call Initialize() first.");
			return false;
		}
		if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
			fireCallback(callback, false, "",
					operation + ": Invalid ServerUrl — must start with https://");
			return false;
		}
		if (secretKey.isEmpty()) {
			fireCallback(callback, false, "",
					operation + ": SecretKey not set. Call Initialize() first.");
			return false;
		}
		return true;
	}
}