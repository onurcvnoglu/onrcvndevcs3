# Cloudstream Extra Eklentileri (Turkish Extra Plugins)

Bu depo (repository), Cloudstream uygulaması için özelleştirilmiş Türkçe film, dizi, anime ve Asya dizileri sağlayıcı eklentilerini içerir. 

Uygulama arayüzünde kalabalık olmasını engellemek amacıyla tüm alt kaynaklar 4 ana çatı eklenti altında birleştirilmiştir:

1. **Film Extra 🎬**: En popüler Türkçe film sitelerinden içerikleri bir araya getirir.
2. **Dizi Extra 📺**: Türkçe yabancı dizi platformlarındaki içerikleri listeler.
3. **Anime Extra ⛩️**: Türkçe anime izleme platformlarındaki tüm anime ve anime filmlerini sunar.
4. **Asya Extra 🌸**: Kore, Çin, Tayland dizileri ve filmlerini içeren Türkçe Asya dizi kaynaklarını barındırır.

---

## 🚀 Cloudstream'e Nasıl Eklenir? (Kurulum)

Eklentileri Cloudstream uygulamanıza doğrudan kurmak için aşağıdaki adımları takip edin:

1. **Cloudstream** uygulamasını açın.
2. **Ayarlar (Settings) > Eklentiler (Plugins) > Depo Ekle (Add Repository)** yolunu izleyin.
3. Depo adı kısmına herhangi bir isim yazın (Örn: `Extra Plugins`).
4. Depo URL'si kısmına aşağıdaki adresi kopyalayıp yapıştırın:
   ```text
   https://raw.githubusercontent.com/onurcvnoglu/onrcvndevcs3/builds/repo.json
   ```
5. **Ekle (Add)** butonuna tıklayın.
6. Depo eklendikten sonra listeden depoyu seçerek **Anime Extra**, **Asya Extra**, **Dizi Extra** ve **Film Extra** eklentilerini tek tıkla kurup kullanmaya başlayabilirsiniz.

---

## 🛠 Geliştiriciler İçin Derleme (Build)

Eklentileri yerel olarak derlemek için aşağıdaki Gradle komutlarını kullanabilirsiniz:

### Tek Bir Eklentiyi Derleme:
- **Anime Extra**:
  ```bash
  ./gradlew :AnimeExtra:make
  ```
- **Asya Extra**:
  ```bash
  ./gradlew :AsyaExtra:make
  ```
- **Dizi Extra**:
  ```bash
  ./gradlew :DiziExtra:make
  ```
- **Film Extra**:
  ```bash
  ./gradlew :FilmExtra:make
  ```

### Tüm Eklentileri Derleme ve Manifesto Güncelleme:
```bash
./gradlew make makePluginsJson
```

Derleme sonrasında `.cs3` uzantılı eklenti dosyaları ve güncel `plugins.json` manifestosu `build` dizininde otomatik olarak oluşturulacaktır.

---

## 📝 Lisans ve Atıf
Bu eklentiler Cloudstream3 topluluk belgeleri ve resmi olmayan sağlayıcı API standartlarına uygun olarak geliştirilmiştir.
