# Android 6 update HTTPS

The final APK's Android 6 emulator probe found that the stock trust store rejects
gh-proxy.com, ghproxy.net and the redirected GitHub release download chain with
`Trust anchor for certification path not found`. GitHub API TLS succeeds but may
return the unauthenticated API rate limit.

Only updater HTTPS connections on API 23–25 use the compatibility socket factory.
It first validates against Android's trust manager, then the bundled Mozilla
roots if Android does not trust the chain. Standard certificate-chain validation
and HttpsURLConnection hostname verification stay enabled. No process-wide TLS
settings, system trust store or CarPlay TLS path is changed. Unknown self-signed
certificates remain rejected.

Public CA source: certifi/python-certifi, version 2026.10.23, revision
`c463746147ee894635ba5f67bab4bddff53abee2`, `certifi/cacert.pem`.
The unmodified bundle contains 118 public CA certificates; its SHA-256 is
`2ef52ad56b4803e264045c915e55c236cbfa045b84b81efefc218a19eb44bd2b`.
https://github.com/certifi/python-certifi/tree/c463746147ee894635ba5f67bab4bddff53abee2
The upstream MPL 2.0 notice is included as update-ca-bundle.LICENSE.

The head unit still needs Internet access and a correct date/time. A clock reset
to 2012 must be corrected; certificate date validation is never disabled.

The explicit Q7UpdateNetworkProbe ADB entry point tests the actual optimized
updater TLS code and packaged Java resource on Android 6. It does not run on app
startup.
