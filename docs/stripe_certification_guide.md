# Stripe Tap to Pay Android Certification Guide

> **Last Updated:** 2026-01-14  
> **Status:** Pre-certification checklist  
> **SDK Version:** 5.1.1

## Overview

This document outlines the requirements and steps needed to pass Stripe Tap to Pay certification and achieve PCI MPoC compliance for the Venloop Tap app.

> [!IMPORTANT]
> Stripe handles most PCI MPoC compliance when using their Terminal SDK. Your app doesn't directly handle card data, which significantly simplifies the certification process.

---

## Part 1: Device Requirements

All devices running Venloop Tap must meet these requirements:

| Requirement | Details | Verification |
|-------------|---------|--------------|
| **Android Version** | Android 13 or later | `Build.VERSION.SDK_INT >= 33` |
| **NFC Sensor** | Functioning, integrated NFC | Device spec check |
| **Processor** | ARM-based processor | Device spec check |
| **Google Services** | Google Mobile Services (GMS) with Play Store | GMS check |
| **Keystore** | FEATURE_HARDWARE_KEYSTORE v100+ with ECDH support | Runtime check |
| **Device Integrity** | Not rooted, locked bootloader | Play Integrity API |
| **OS Integrity** | Unmodified manufacturer OS | Play Integrity API |
| **Connectivity** | Stable internet connection | Network check |

### Recommended Test Device
- **Realme Note 70T (RMX5313)** - See `docs/android_device_research.md`

---

## Part 2: App Requirements Checklist

### Build Configuration

- [ ] **Release build** - App must NOT be debuggable
  ```gradle
  buildTypes {
      release {
          debuggable false
          minifyEnabled true
          proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
      }
  }
  ```

- [ ] **ProGuard/R8 enabled** - Code obfuscation required
- [ ] **Latest Stripe SDK** - Currently using v5.1.1 ✅
- [ ] **Play Store distribution** - Required for Play Integrity API

### Security Requirements

- [ ] **No hardcoded secrets** - API keys in gradle.properties or secure storage
- [ ] **Certificate pinning** - Pin production API certificates
- [ ] **HTTPS enforcement** - All API calls over TLS 1.2+
- [ ] **Encrypted storage** - Using EncryptedSharedPreferences ✅
- [ ] **No sensitive data logging** - Remove all PAN/card data from logs

---

## Part 3: Security Implementation Tasks

### 3.1 Certificate Pinning

Add to `ApiClient.kt`:

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.venloop.tech", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

### 3.2 ProGuard Rules

Create/update `proguard-rules.pro`:

```proguard
# Stripe Terminal SDK
-keep class com.stripe.stripeterminal.** { *; }
-dontwarn com.stripe.stripeterminal.**

# Keep models for JSON serialization
-keep class com.example.taptopayandroid.models.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
```

### 3.3 Network Security Config

Create `res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.venloop.tech</domain>
        <pin-set>
            <pin digest="SHA-256">YOUR_CERT_PIN_HERE</pin>
        </pin-set>
    </domain-config>
    
    <!-- Allow localhost for development only - REMOVE for production -->
    <domain-config cleartextTrafficPermitted="true">
        <domain>localhost</domain>
        <domain>10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

---

## Part 4: Pre-Submission Checklist

### Before Submitting to Play Store

- [ ] Switch to **production Stripe API keys**
- [ ] Set `EXAMPLE_BACKEND_URL` to production API
- [ ] Remove all debug/simulation code paths
- [ ] Run security scan (OWASP MASVS)
- [ ] Test on physical device (not emulator)
- [ ] Verify NFC payments work with real cards
- [ ] Test with Apple Pay, Google Pay, Samsung Pay
- [ ] Test offline error handling
- [ ] Review all log statements for sensitive data

### Stripe Dashboard Setup

1. Create production Terminal Location
2. Configure webhook endpoints
3. Set up Stripe Connect if using platform model
4. Enable Tap to Pay for your account

---

## Part 5: PCI Compliance

### What Stripe Handles
- End-to-end encryption (E2EE)
- Card data never touches your servers
- Play Integrity API verification
- MPoC certification for Terminal SDK

### Your Responsibilities
- Secure app storage (EncryptedSharedPreferences) ✅
- No logging of payment data
- Secure API communication (HTTPS + pinning)
- Access control (admin password) ✅
- Keep SDK updated

### SAQ C Document
Stripe provides a **pre-filled SAQ C** (Self-Assessment Questionnaire) in your Stripe Dashboard under Terminal settings. This significantly simplifies PCI compliance reporting.

---

## Part 6: Certification Timeline

| Phase | Duration | Tasks |
|-------|----------|-------|
| **Development** | ✅ Done | SDK integration, basic flows |
| **Security Hardening** | 1-2 days | ProGuard, pinning, config |
| **Internal Testing** | 1 week | Full flow testing, edge cases |
| **Play Store Submission** | 1-3 days | Review process |
| **Stripe Review** | Varies | May require additional verification |

---

## References

- [Stripe Tap to Pay Documentation](https://docs.stripe.com/terminal/payments/setup-reader/tap-to-pay?platform=android)
- [PCI MPoC Standard](https://www.pcisecuritystandards.org/document_library/?document=mpoc)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [Play Integrity API](https://developer.android.com/google/play/integrity)

---

## Contact

For Stripe certification questions:
- Stripe Support: https://support.stripe.com
- Terminal-specific: Contact your Stripe account representative
