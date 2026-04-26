# NFC Manager

A modern, powerful, and privacy-focused NFC toolkit for Android. Built with Kotlin and Jetpack Compose, NFC Manager makes it easy to read, write, and automate your life with NFC tags.

## ✨ Key Features

- **🔍 Advanced Scanning**: Read almost any NFC tag (NfcA, NfcB, NfcF, NfcV, and more). View detailed information about tag technology and capacity.
- **✍️ Powerful Writing**: Create your own tags for URLs, Wi-Fi networks, contacts, or plain text. Supports tag locking for permanent storage.
- **⚡ Smart Actions**: Map tags to phone actions. Tap a tag to toggle your flashlight, open a specific app, or connect to a saved Wi-Fi network.
- **📱 Modern UI**: Stunning Material 3 "Expressive" design with full support for Dark Mode.
- **🔒 Privacy First**: Your data stays on your device. Tag identifiers are hashed locally, and the app requests only the permissions it truly needs.

## 🛠️ Building for Production

To build a production-ready signed APK, follow these steps:

1. **Clone the repository**:

   ```bash
   git clone https://github.com/shivamsingh-07/NFC_Manager.git
   cd NFC_Manager
   ```

2. **Configure Signing**:
   Ensure you have your `keystore` file ready. You can configure signing in `app/build.gradle.kts` or provide the signing properties via environment variables.

3. **Build the Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

The signed APK will be available in `app/build/outputs/apk/release/`.

_Note: Requires JDK 17 and Android 12 (API 31) or higher._

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

<div align="center">Built with ❤️ by Shivam Singh.</div>
