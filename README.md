# 📱 Dynamic QR Scanner (Android)

A lightweight Android application to **scan dynamic QR codes** and validate them against a backend service in real-time.

---

## 🚀 Overview

This app is designed for systems where QR codes are:

- ⏱️ **Time-sensitive (short-lived)**
- 🔐 **Server-issued (secure)**
- 📡 Used for **attendance / authentication**

The app scans a QR code and sends it to the backend for validation.

---

## ⚙️ Features

- 📷 Real-time QR scanning (CameraX + ML Kit)
- 🔐 Secure backend validation
- ⚡ Fast response with Retrofit
- 🧾 Displays:
  - Scanned QR content
  - Raw server response
- 🏫 Editable **Class ID** (default: `LH-1`)

---

## 🧠 How It Works

Open App → Scan QR → Send to Backend → Get Validation Result

### Request sent:

```json
{
  "class_id": "LH-1",
  "hash": "<scanned_qr>",
  "timestamp": <current_time>
}
```

### Response:

```json
{
  "valid": true
}
```

---

## 📦 Tech Stack

- Kotlin + Jetpack Compose
- CameraX (camera preview)
- Google ML Kit (QR detection)
- Retrofit (API calls)

---

## 📲 APK Usage

1. Install the APK on your Android device  
2. Open the app  
3. Enter Class ID (or use default `LH-1`)  
4. Tap **Scan QR**  
5. Point camera at QR  
6. View validation result  

---

## ⚠️ Notes

- Internet connection required  
- Backend must be running  
- QR codes expire quickly (time-based validation)  
- Delay in scanning may result in invalid response  

---

## 🧪 Debugging

If validation fails:

- Check if QR is expired  
- Ensure correct `class_id`  
- Verify backend URL is reachable  
- Compare scanned QR content  

---

## 👤 Author

**Jyothsna Abhay**  
IIT Hyderabad  

---

## 💬 One-line Summary

> Scan QR → send to backend → validate in real-time
