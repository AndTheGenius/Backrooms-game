# Backrooms: Null Sector — iOS Installation Guide

Because Apple requires macOS + Xcode + Apple code signing to build and sign iOS binaries, you can install the iOS port using either method below after exporting this project from AI Studio.

---

## Option 1: Build & Install Without a Mac (GitHub Actions + Sideloading)

1. In AI Studio, open **Settings → Push to GitHub** to push this repository to your GitHub account.
2. In your GitHub repository, open the **Actions** tab, select **Build iOS IPA**, and click **Run workflow** (it also runs automatically when `ios/` changes).
3. Once the `macos-latest` job finishes, download the **`BackroomsNullSector-unsigned-ipa`** artifact (`BackroomsNullSector-unsigned.ipa`).
4. Install `BackroomsNullSector-unsigned.ipa` onto your iPhone/iPad using **Sideloadly** or **AltStore** (sign in with your free Apple ID to sign and install directly over USB/Wi-Fi).

---

## Option 2: Build & Install Directly with a Mac + Xcode

1. Export the repository (**Settings → Export as ZIP** or **Push to GitHub**).
2. In Terminal inside the `ios/` folder, generate the Xcode project:
   ```bash
   brew install xcodegen
   xcodegen generate
   open BackroomsNullSector.xcodeproj
   ```
   *(Or manually create a new iOS SwiftUI App in Xcode and drag all `.swift` files from `ios/BackroomsNullSector/` into it.)*
3. Select the `BackroomsNullSector` target → **Signing & Capabilities** → pick your **Team** (your free Apple ID works).
4. Connect your iPhone/iPad via USB and press **Cmd + R** to install and play.
