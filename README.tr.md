# Watchdog

[English](README.md)

### [⬇ Android için indir (Watchdog.apk)](https://github.com/SCgrS/Watchdog/releases/latest/download/Watchdog.apk)

Watchdog, APK kurmak için açılan hata ayıklama ayarlarını ve kapatılan Otomatik Engelleyici'yi unutmamanız için
**hatırlatma yapan küçük bir Android uygulamasıdır**.

Samsung telefonda dışarıdan APK kurmak için Otomatik Engelleyici kapatılır, USB hata ayıklama açılır. Kurulum bitince
bunları geri almak çoğu zaman unutulur ve telefon günlerce korumasız kalır. Watchdog seçtiğiniz aralıkta (varsayılan
haftada bir) bu ayarlara bakar, açık kalan varsa bildirimle hatırlatır. Bildirime dokununca uygulama açılır. Ayarları
kapatmayı siz yaparsınız: Uygulama hiçbir ayarı değiştiremez.

<p align="center">
  <img src="docs/screen-main-tr.png" width="45%" alt="Ana ekran: USB hata ayıklama açık, kablosuz hata ayıklama kapalı, Otomatik Engelleyici bilinmiyor, altında Denetle düğmesi ve denetim aralığı">
  <img src="docs/screen-language-tr.png" width="45%" alt="Dil penceresi: Türkçe yeşil halkayla seçili, üstünde English, altta Vazgeç">
</p>

## Kurulum

Gereksinim: Android 12 ya da sonrası. Otomatik Engelleyici yalnız Samsung telefonlarda (One UI 6 ve sonrası) vardır, öbür
telefonlarda uygulama yalnız hata ayıklama ayarlarını izler.

1. Yukarıdaki bağlantıdan `Watchdog.apk` dosyasını telefona indirin.
2. Samsung telefonda **Ayarlar › Güvenlik ve gizlilik › Otomatik Engelleyici**'yi kapatın. Açıkken dışarıdan APK kurulmaz.
3. Dosyayı açıp kurun. Telefon bu kaynaktan kurulum için izin isterse verin. Google Play Protect bilinmeyen uygulama
   uyarısı verirse ayrıntıları açıp **Yine de yükle**'yi seçin. Uyarı, uygulama Play Store'dan gelmediği için çıkar.
4. Watchdog'u açın ve bildirimlere izin verin.
5. Uygulamanın önerdiği gibi **Uygulama bilgileri › Pil › Kısıtlanmamış**'ı seçin. Yoksa Samsung uygulamayı uyutabilir ve
   hatırlatmalar gelmez.
6. Otomatik Engelleyici'yi yeniden açın.
7. Uygulama İngilizce açılır. Türkçe için ana ekranda **App › Language**'e dokunup **Türkçe**'yi seçin.

APK, Watchdog'un yayım anahtarıyla imzalıdır. Sertifikasının SHA-256 parmak izi
`32:03:63:47:A0:5B:5C:D3:BA:28:58:37:D7:E4:5A:7C:51:9F:B4:15:E2:FF:62:2E:BE:5B:25:F5:2C:3D:10:6E`.

Uygulamayı kaynak kodundan derlemek için [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) dosyasına bakınız.

## Öne çıkanlar

- **Kesin kural:** Uygulama hiçbir ayarı değiştiremez. `WRITE_SECURE_SETTINGS` izni, erişilebilirlik servisi, cihaz
  yöneticisi, Shizuku, root ve `INTERNET` izni yoktur. Ayarları yalnızca okur, bildirim gösterir ve ilgili ayar
  sayfasını açar. Kapatmayı siz yaparsınız. Yalnız iki izin ister: Bildirim göstermek ve telefon açılınca denetimi
  yeniden kurmak.
- **İki ayar:** Denetim aralığını (saatte bir ile haftada bir arası) ve dili (İngilizce ya da Türkçe) seçersiniz.
- **Kapanınca susar:** Ayarı kapattığınız anda bildirim kalkar.
- **Az kaynak:** Arka planda sürekli çalışmaz. Sistem denetim anında uygulamayı kısa bir an uyandırır. Paket yaklaşık 80 KB'tır,
  başka bir kütüphaneye bağlı değildir.

## Ne yapar

### Denetim

