/**
 * FCM Sender — Google Apps Script Web App
 * Uses service account JWT auth for FCM v1 API.
 *
 * SETUP:
 * 1. Paste your service account JSON into SERVICE_ACCOUNT below
 * 2. Set SECRET_KEY to any strong password
 * 3. Set PROJECT_ID to your Firebase project ID
 * 4. Deploy as Web App → Execute as: Me → Anyone can access
 * 5. Use the Web App URL in FCMSenderExtension.Initialize()
 */

// ====================================================================
// CONFIGURATION
// ====================================================================

var SECRET_KEY = 'your-strong-secret-key-here';

var PROJECT_ID = 'fcm-messaging-1a4a1';

// Paste your entire service-account.json contents here
// Firebase Console → Project Settings → Service Accounts → Generate new private key
var SERVICE_ACCOUNT = {};

// ====================================================================
// DO NOT EDIT BELOW THIS LINE
// ====================================================================

// ====================================================================
// CONSTANTS
// ====================================================================

var FCM_ENDPOINT  = 'https://fcm.googleapis.com/v1/projects/' + PROJECT_ID + '/messages:send';
var OAUTH_ENDPOINT = 'https://oauth2.googleapis.com/token';
var FCM_SCOPE     = 'https://www.googleapis.com/auth/firebase.messaging';

// Reserved data keys — receiver extension reads these to build notifications
var KEY_TITLE    = 'fcm_title';
var KEY_BODY     = 'fcm_body';
var KEY_IMAGE    = 'fcm_image';
var KEY_SMALL_ICON = 'fcm_small_icon';
var KEY_LARGE_ICON = 'fcm_large_icon';
var KEY_MSG_ID   = 'fcm_message_id';
var TOKEN_KEY    = 'fcm_access_token';
var EXPIRY_KEY   = 'fcm_token_expiry';

// ====================================================================
// ENTRY POINT
// ====================================================================

function doPost(e) {
  var out = ContentService.createTextOutput();
  out.setMimeType(ContentService.MimeType.JSON);

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return respond(out, false, '', 'Empty request body');
    }

    var input = JSON.parse(e.postData.contents);

    if (!input.secret || input.secret !== SECRET_KEY) {
      return respond(out, false, '', 'Unauthorized');
    }

    var required = ['action', 'target_type'];
    for (var i = 0; i < required.length; i++) {
      if (!input[required[i]]) {
        return respond(out, false, '', 'Missing: ' + required[i]);
      }
    }

    var action     = input.action;      // 'send_data' | 'send_notification'
    var targetType = input.target_type; // 'token' | 'topic' | 'multicast' | 'condition'
    var target     = input.target    || '';
    var tokens     = input.tokens    || [];
    var data       = input.data      || {};

    // Stringify all data values — FCM v1 requires string values
    var stringData = {};
    for (var k in data) {
      if (data.hasOwnProperty(k)) stringData[k] = String(data[k]);
    }

    // For notifications — inject reserved keys into data payload
    if (action === 'send_notification') {
      if (!input.title || !input.body) {
        return respond(out, false, '', 'title and body required for send_notification');
      }
      stringData[KEY_TITLE] = input.title;
      stringData[KEY_BODY]  = input.body;
      
      if (input.image && input.image !== '') 
        stringData[KEY_IMAGE] = input.image;

      if (input.small_icon && input.small_icon !== '')
        stringData[KEY_SMALL_ICON] = input.small_icon;  

      if (input.large_icon && input.large_icon !== '')
        stringData[KEY_LARGE_ICON] = input.large_icon;  

    }

    var accessToken = getAccessToken();
    var result;

    if (targetType === 'multicast') {
      result = sendMulticast(tokens, stringData, accessToken);
    } else {
      result = sendSingle(targetType, target, stringData, accessToken);
    }

    out.setContent(JSON.stringify(result));
    return out;

  } catch (err) {
    return respond(out, false, '', 'Script error: ' + err.message);
  }
}

// ====================================================================
// SEND — Single target (token / topic / condition)
// ====================================================================

function sendSingle(targetType, target, data, accessToken) {
  if (!target && targetType !== 'condition') {
    return { success: false, message_id: '', error: 'target is required' };
  }
  var payload = buildPayload(targetType, target, data);
  return callFCM(payload, accessToken);
}

// ====================================================================
// SEND — Multicast (multiple tokens)
// ====================================================================

function sendMulticast(tokens, data, accessToken) {
  if (!tokens || tokens.length === 0) {
    return { success: false, message_id: '', error: 'tokens array required for multicast' };
  }
  if (tokens.length > 500) {
    return { success: false, message_id: '', error: 'Maximum 500 tokens per batch' };
  }

  var successCount = 0;
  var failCount    = 0;
  var lastError    = '';

  for (var i = 0; i < tokens.length; i++) {
    var token = String(tokens[i]).trim();
    if (!token) continue;
    var payload = buildPayload('token', token, data);
    var result  = callFCM(payload, accessToken);
    if (result.success) {
      successCount++;
    } else {
      failCount++;
      lastError = result.error;
    }
  }

  var total   = successCount + failCount;
  var allOk   = failCount === 0;

  return {
    success:    allOk,
    message_id: successCount + '/' + total + ' sent',
    sent:       successCount,
    failed:     failCount,
    error:      allOk ? '' : 'Some failed. Last: ' + lastError
  };
}

