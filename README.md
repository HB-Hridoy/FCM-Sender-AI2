<div align="center">
<h1><kbd><img width="333" height="151" alt="Firebase-Cloud-Messaging" src="https://github.com/user-attachments/assets/1c030a13-5f4c-4a92-80d3-a9f752cee1ad" /></kbd></h1>
Firebase Cloud Messaging sender extension. Developed by Hridoy.
</div>

## 📝 Specifications
* **
* 📦 **Package:** com.hridoy.fcmsender 
* 💾 **Size:** 16.70 KB 
* ⚙️ **Version:** 1.0.0 
* 📱 **Minimum API Level:** 14 
* 📅 **Updated On:** 21-05-2026 SAST
* 💻 **Built using:** [FAST](https://community.appinventor.mit.edu/t/fast-an-efficient-way-to-build-publish-extensions/129103?u=jewel) <small><mark>v6.1.0</mark></small>

## All Blocks

---

<details>
<summary><kbd>Methods (7)</kbd></summary>

### 1. Initialize
Initialize the sender with your server URL and secret key. Call this once at app startup. serverUrl: full URL to deployed web-app. secretKey: must match SECRET_KEY in your script.

| Parameter | Type
| - | - |
| serverUrl | text
| secretKey | text

### 2. SendDataToToken
Sends a data-only message to a single device token.
No notification is shown automatically — the app handles display.
Delivered to MessageReceived event on the target device.
.
===============================================================
.
Parameters:
• token    — FCM registration token of target device
• data     — Dictionary of key-value pairs
• callback — procedure(success, messageId, error)

| Parameter | Type
| - | - |
| token | text
| data | dictionary
| callback | any

### 3. SendNotificationToToken
Sends a notification message to a single device token.
Notification is built and shown by the receiver extension.
Delivered to NotificationReceived event on the target device.
.
===============================================================
.
Parameters:
• token        — FCM registration token of target device
• title        — notification title
• body         — notification body text
• imageUrl     — full HTTPS image URL, or empty string
• data         — Dictionary of extra key-value pairs
• callback     — procedure(success, messageId, error)

| Parameter | Type
| - | - |
| token | text
| title | text
| body | text
| imageUrl | text
| data | dictionary
| callback | any

### 4. SendDataToTopic
Sends a data-only message to all devices subscribed to a topic.
No notification is shown automatically — the app handles display.
Delivered to MessageReceived event on all subscribed devices.
.
===============================================================
.
Parameters:
• topic    — FCM topic name (without /topics/ prefix)
• data     — Dictionary of key-value pairs
• callback — procedure(success, messageId, error)

| Parameter | Type
| - | - |
| topic | text
| data | dictionary
| callback | any

### 5. SendNotificationToTopic
Sends a notification message to all devices subscribed to a topic.
Notification is built and shown by the receiver extension.
Delivered to NotificationReceived event on all subscribed devices.
.
===============================================================
.
Parameters:
• topic        — FCM topic name (without /topics/ prefix)
• title        — notification title
• body         — notification body text
• imageUrl     — full HTTPS image URL, or empty string
• data         — Dictionary of extra key-value pairs
• callback     — procedure(success, messageId, error)

| Parameter | Type
| - | - |
| topic | text
| title | text
| body | text
| imageUrl | text
| data | dictionary
| callback | any

### 6. SendDataToMultipleTokens
Sends a data-only message to multiple device tokens (up to 500).
No notification is shown automatically — the app handles display.
Delivered to MessageReceived event on each target device.
.
===============================================================
.
Parameters:
• tokens   — List of FCM registration token strings
• data     — Dictionary of key-value pairs
• callback — procedure(success, messageId, error)
messageId contains summary e.g. '5/5 sent'

| Parameter | Type
| - | - |
| tokens | list
| data | dictionary
| callback | any

### 7. SendNotificationToMultipleTokens
Sends a notification message to multiple device tokens (up to 500).
Notification is built and shown by the receiver extension on each device.
Delivered to NotificationReceived event on each target device.
.
===============================================================
.
Parameters:
• tokens       — List of FCM registration token strings
• title        — notification title
• body         — notification body text
• imageUrl     — full HTTPS image URL, or empty string
• data         — Dictionary of extra key-value pairs
• callback     — procedure(success, messageId, error)
messageId contains summary e.g. '5/5 sent'

| Parameter | Type
| - | - |
| tokens | list
| title | text
| body | text
| imageUrl | text
| data | dictionary
| callback | any

</details>

---

<details>
<summary><kbd>Properties (2)</kbd></summary>


### 1. ServerUrl
Deployed web app url of google app-script

* Input type: `text`

* Return type: `text`

### 2. SecretKey
Secret key that matches SECRET_KEY in your script

* Input type: `text`

* Return type: `text`
</details>


