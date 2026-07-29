#!/bin/sh
# Fetches the Google Cast iOS SDK (static xcframework) if not already present.
# Called automatically from the Xcode build phase; safe to run manually.
set -e
DIR="$(cd "$(dirname "$0")/.." && pwd)/iOS/Frameworks"
DEST="$DIR/GoogleCast.xcframework"
VERSION="4.8.4"
if [ -d "$DEST" ]; then
  exit 0
fi
echo "Downloading Google Cast SDK $VERSION..."
mkdir -p "$DIR"
TMP=$(mktemp -d)
curl -sL --fail -o "$TMP/cast.zip" "https://dl.google.com/dl/chromecast/sdk/ios/GoogleCastSDK-ios-${VERSION}_static.zip"
unzip -q "$TMP/cast.zip" -d "$TMP"
mv "$TMP"/GoogleCastSDK-ios-*_static_xcframework/GoogleCast.xcframework "$DEST"
rm -rf "$TMP"
echo "Google Cast SDK installed at $DEST"
