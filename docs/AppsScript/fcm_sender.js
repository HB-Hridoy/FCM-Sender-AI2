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

var PROJECT_ID = '';

// Paste your entire service-account.json contents here
// Firebase Console → Project Settings → Service Accounts → Generate new private key
var SERVICE_ACCOUNT = {};

// ====================================================================
// DO NOT EDIT BELOW THIS LINE
// ====================================================================

// ====================================================================
// CONSTANTS
// ====================================================================

var FCM_ENDPOINT   = 'https://fcm.googleapis.com/v1/projects/' + PROJECT_ID + '/messages:send';
var OAUTH_ENDPOINT = 'https://oauth2.googleapis.com/token';
var FCM_SCOPE      = 'https://www.googleapis.com/auth/firebase.messaging';
var TOKEN_KEY      = 'fcm_access_token';
var EXPIRY_KEY     = 'fcm_token_expiry';

// Reserved system keys injected into data payload
var KEY_TITLE             = 'fcm_title';
var KEY_BODY              = 'fcm_body';
var KEY_SMALL_ICON        = 'fcm_small_icon';
var KEY_NOTIFICATION_STYLE = 'fcm_notification_style';

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

    if (!input.action || !input.target_type) {
      return respond(out, false, '', 'Missing: action or target_type');
    }

    var action     = input.action;
    var targetType = input.target_type;
    var target     = input.target  || '';
    var tokens     = input.tokens  || [];
    var data       = input.data    || {};

    // Stringify all user data values
    var stringData = {};
    for (var k in data) {
      if (data.hasOwnProperty(k)) stringData[k] = String(data[k]);
    }

    if (action === 'send_notification') {
      // ── Inject small icon ─────────────────────────────────────
      if (input.small_icon && input.small_icon !== '') {
        stringData[KEY_SMALL_ICON] = input.small_icon;
      }

      // ── Validate and inject notificationStyle ─────────────────
      if (!input.notificationStyle || !input.notificationStyle.style) {
        return respond(out, false, '', 'notificationStyle.style is required for send_notification');
      }

      var ns = input.notificationStyle;
      var styleName = ns.style;
      var validStyles = ['basic', 'bigText', 'bigPicture', 'individualMessage', 'groupMessage'];

      if (validStyles.indexOf(styleName) === -1) {
        return respond(out, false, '', 'Invalid style. Must be one of: ' + validStyles.join(', '));
      }

      // Validate required fields per style
      var err = validateStyle(ns);
      if (err) return respond(out, false, '', err);

      // Serialize notificationStyle object as JSON string into data payload
      // FCMService on device parses this back to JSONObject
      stringData[KEY_NOTIFICATION_STYLE] = JSON.stringify(ns);

      // Also inject fcm_title/fcm_body at top level for NotificationReceived event
      if (ns.title) stringData[KEY_TITLE] = ns.title;
      if (ns.message) stringData[KEY_BODY] = ns.message; // messaging styles use 'message'
      if (ns.body)    stringData[KEY_BODY] = ns.body;     // other styles use 'body'
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
// STYLE VALIDATION
// ====================================================================

function validateStyle(ns) {
  var style = ns.style;

  if (style === 'basic' || style === 'bigText') {
    if (!ns.title) return style + ': title is required';
    if (!ns.body)  return style + ': body is required';
  }

  if (style === 'bigPicture') {
    if (!ns.title)      return 'bigPicture: title is required';
    if (!ns.body)       return 'bigPicture: body is required';
    if (!ns.bigPicture) return 'bigPicture: bigPicture URL is required';
  }

  if (style === 'individualMessage') {
    if (!ns.personId)   return 'individualMessage: personId is required';
    if (!ns.personName) return 'individualMessage: personName is required';
    if (!ns.message)    return 'individualMessage: message is required';
  }

  if (style === 'groupMessage') {
    if (!ns.groupId)    return 'groupMessage: groupId is required';
    if (!ns.groupName)  return 'groupMessage: groupName is required';
    if (!ns.personId)   return 'groupMessage: personId is required';
    if (!ns.personName) return 'groupMessage: personName is required';
    if (!ns.message)    return 'groupMessage: message is required';
  }

  return null; // valid
}

// ====================================================================
// SEND — Single
// ====================================================================

function sendSingle(targetType, target, data, accessToken) {
  if (!target && targetType !== 'condition') {
    return { success: false, message_id: '', error: 'target is required' };
  }
  return callFCM(buildPayload(targetType, target, data), accessToken);
}

// ====================================================================
// SEND — Multicast
// ====================================================================

function sendMulticast(tokens, data, accessToken) {
  if (!tokens || tokens.length === 0) {
    return { success: false, message_id: '', error: 'tokens array required' };
  }
  if (tokens.length > 500) {
    return { success: false, message_id: '', error: 'Maximum 500 tokens' };
  }

  var successCount = 0, failCount = 0, lastError = '';

  for (var i = 0; i < tokens.length; i++) {
    var token = String(tokens[i]).trim();
    if (!token) continue;
    var result = callFCM(buildPayload('token', token, data), accessToken);
    if (result.success) { successCount++; }
    else { failCount++; lastError = result.error; }
  }

  var total = successCount + failCount;
  var allOk = failCount === 0;
  return {
    success:    allOk,
    message_id: successCount + '/' + total + ' sent',
    sent:       successCount,
    failed:     failCount,
    error:      allOk ? '' : 'Some failed. Last: ' + lastError
  };
}

// ====================================================================
// PAYLOAD BUILDER — always data-only, device builds the notification
// ====================================================================

function buildPayload(targetType, target, data) {
  var message = {};

  switch (targetType) {
    case 'token':     message.token     = target; break;
    case 'topic':     message.topic     = target.replace(/^\/topics\//, ''); break;
    case 'condition': message.condition = target; break;
    default: throw new Error('Invalid target_type: ' + targetType);
  }

  if (data && Object.keys(data).length > 0) {
    message.data = data;
  }

  message.android = { priority: 'high' };
  message.apns    = {
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
    method: 'post', contentType: 'application/json',
    headers: { 'Authorization': 'Bearer ' + accessToken },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });

  var code = response.getResponseCode();
  var body;
  try { body = JSON.parse(response.getContentText()); }
  catch (e) { return { success: false, message_id: '', error: 'Invalid response (HTTP ' + code + ')' }; }

  if (code === 200 && body.name) return { success: true, message_id: body.name, error: '' };

  var errMsg = 'FCM error (HTTP ' + code + ')';
  if (body.error && body.error.message) errMsg = body.error.message;
  return { success: false, message_id: '', error: errMsg };
}

// ====================================================================
// OAUTH2
// ====================================================================

function getAccessToken() {
  var props  = PropertiesService.getScriptProperties();
  var cached = props.getProperty(TOKEN_KEY);
  var expiry = props.getProperty(EXPIRY_KEY);
  if (cached && expiry && parseInt(expiry) > Date.now() + 300000) return cached;

  var response = UrlFetchApp.fetch(OAUTH_ENDPOINT, {
    method: 'post', contentType: 'application/x-www-form-urlencoded',
    payload: { grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: buildJWT() },
    muteHttpExceptions: true
  });

  var data = JSON.parse(response.getContentText());
  if (!data.access_token) throw new Error('OAuth2 failed: ' + JSON.stringify(data));

  props.setProperty(TOKEN_KEY,  data.access_token);
  props.setProperty(EXPIRY_KEY, String(Date.now() + (data.expires_in || 3600) * 1000));
  return data.access_token;
}

function buildJWT() {
  var now    = Math.floor(Date.now() / 1000);
  var header = Utilities.base64EncodeWebSafe(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).replace(/=+$/, '');
  var claims = Utilities.base64EncodeWebSafe(JSON.stringify({
    iss: SERVICE_ACCOUNT.client_email, scope: FCM_SCOPE,
    aud: OAUTH_ENDPOINT, iat: now, exp: now + 3600
  })).replace(/=+$/, '');
  var input = header + '.' + claims;
  var sig   = Utilities.base64EncodeWebSafe(
    Utilities.computeRsaSha256Signature(input, SERVICE_ACCOUNT.private_key)
  ).replace(/=+$/, '');
  return input + '.' + sig;
}

function respond(out, success, messageId, error) {
  out.setContent(JSON.stringify({ success: success, message_id: messageId || '', error: error || '' }));
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