Uygulama seçtiğiniz aralık dolunca USB hata ayıklamaya ve kablosuz hata ayıklamaya bakar. Biri açıksa bildirim gelir.
İkisi birden açıksa bildirimin başlığı ikisini de söyler. Bildirime dokununca Watchdog açılır: Hata ayıklama ayarları ve
Otomatik Engelleyici aynı ekranda görünür, hata ayıklamayı kapatırken Otomatik Engelleyici unutulmaz. Ekranda bir ayarın
satırına dokununca o ayar açılır. Bildirimdeki **Otomatik Engelleyici** düğmesi doğrudan onun sayfasını açar. Ayarı
kapatana dek bildirim yerinde kalır, her aralıkta yeniden uyarır. Telefon o sırada kapalıysa denetim açılınca yapılır.
Telefon yeniden başlayınca bildirim sessizce geri gelir.

Ayar açarken ya da uygulamayı açtığınızda bildirim gelmez. Uygulama yalnız aralık dolunca uyarır.

Geliştirici seçeneklerinin kendisi izlenmez. Açık kalması tek başına risk değildir, uyarı hata ayıklama ayarları içindir.

### Elle denetim

Ana ekrandaki **Denetle** düğmesi ayarlara hemen bakar ve sonucu kısa bir iletiyle söyler ("Denetlendi: USB hata ayıklama
açık" gibi). Açık ayar varsa bildirim sessizce gelir. Sonraki denetim o andan bir aralık sonraya kayar.

### Otomatik Engelleyici

Android, Otomatik Engelleyici'nin durumunu uygulamalara okutmuyor. Bu yüzden hatırlatma şöyle yapılır: Hata ayıklama
ayarlarını kapattığınız anda sessiz bir "Şimdi Otomatik Engelleyici'yi açın" bildirimi gelir (hatırlatmayı görmüş olun ya
da olmayın). Dokununca Otomatik Engelleyici sayfası açılır ve bildirim kapanır. Bu hatırlatma bir kez gelir. Durumu
okunabilen telefonlarda kapalı Otomatik Engelleyici aralık dolunca ayrıca hatırlatılır.

### Ana ekran

Ana ekran üç ayarın şu anki durumunu, son ve sonraki denetim zamanını gösterir. Dokunulabilen her satırın sağında ok
vardır. Bir ayarın satırına dokununca o ayar açılır, USB ve kablosuz hata ayıklamada sayfa doğrudan o ayara kayar. Pil
ayarında uygulama kısıtlanmışsa ya da Android uzun süre açılmayan uygulamayı duraklatabilecekse ana ekran bunu söyler ve
ilgili ayar sayfasını açar.

### Dil

Uygulama İngilizce ve Türkçe kullanılabilir. Varsayılan dil İngilizcedir, telefonun dili ne olursa olsun İngilizce açılır.
Dil ana ekrandaki **Uygulama › Dil** satırından değiştirilir. Ekran ve bildirimler seçilen dile geçer.

## Güncelleme

Uygulama kendini güncellemez. Yeni sürümün APK'sını indirip eskisinin üstüne kurun, ayarlarınız korunur.

## Veri gizliliği

Uygulamanın internet izni yoktur, hiçbir veri telefondan çıkmaz. Telefonda yalnız seçtiğiniz aralık ve dil, son ve
sonraki denetim zamanı, bildirim izninin sorulup sorulmadığı ve bekleyen bir Otomatik Engelleyici hatırlatması olup
olmadığı saklanır. Bu kayıt buluta yedeklenmez ve yeni telefona aktarılmaz. Telemetri yoktur.

## Üçüncü taraf bileşenler

Watchdog Android'in kendi sınıflarını ve Kotlin'in standart kütüphanesini (JetBrains, Apache-2.0) kullanır. Başka hiçbir
pakete bağlı değildir.

## Otomatik denetimler

[![CodeQL Advanced](https://github.com/SCgrS/Watchdog/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/SCgrS/Watchdog/actions/workflows/codeql.yml)\
[![Dependency Review](https://github.com/SCgrS/Watchdog/actions/workflows/dependency-review.yml/badge.svg?branch=main)](https://github.com/SCgrS/Watchdog/actions/workflows/dependency-review.yml)

---

Soru ve geri bildirimler için: [x.com/CgrShn](https://x.com/CgrShn)

Lisans: AGPL-3.0. Ayrıntılar için [LICENSE](LICENSE) dosyasına bakınız.
