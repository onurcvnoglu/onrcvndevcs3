# ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmış olup, çoklu modül yapısı için uyarlanmıştır.

import os
import re
import json
from cloudscraper import CloudScraper

try:
    from Kekik.cli import konsol
    def log(msg):
        konsol.log(msg)
except ImportError:
    def log(msg):
        print(msg)

class MainUrlUpdater:
    def __init__(self, base_dir="."):
        self.base_dir = base_dir
        self.oturum   = CloudScraper()
        self.eklenti_dizinleri = ["DiziExtra", "FilmExtra", "AnimeExtra", "AsyaExtra"]

    def _kt_dosyalarini_bul(self):
        # Bu fonksiyon, eklenti dizinleri altındaki ana provider Kotlin dosyalarını bulur.
        # Mantık: Kotlin dosyasının uzantısız adı (case-insensitive) ile bulunduğu klasörün adı eşit olmalıdır.
        hedef_dosyalar = []
        for eklenti in self.eklenti_dizinleri:
            eklenti_yolu = os.path.join(self.base_dir, eklenti, "src", "main", "kotlin")
            if not os.path.exists(eklenti_yolu):
                continue
            
            for kok, alt_dizinler, dosyalar in os.walk(eklenti_yolu):
                for dosya in dosyalar:
                    if dosya.endswith(".kt"):
                        klasor_adi = os.path.basename(kok)
                        dosya_adi_uzantisiz = dosya[:-3]
                        # Case-insensitive eşleştirme
                        if klasor_adi.lower() == dosya_adi_uzantisiz.lower():
                            hedef_dosyalar.append((eklenti, os.path.join(kok, dosya)))
        return hedef_dosyalar

    def _mainurl_bul(self, kt_dosya_yolu):
        with open(kt_dosya_yolu, "r", encoding="utf-8") as file:
            icerik = file.read()
            if mainurl := re.search(r'override\s+var\s+mainUrl\s*=\s*"([^"]+)"', icerik):
                return mainurl[1]
        return None

    def _mainurl_guncelle(self, kt_dosya_yolu, eski_url, yeni_url):
        with open(kt_dosya_yolu, "r+", encoding="utf-8") as file:
            icerik = file.read()
            yeni_icerik = icerik.replace(eski_url, yeni_url)
            file.seek(0)
            file.write(yeni_icerik)
            file.truncate()

    def _versiyonu_artir(self, build_gradle_yolu):
        if not os.path.exists(build_gradle_yolu):
            return None
        with open(build_gradle_yolu, "r+", encoding="utf-8") as file:
            icerik = file.read()
            if version_match := re.search(r'version\s*=\s*(\d+)', icerik):
                eski_versiyon = int(version_match[1])
                yeni_versiyon = eski_versiyon + 1
                yeni_icerik = icerik.replace(f"version = {eski_versiyon}", f"version = {yeni_versiyon}")
                file.seek(0)
                file.write(yeni_icerik)
                file.truncate()
                return yeni_versiyon
        return None

    def _rectv_ver(self):
        istek = self.oturum.post(
            url     = "https://firebaseremoteconfig.googleapis.com/v1/projects/791583031279/namespaces/firebase:fetch",
            headers = {
                "X-Goog-Api-Key"    : "AIzaSyBbhpzG8Ecohu9yArfCO5tF13BQLhjLahc",
                "X-Android-Package" : "com.rectv.shot",
                "User-Agent"        : "Dalvik/2.1.0 (Linux; U; Android 12)",
            },
            json    = {
                "appBuild"      : "81",
                "appInstanceId" : "evON8ZdeSr-0wUYxf0qs68",
                "appId"         : "1:791583031279:android:1",
            }
        )
        return istek.json().get("entries", {}).get("api_url", "").replace("/api/", "")

    def guncelle(self):
        kt_dosyalar = self._kt_dosyalarini_bul()
        
        for eklenti, dosya_yolu in kt_dosyalar:
            dosya_adi = os.path.basename(dosya_yolu)
            provider_adi = dosya_adi[:-3]
            
            mainurl = self._mainurl_bul(dosya_yolu)
            if not mainurl:
                continue

            # CanliTV gibi M3U veya sabit test URL barındıran dosyaları güncelleme
            if mainurl.endswith(".m3u") or "raw.githubusercontent.com" in mainurl:
                log(f"[-] Atlaniyor (Sabit URL) : {provider_adi}")
                continue

            log(f"[~] Kontrol Ediliyor : {provider_adi} ({eklenti})")
            
            if provider_adi.lower() == "rectv":
                try:
                    final_url = self._rectv_ver()
                    log(f"[+] Kontrol Edildi   : {mainurl}")
                except Exception as hata:
                    log(f"[!] Kontrol Edilemedi : {mainurl}")
                    log(f"[!] {type(hata).__name__} : {hata}")
                    continue
            else:
                try:
                    istek = self.oturum.get(mainurl, allow_redirects=True, timeout=10)
                    log(f"[+] Kontrol Edildi   : {mainurl}")
                except Exception as hata:
                    log(f"[!] Kontrol Edilemedi : {mainurl}")
                    log(f"[!] {type(hata).__name__} : {hata}")
                    continue

                final_url = istek.url[:-1] if istek.url.endswith("/") else istek.url

            if mainurl == final_url:
                continue

            self._mainurl_guncelle(dosya_yolu, mainurl, final_url)
            
            build_gradle_yolu = os.path.join(self.base_dir, eklenti, "build.gradle.kts")
            if self._versiyonu_artir(build_gradle_yolu):
                log(f"[»] {provider_adi}: {mainurl} -> {final_url} (Versiyon Artirildi)")

if __name__ == "__main__":
    updater = MainUrlUpdater()
    updater.guncelle()
