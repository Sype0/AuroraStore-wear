# Aurora Wear

Aurora Store'un Wear OS saatler için değiştirilmiş sürümü. Upstream: <https://gitlab.com/AuroraOSS/AuroraStore>
(4.8.4, commit `3a15ff6`). Lisans upstream ile aynı: GPL-3.0-or-later.

## Farkları

- **Tüm uygulamalar görünür.** Google Play bir saate yalnızca Wear OS sürümü olan uygulamaları
  gösterir. Saatte, kullanıcı başka bir cihaz taklidi seçmediyse liste bir telefon profiliyle
  (Pixel 9a) çekilir; CPU mimarileri saatin kendi değerleri olarak kalır.
- **"Wear OS için özel üretilmedi" etiketi.** Her uygulama ayrıca saatin kendi profiliyle
  açılmış ikinci bir Play oturumuna sorulur. Play'in saate sunmadığı uygulamaların listede ve
  ayrıntı sayfasında en üstünde bu etiket görünür.
- **Wear OS sürümü olan uygulamalar saat sürümüyle iner.** İndirme ve güncelleme kontrolü bu
  uygulamalar için saat oturumuyla yapılır; diğerleri telefon sürümüyle iner.
- Paket adı `com.aurora.store.wear`, uygulama adı "Aurora Wear". Upstream'in kendi kendini
  güncelleme özelliği kapalı; güncellemeler bu deponun Releases sayfasından gelir.

## Derleme

`master` dalına her push'ta GitHub Actions imzalı APK'yı derleyip Releases'e yükler
(`.github/workflows/release.yml`). Gereken repo secret'ları: `KEYSTORE_BASE64`, `KEY_ALIAS`,
`KEY_PASSWORD`.

## Saate kurulum

```
adb install AuroraWear-<sürüm>.apk
# Saat, uygulamaların kendi kendine APK kurmasına izin veren ayar ekranını göstermeyebilir:
adb shell appops set com.aurora.store.wear REQUEST_INSTALL_PACKAGES allow
```
