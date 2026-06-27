# SkynetVPN Native Core Assets

SkynetVPN expects native binaries in Android assets by ABI. Add executable files with these paths:

```text
app/src/main/assets/xray/arm64-v8a/xray
app/src/main/assets/xray/armeabi-v7a/xray
app/src/main/assets/xray/x86/xray
app/src/main/assets/xray/x86_64/xray

app/src/main/assets/tun2socks/arm64-v8a/tun2socks
app/src/main/assets/tun2socks/armeabi-v7a/tun2socks
app/src/main/assets/tun2socks/x86/tun2socks
app/src/main/assets/tun2socks/x86_64/tun2socks
```

At runtime the app extracts the binary matching `Build.SUPPORTED_ABIS`, marks it executable, and starts it from app-private storage.

The Java/Kotlin code intentionally fails the connection when these files are missing. That prevents the UI from showing a fake connected state while no VPN tunnel is actually running.