// ====================================================================
// PAYLOAD BUILDER
// ALL messages are data-only — no 'notification' field.
// The receiver's MyFCMService reads fcm_title/fcm_body and builds
// the notification on-device. This gives full control over display.
// ====================================================================

function buildPayload(targetType, target, data) {
  var message = {};

  // Target
  switch (targetType) {
    case 'token':
      message.token = target;
      break;
    case 'topic':
      // FCM v1 uses bare topic name — no /topics/ prefix
      message.topic = target.replace(/^\/topics\//, '');
      break;
    case 'condition':
      message.condition = target;
      break;
    default:
      throw new Error('Invalid target_type: ' + targetType);
  }

  // Data payload — all values must be strings
  if (data && Object.keys(data).length > 0) {
    message.data = data;
  }

  // Android config — HIGH priority for data-only messages.
  // Critical: without this, data messages in background are
  // deferred by Doze mode and may not arrive for hours.
  message.android = {
    priority: 'high'
  };

  // APNs (iOS) — wake app in background
  message.apns = {
    headers: { 'apns-priority': '10' },
    payload: { aps: { 'content-available': 1 } }
  };

  return { message: message };
}

// ====================================================================
// FCM API CALL
// ====================================================================

function callFCM(payload, accessToken) {
  var response = UrlFetchApp.fetch(FCM_ENDPOINT, {
    method:             'post',
    contentType:        'application/json',
    headers:            { 'Authorization': 'Bearer ' + accessToken },
    payload:            JSON.stringify(payload),
    muteHttpExceptions: true
  });

  var code = response.getResponseCode();
  var body;

  try {
    body = JSON.parse(response.getContentText());
  } catch (e) {
    return { success: false, message_id: '',
             error: 'Invalid FCM response (HTTP ' + code + ')' };
  }

  if (code === 200 && body.name) {
    return { success: true, message_id: body.name, error: '' };
  }

  var errMsg = 'FCM error (HTTP ' + code + ')';
  if (body.error && body.error.message) errMsg = body.error.message;
  else if (body.error && body.error.status) errMsg = body.error.status;

  return { success: false, message_id: '', error: errMsg };
}

// ====================================================================
// OAUTH2 — Service Account JWT
// ====================================================================

function getAccessToken() {
  var props      = PropertiesService.getScriptProperties();
  var cached     = props.getProperty(TOKEN_KEY);
  var expiry     = props.getProperty(EXPIRY_KEY);

  if (cached && expiry && parseInt(expiry) > Date.now() + 300000) {
    return cached;
  }

  var jwt      = buildJWT();
  var response = UrlFetchApp.fetch(OAUTH_ENDPOINT, {
    method:      'post',
    contentType: 'application/x-www-form-urlencoded',
    payload: {
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion:  jwt
    },
    muteHttpExceptions: true
  });

  var data = JSON.parse(response.getContentText());
  if (!data.access_token) {
    throw new Error('OAuth2 failed: ' + JSON.stringify(data));
  }

  var token     = data.access_token;
  var expiresIn = data.expires_in || 3600;

  props.setProperty(TOKEN_KEY,  token);
  props.setProperty(EXPIRY_KEY, String(Date.now() + expiresIn * 1000));

  return token;
}

function buildJWT() {
  var now = Math.floor(Date.now() / 1000);

  var header = Utilities.base64EncodeWebSafe(
    JSON.stringify({ alg: 'RS256', typ: 'JWT' })
  ).replace(/=+$/, '');

  var claims = Utilities.base64EncodeWebSafe(
    JSON.stringify({
      iss:   SERVICE_ACCOUNT.client_email,
      scope: FCM_SCOPE,
      aud:   OAUTH_ENDPOINT,
      iat:   now,
      exp:   now + 3600
    })
  ).replace(/=+$/, '');

  var input     = header + '.' + claims;
  var signature = Utilities.computeRsaSha256Signature(
    input, SERVICE_ACCOUNT.private_key);
  var sig = Utilities.base64EncodeWebSafe(signature).replace(/=+$/, '');

  return input + '.' + sig;
}

// ====================================================================
// HELPERS
// ====================================================================

function respond(out, success, messageId, error) {
  out.setContent(JSON.stringify({
    success:    success,
    message_id: messageId || '',
    error:      error     || ''
  }));
  return out;
}

// ====================================================================
// TEST — run from Apps Script editor to verify setup
// ====================================================================

function testSetup() {
  Logger.log('Project: ' + PROJECT_ID);
  Logger.log('Service account: ' + SERVICE_ACCOUNT.client_email);
  try {
    var token = getAccessToken();
    Logger.log('Token: ' + token.substring(0, 20) + '...');
    var r = UrlFetchApp.fetch(FCM_ENDPOINT, {
      method: 'post', contentType: 'application/json',
      headers: { 'Authorization': 'Bearer ' + token },
      payload: JSON.stringify({}), muteHttpExceptions: true
    });
    var code = r.getResponseCode();
    Logger.log('FCM response: ' + code + (code === 400 ? ' ✓ Auth OK' : ' ✗ Auth failed'));
  } catch (e) {
    Logger.log('ERROR: ' + e.message);
  }
}