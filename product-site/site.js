const menuButton = document.querySelector('.menu-toggle')
const navigation = document.querySelector('.site-nav')

menuButton?.addEventListener('click', () => {
  const isOpen = menuButton.getAttribute('aria-expanded') === 'true'
  menuButton.setAttribute('aria-expanded', String(!isOpen))
  navigation.classList.toggle('is-open', !isOpen)
})

navigation?.querySelectorAll('a').forEach((link) => {
  link.addEventListener('click', () => {
    menuButton?.setAttribute('aria-expanded', 'false')
    navigation.classList.remove('is-open')
  })
})

const visual = document.querySelector('.hero-visual')
const tag = document.querySelector('.hero-tag')
visual?.addEventListener('pointermove', (event) => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  const bounds = visual.getBoundingClientRect()
  const x = (event.clientX - bounds.left) / bounds.width - 0.5
  const y = (event.clientY - bounds.top) / bounds.height - 0.5
  tag?.style.setProperty('--tilt-x', `${y * -10}deg`)
  tag?.style.setProperty('--tilt-y', `${x * 13}deg`)
})
visual?.addEventListener('pointerleave', () => {
  tag?.style.setProperty('--tilt-x', '0deg')
  tag?.style.setProperty('--tilt-y', '0deg')
})

const translations = {
  tr: {
    'nav-how': 'Nasıl çalışır', 'nav-platform': 'Platform', 'nav-roadmap': 'Yol haritası',     'nav-dashboard': 'Kontrol panelini aç <span aria-hidden="true">↗</span>',
    'hero-kicker': '<span class="status-dot"></span> ÜRÜN DOĞRULAMA · PROJE GENEL BAKIŞI', 'hero-title': 'Her ürüne<br>doğrulanabilir bir <em>kimlik</em> ekleyin.',
    'hero-lede': 'AuthentiChain, güvenli NFC etiketleri üzerine kurulmuş bir ürün doğrulama projesidir. Her taramayı etiketin kriptografik imzası ve sayacıyla kontrol eder; ekiplerin ürünleri, etiketleri ve doğrulama olaylarını yönetmesini sağlar.',
    'explore': 'Sistemi keşfet <span aria-hidden="true">↓</span>', 'dashboard': 'Kontrol paneli <span aria-hidden="true">↗</span>', 'hero-note': 'NTAG 424 DNA için · Taramak için tüketici hesabı gerekmez',
    'idea-kicker': '01 — FİKİR', 'idea-title': 'Sadece bir etiket değil.<br><em>Kriptografik bir kontrol.</em>',
    'scan-kicker': '02 — TARAMA', 'scan-title': 'Dokunuştan karara<br>birkaç adımda.', 'scan-lede': 'Etiket kriptografik kanıt sağlar. AuthentiChain bu kanıtı kontrol etmeden önce ürün sonucu döndürmez.',
    'platform-kicker': '03 — PLATFORM', 'platform-title': 'Ürün kimliğinin<br>arkasındaki parçalar.', 'platform-lede': 'Tarama, doğrulama, anahtar yönetimi ve operasyonlar arasında net sınırları olan uçtan uca çalışan bir proje.',
    'security-kicker': '04 — ÇOK KATMANLI SAVUNMA', 'security-title': 'Sinyalleri izle.<br>Sınırları görünür tut.', 'security-link': 'Sınırlamaları okuyun <span aria-hidden="true">↓</span>',
    'limits-kicker': '05 — DÜRÜST TASARIM', 'limits-title': 'Bir tarama neyi<br>söyleyebilir, neyi söyleyemez.',
    'roadmap-kicker': '06 — SIRADA NE VAR', 'roadmap-title': 'Demodan dağıtıma<br>uygulanabilir yol.', 'roadmap-lede': 'Bunlar teslim edilmiş özellikler değil, aday sonraki adımlardır.',
    'audience-kicker': 'ANLAŞILMAK İÇİN TASARLANDI', 'audience-title': 'Üreten, taşıyan ve<br>malları doğrulayan ekipler için.',
    'closing-kicker': 'AUTHENTICHAIN · ÜRÜN KİMLİĞİ PROJESİ', 'closing-title': 'Bir dokunuşla başlayın.<br>Güvene doğru ilerleyin.',
    'closing-lede': 'Kontrol merkezini keşfedin veya projenin teknik belgelerini inceleyin.', 'github': 'GitHub projesi <span aria-hidden="true">↗</span>',
    'footer-tagline': 'Doğrulayabileceğiniz ürün kimliği.'
  },
  az: {
    'nav-how': 'Necə işləyir', 'nav-platform': 'Platforma', 'nav-roadmap': 'Yol xəritəsi',     'nav-dashboard': 'Paneli aç <span aria-hidden="true">↗</span>',
    'hero-kicker': '<span class="status-dot"></span> MƏHSUL DOĞRULAMASI · LAYİHƏ İCMALI', 'hero-title': 'Hər məhsula<br>doğrulaya biləcəyiniz bir <em>kimlik</em> verin.',
    'hero-lede': 'AuthentiChain təhlükəsiz NFC etiketləri əsasında qurulan məhsul doğrulama layihəsidir. Hər skanı etiketin kriptoqrafik imzası və sayğacı ilə yoxlayır, komandaların məhsulları, etiketləri və doğrulama hadisələrini idarə etməsinə imkan verir.',
    'explore': 'Sistemi kəşf edin <span aria-hidden="true">↓</span>', 'dashboard': 'İdarəetmə paneli <span aria-hidden="true">↗</span>', 'hero-note': 'NTAG 424 DNA üçün · Skan etmək üçün istehlakçı hesabı lazım deyil',
    'idea-kicker': '01 — İDEYA', 'idea-title': 'Sadəcə etiket deyil.<br><em>Kriptoqrafik yoxlama.</em>',
    'scan-kicker': '02 — SKAN', 'scan-title': 'Toxunuşdan qərara<br>bir neçə addımda.', 'scan-lede': 'Etiket kriptoqrafik sübut təqdim edir. AuthentiChain bu sübutu yoxlamadan məhsul nəticəsi qaytarmır.',
    'platform-kicker': '03 — PLATFORMA', 'platform-title': 'Məhsul kimliyinin<br>arxasındakı hissələr.', 'platform-lede': 'Skan, doğrulama, açar idarəetməsi və əməliyyatlar arasında aydın sərhədləri olan işlək uçdan-uca layihə.',
    'security-kicker': '04 — DƏRİNLİYİNƏ MÜDAFİƏ', 'security-title': 'Siqnalları izlə.<br>Məhdudiyyətləri görünən saxla.', 'security-link': 'Məhdudiyyətləri oxuyun <span aria-hidden="true">↓</span>',
    'limits-kicker': '05 — DÜRÜST DİZAYN', 'limits-title': 'Bir skan nəyi<br>deyə bilər, nəyi deyə bilməz.',
    'roadmap-kicker': '06 — NÖVBƏDƏ NƏ VAR', 'roadmap-title': 'Demodan istifadəyə<br>praktik yol.', 'roadmap-lede': 'Bunlar hazır funksiyalar deyil, mümkün növbəti addımlardır.',
    'audience-kicker': 'ANLAŞILMAQ ÜÇÜN HAZIRLANIB', 'audience-title': 'İstehsal edən, daşıyan və<br>malları doğrulayan komandalar üçün.',
    'closing-kicker': 'AUTHENTICHAIN · MƏHSUL KİMLİYİ LAYİHƏSİ', 'closing-title': 'Toxunuşla başlayın.<br>Etibara doğru qurun.',
    'closing-lede': 'İdarəetmə mərkəzini kəşf edin və ya layihənin texniki sənədlərinə baxın.', 'github': 'GitHub layihəsi <span aria-hidden="true">↗</span>',
    'footer-tagline': 'Doğrulaya biləcəyiniz məhsul kimliyi.'
  }
}

const languageSelectors = {
  'nav-how': '.site-nav a[href="#how"]', 'nav-platform': '.site-nav a[href="#platform"]', 'nav-roadmap': '.site-nav a[href="#roadmap"]',
  'nav-dashboard': '.nav-cta', 'hero-kicker': '.hero-copy .eyebrow', 'hero-title': '.hero h1', 'hero-lede': '.hero-lede',
  'explore': '.hero-actions .button-primary', 'dashboard': '.hero-actions .button-secondary', 'hero-note': '.hero-note',
  'idea-kicker': '#why > .section-kicker', 'idea-title': '#why h2', 'scan-kicker': '#how .section-kicker', 'scan-title': '#how h2', 'scan-lede': '#how .section-heading > p',
  'platform-kicker': '#platform .section-kicker', 'platform-title': '#platform h2', 'platform-lede': '#platform .section-heading > p',
  'security-kicker': '.security-intro .section-kicker', 'security-title': '.security-intro h2', 'security-link': '.security-intro .text-link',
  'limits-kicker': '#limits > .section-kicker', 'limits-title': '#limits h2', 'roadmap-kicker': '#roadmap .section-kicker', 'roadmap-title': '#roadmap h2', 'roadmap-lede': '#roadmap .section-heading > p',
  'audience-kicker': '.audience-card .section-kicker', 'audience-title': '.audience-card h2', 'closing-kicker': '.closing-inner .section-kicker', 'closing-title': '.closing-inner h2',
  'closing-lede': '.closing-inner > p', 'github': '.closing-inner .button-light', 'footer-tagline': '.site-footer p'
}

const detailedTranslations = {
  tr: {
    '.menu-toggle': 'Menü', '.trust-strip > div span': ['ETİKET', 'KRİPTOGRAFİ', 'ANAHTAR SERVİSİ', 'OPERASYONLAR'], '.trust-strip > div strong': ['NTAG 424 DNA', 'AES-CMAC doğrulama', 'Özel HSM tipi API', 'Canlı yönetim paneli'], '.tag-nfc': 'NFC / SDM', '.tag-label': 'ÖZGÜNLÜK<br><strong>HER DOKUNUŞTA</strong>', '.visual-caption': 'İmzalı tarama. Sunucu tarafında karar.', '.proof-card strong': 'İmza doğrulandı', '.proof-card small': 'Sayaç kabul edildi · Ürün eşleşti', '.proof-live': 'GERÇEK', '.hero-index span:last-child': 'GÜVENİ SOMUTLAŞTIR', '.data-node span': ['04 A7 2C', 'GEÇERLİ', '00 02 23'], '.identity-record b': 'KAYIT', '.result-band strong': ['GERÇEK', 'İŞARETLENDİ'], '.result-band p': 'Tarama sonucu operatör için bir sinyaldir; fiziksel ürün veya tedarik zinciri hakkında garanti değildir.', '.console-section .section-kicker': '03.5 — KANIT KONSOLU', '.console-copy h2': 'Bir dokunuşu<br><em>görünür kanıta</em> dönüştürün.', '.security-intro > p': 'Birden fazla kontrol şüpheli etkinliği fark etmeyi kolaylaştırır. Hiçbir tek tarama veya konum sinyali fiziksel ürünün gerçek olduğunu kanıtlamaz.', '.shield-core small': 'KORUMALI', '.skip-link': 'İçeriğe geç',
    '#why .story-copy > p': ['Sıradan QR kodları ve basılı seri numaraları kopyalanabilir. AuthentiChain daha güçlü bir yaklaşımı araştırır: NFC etiketi, sunucunun kayıtlı ürün ve gizli anahtarla doğrulayabileceği değişken, imzalı tarama verileri üretir.', 'Sonuç; markanın veya operatörün geçerli bir etiket yanıtını tekrar oynatma, değiştirilmiş imza, bilinmeyen etiket veya iptal edilmiş kimlikten ayırt etmesine yardımcı olmak üzere tasarlanmıştır.'],
    '#why .callout strong': 'Bu isim ne anlama geliyor', '#why .callout small': 'Ürün kimliği ve doğrulama kayıtlarını birbirine bağlayan bir zincir; herkese açık bir blok zinciri değil.', '.identity-tag small': 'fiziksel etiket', '.identity-proof small': 'imzalı kanıt', '.identity-record small': 'denetim izi', '.identity-core b': 'KİMLİK',
    '#how .step-card h3': ['Etikete dokun', 'Kanıtı kontrol et', 'Olayı kaydet', 'İncele ve yanıtla'], '#how .step-card p': ['Telefon veya uyumlu NFC okuyucu, etiketin güvenli dinamik mesajını okur.', 'API, CMAC imzasını, etiket durumunu ve sayacı kontrol eder. Yapılandırılmış HSM servisi anahtarları döndürmeden doğrular.', 'Tarama sonucu kaydedilir. Geçerli yanıt etiketi kayıtlı ürünle eşleştirebilir.', 'Yetkili ekipler taramaları inceler, uyarıları araştırır ve etiket durumunu yönetir.'], '#how .step-tag': ['NFC · NTAG 424 DNA', 'AES-CMAC · SAYAÇ', 'DENETİM · ÜRÜN EŞLEŞMESİ', 'PANEL · ROL TABANLI'], '.result-band small': ['Geçerli kriptografik yanıt', 'Tekrar oynatma, değiştirme, bilinmeyen, iptal veya şüpheli'], '.scan-track > span': ['DOKUN', 'OKU', 'DOĞRULA', 'KARAR VER'],
    '#platform .platform-card h3': ['Doğrulama API', 'Anahtar servisi sınırı', 'Yönetim merkezi', 'Geliştirici simülatörü'], '#platform .platform-card p': ['Java 21 ve Spring Boot etiket mesajlarını doğrular, sayaçları uygular ve sonuç döndürür. PostgreSQL kayıtlı kimlikleri ve tarama geçmişini saklar.', 'Ayrı ve kimlik doğrulamalı HSM tipi servis AES anahtarlarını saklar ve kriptografik doğrulamayı destekler. Envanter anahtarları değil, yalnızca meta verileri gösterir.', 'Ürünleri, provizyonu, etiket durumunu, hesapları ve tarama kanıtlarını yönetici, operatör ve görüntüleyici rolleriyle yönetin.', 'Sanal etiket kayıtları oluşturun, provizyon CSV dosyaları dışa aktarın ve geliştirme için imzalı test URL’leri üretin.'], '#platform .card-index': ['A / DOĞRULA', 'B / KORU', 'C / YÖNET', 'D / SİMÜLE ET'], '#platform .tech-row': [['SPRING BOOT', 'POSTGRESQL', 'FLYWAY'], ['PYTHON SERVİSİ', 'KİMLİKLİ API'], ['REACT', 'OTURUM + CSRF'], ['PYTHON', 'SADECE GELİŞTİRME']], '.architecture-note': 'Mevcut dağıtım modeli — web servisleri ve PostgreSQL Render için yapılandırılmıştır. Geliştirme simülatörü ve HSM tipi servis sertifikalı donanım güvenlik modülü değildir.', '.architecture-visual .arch-layer': ['NFC OKUYUCU →', 'DOĞRULAMA API →', 'ANAHTAR SERVİSİ →', 'DENETİM VERİTABANI'],
    '.defense-list strong': ['İmzalı mesaj doğrulama', 'Sayaç ve tekrar oynatma kontrolleri', 'Etiket durumu uygulaması', 'Operasyonel önlemler', 'Konum bağlamı'], '.defense-list small': ['Geçersiz kriptografik kanıtları reddeder.', 'Tekrar kullanılan veya sıra dışı tarama sayaçlarını işaretler.', 'İptal edilen etiketler artık aktif kimlik olarak geçemez.', 'Oturum tabanlı yönetici erişimi, roller, CSRF kontrolleri ve olay geçmişi.', 'İsteğe bağlı yaklaşık IP bağlamı ve kullanıcı paylaşımlı GPS; asla kriptografik kanıt değildir.'], '.defense-list b': ['TEMEL', 'TEMEL', 'TEMEL', 'YÖNETİM', 'UYARI'],
    '#limits > .limits-grid p': 'Kriptografi, bir etiketin kayıtlı anahtar için geçerli tarama verisi ürettiğini gösterebilir. Tek başına taramayı kimin yaptığını, nerede olduğunu veya etiketli ürünün fiziksel olarak taşınmadığını kanıtlayamaz.', '#limits li span:first-child': ['IP GeoIP', 'Cihaz GPS', 'Simülatör / HSM', 'Sahtekârlık sinyalleri'], '#limits li span:last-child': ['Yaklaşık ağ konumu; mobil ağlar, VPN’ler ve proxy’ler yanıltıcı olabilir.', 'İsteğe bağlı ve izin tabanlıdır; reddedilebilir, hatalı olabilir veya taklit edilebilir.', 'Yazılım gösterim bileşenleri; sertifikalı donanım güvenliği değildir.', 'İnsan incelemesi için yararlıdır; yalnızca konum tahminine göre engelleme yapmayın.'], '.signal-label': ['IP · YAKLAŞIK', 'GPS · İSTEĞE BAĞLI', 'KRİPTO · DOĞRULANDI'],
    '#roadmap article .roadmap-status': ['SONRAKİ ADAY', 'PLANLANAN YÖN', 'GELECEK SEÇENEĞİ'], '#roadmap article h3': ['Fiziksel okuyucuyu bağla', 'Konum kanıtını güçlendir', 'Gerçek operasyonlara hazırlan'], '#roadmap article p': ['Arduino Uno + PN532 tarama akışını OLED geri bildirimi ve Nano SD kart / hoparlör yardımcısıyla sağlamlaştırın.', 'Yaklaşık GeoIP’yi izinli cihaz konumundan ayırın, belirsizliği açıkça gösterin ve yalnızca operasyonun ihtiyaç duyduğu veriyi saklayın.', 'Sertifikalı HSM, anahtar döndürme ve kurtarma, üretim izleme, yedekleme stratejisi ve bağımsız güvenlik incelemesini değerlendirin.'], '#roadmap article small': ['DONANIM · SERİ PROTOKOL · ÇEVRİMDIŞI DAVRANIŞ', 'GİZLİLİK · DOĞRULUK · SAKLAMA', 'ANAHTAR YAŞAM DÖNGÜSÜ · DAYANIKLILIK · GÜVENCE'], '.roadmap-line b': ['ŞİMDİ', 'SONRA', 'GELECEK'],
    '.audience-card p': 'Güvenli NFC kimliğini araştıran markalar, ürün ekipleri ve entegratörler için gösterilebilir bir temel olarak tasarlandı.', '.audience-pills span': ['MARKALAR', 'OPERASYONLAR', 'ENTEGRATÖRLER', 'GELİŞTİRİCİLER'], '.closing-inner .button-primary': 'Paneli aç <span aria-hidden="true">↗</span>', '.footer-disclaimer': 'Proje gösterimi · Sertifika veya ürün özgünlüğü garantisi değildir', '.back-top': 'Başa dön ↑', '.console-copy > p': 'Her kararın bir izi vardır: ürün kimliği, sayaç durumu, doğrulama sonucu ve operatörün incelemesi gereken sinyaller.', '.console-metrics small': ['örnekte doğrulandı', 'kayıtlı olay', 'açığa çıkan anahtar'], '.console-brand': 'AUTHENTICHAIN / CANLI', '.console-label': 'DOĞRULAMA KARARI', '.console-details strong': 'İmza kabul edildi', '.console-details p': 'Ürün kayıtlı kimlikle eşleşti.', '.console-code span': ['UID', 'CTR', 'CMAC'], '.console-code b': ['04 A7 2C 91', '00 02 23', 'GEÇERLİ'], '.console-footer span': ['API YANITI 200', 'SAYAÇ ARTIRILDI ↗'],
    '.console-ring-core small': 'GERÇEK',
  },
  az: {
    '.menu-toggle': 'Menyu', '.trust-strip > div span': ['ETİKET', 'KRİPTOQRAFİYA', 'AÇAR SERVİSİ', 'ƏMƏLİYYATLAR'], '.trust-strip > div strong': ['NTAG 424 DNA', 'AES-CMAC doğrulaması', 'Özəl HSM tipli API', 'Canlı idarəetmə paneli'], '.tag-nfc': 'NFC / SDM', '.tag-label': 'ORİJİNALLIQ<br><strong>HƏR TOXUNUŞDA</strong>', '.visual-caption': 'İmzalı skan. Server tərəfində qərar.', '.proof-card strong': 'İmza doğrulandı', '.proof-card small': 'Sayğac qəbul edildi · Məhsul uyğunlaşdı', '.proof-live': 'REAL', '.hero-index span:last-child': 'ETİBARI GÖRÜNƏN ET', '.data-node span': ['04 A7 2C', 'DOĞRUDUR', '00 02 23'], '.identity-record b': 'QEYD', '.result-band strong': ['REAL', 'İŞARƏLƏNİB'], '.result-band p': 'Skan nəticəsi operator üçün siqnaldır; fiziki məhsul və ya təchizat zənciri barədə zəmanət deyil.', '.console-section .section-kicker': '03.5 — SÜBUT KONSOLU', '.console-copy h2': 'Bir toxunuşu<br><em>görünən sübuta</em> çevirin.', '.security-intro > p': 'Bir neçə yoxlama şübhəli fəaliyyəti aşkar etməyi asanlaşdırır. Heç bir tək skan və ya məkan siqnalı fiziki məhsulun həqiqiliyini sübut etmir.', '.shield-core small': 'MÜDAFİƏLİ', '.skip-link': 'Məzmuna keç',
    '#why .story-copy > p': ['Adi QR kodları və çap olunmuş seriya nömrələri kopyalana bilər. AuthentiChain daha güclü yanaşmanı araşdırır: NFC etiketi serverin qeydiyyatlı məhsul və gizli açarla yoxlaya biləcəyi dəyişən, imzalı skan məlumatı yaradır.', 'Nəticə brendə və ya operatora düzgün etiket cavabını təkrar istifadə, dəyişdirilmiş imza, naməlum etiket və ya ləğv edilmiş kimlikdən ayırmağa kömək etmək üçün hazırlanıb.'],
    '#why .callout strong': 'Bu ad nə deməkdir', '#why .callout small': 'Məhsul kimliyi və doğrulama qeydlərini birləşdirən zəncir; ictimai blokçeyn deyil.', '.identity-tag small': 'fiziki etiket', '.identity-proof small': 'imzalı sübut', '.identity-record small': 'audit izi', '.identity-core b': 'KİMLİK',
    '#how .step-card h3': ['Etiketə toxun', 'Sübutu yoxla', 'Hadisəni qeyd et', 'Nəzərdən keçir və cavab ver'], '#how .step-card p': ['Telefon və ya uyğun NFC oxuyucusu etiketin təhlükəsiz dinamik mesajını oxuyur.', 'API CMAC imzasını, etiket statusunu və sayğacı yoxlayır. HSM tipli xidmət açarları qaytarmadan doğrulayır.', 'Skan nəticəsi qeyd olunur. Düzgün cavab etiketi qeydiyyatlı məhsulla uyğunlaşdıra bilər.', 'Səlahiyyətli heyət skanları nəzərdən keçirir, xəbərdarlıqları araşdırır və etiket statusunu idarə edir.'], '#how .step-tag': ['NFC · NTAG 424 DNA', 'AES-CMAC · SAYĞAC', 'AUDİT · MƏHSUL UYĞUNLUĞU', 'PANEL · ROL ƏSASLI'], '.result-band small': ['Düzgün kriptoqrafik cavab', 'Təkrar, dəyişiklik, naməlum, ləğv edilmiş və ya şübhəli'], '.scan-track > span': ['TOXUN', 'OXU', 'DOĞRULA', 'QƏRAR VER'],
    '#platform .platform-card h3': ['Doğrulama API-si', 'Açar xidməti sərhədi', 'İdarəetmə mərkəzi', 'Tərtibatçı simulyatoru'], '#platform .platform-card p': ['Java 21 və Spring Boot etiket mesajlarını yoxlayır, sayğacları tətbiq edir və nəticə qaytarır. PostgreSQL qeydiyyatlı kimlikləri və skan tarixçəsini saxlayır.', 'Ayrı və autentifikasiya olunmuş HSM tipli xidmət AES açarlarını saxlayır və kriptoqrafik yoxlamanı dəstəkləyir. İnventar açarları deyil, yalnız metadatanı göstərir.', 'Məhsulları, proviziyanı, etiket statusunu, hesabları və skan sübutlarını idarəçi, operator və baxış rolları ilə idarə edin.', 'Virtual etiket qeydləri yaradın, provizion CSV-lərini ixrac edin və inkişaf üçün imzalı test URL-ləri yaradın.'], '#platform .tech-row': [['SPRING BOOT', 'POSTGRESQL', 'FLYWAY'], ['PYTHON SERVİSİ', 'AUTENTİFİKASİYALI API'], ['REACT', 'SESSİYA + CSRF'], ['PYTHON', 'YALNIZ İNKİŞAF']], '.architecture-note': 'Cari yerləşdirmə modeli — veb xidmətləri və PostgreSQL Render üçün konfiqurasiya edilib. İnkişaf simulyatoru və HSM tipli xidmət sertifikatlı aparat təhlükəsizlik modulu deyil.', '.architecture-visual .arch-layer': ['NFC OXUYUCU →', 'DOĞRULAMA API →', 'AÇAR SERVİSİ →', 'AUDİT VERİLƏNLƏRİ BAZASI'],
    '.defense-list strong': ['İmzalı mesajın yoxlanması', 'Sayğac və təkrar yoxlamaları', 'Etiket statusunun tətbiqi', 'Əməliyyat təhlükəsizliyi', 'Məkan konteksti'], '.defense-list small': ['Yanlış kriptoqrafik sübutları rədd edir.', 'Təkrar istifadə olunan və ya ardıcıllıqdan kənar sayğacları işarələyir.', 'Ləğv edilmiş etiketlər artıq aktiv kimlik kimi keçmir.', 'Sessiya əsaslı idarəçi girişi, rollar, CSRF nəzarəti və hadisə tarixçəsi.', 'İstəyə bağlı təxmini IP konteksti və istifadəçi GPS-i; heç vaxt kriptoqrafik sübut deyil.'], '.defense-list b': ['ƏSAS', 'ƏSAS', 'ƏSAS', 'İDARƏ', 'XƏBƏRDARLIQ'],
    '#limits > .limits-grid p': 'Kriptoqrafiya etiketin qeydiyyatlı açar üçün düzgün skan məlumatı yaratdığını göstərə bilər. Təkbaşına skanı kimin etdiyini, harada olduğunu və ya məhsulun fiziki olaraq köçürülmədiyini sübut etmir.', '#limits li span:first-child': ['IP GeoIP', 'Cihaz GPS-i', 'Simulyator / HSM', 'Fırıldaq siqnalları'], '#limits li span:last-child': ['Təxmini şəbəkə məkanı; mobil şəbəkələr, VPN və proksi yanıltıcı ola bilər.', 'İstəyə bağlı və icazə əsaslıdır; rədd edilə, səhv ola və ya saxtalaşdırıla bilər.', 'Proqram nümayiş komponentləri; sertifikatlı aparat təhlükəsizliyi deyil.', 'İnsan yoxlaması üçün faydalıdır; yalnız məkan təxmininə əsasən bloklamayın.'], '.signal-label': ['IP · TƏXMİNİ', 'GPS · İSTƏYƏ BAĞLI', 'KRİPTO · DOĞRULANDI'],
    '#roadmap article .roadmap-status': ['NÖVBƏTİ NAMİZƏD', 'PLANLAŞDIRILAN İSTİQAMƏT', 'GƏLƏCƏK SEÇİMİ'], '#roadmap article h3': ['Fiziki oxuyucunu qoş', 'Məkan sübutunu gücləndir', 'Real əməliyyatlara hazırlaş'], '#roadmap article p': ['Arduino Uno + PN532 skan axınını OLED bildirişi və Nano SD kart / dinamik köməkçisi ilə möhkəmləndirin.', 'Təxmini GeoIP-ni razılıq verilmiş cihaz məkanından ayırın, qeyri-müəyyənliyi göstərin və yalnız əməliyyatın ehtiyacı olanı saxlayın.', 'Sertifikatlı HSM, açar dövriyyəsi və bərpası, istehsal monitorinqi, ehtiyat nüsxə və müstəqil təhlükəsizlik yoxlamasını qiymətləndirin.'], '#roadmap article small': ['AVADANLIQ · SERİAL PROTOKOL · OFFLAYN DAVRANIŞ', 'MƏXFİLİK · DƏQİQLİK · SAXLAMA', 'AÇAR HƏYAT DÖVRÜ · DAVAMLILIQ · TƏMİNAT'], '.roadmap-line b': ['İNDİ', 'SONRA', 'GƏLƏCƏK'],
    '.audience-card p': 'Təhlükəsiz NFC kimliyini araşdıran brendlər, məhsul komandaları və inteqratorlar üçün nümayiş etdirilə bilən əsas kimi hazırlanıb.', '.audience-pills span': ['BRENDLƏR', 'ƏMƏLİYYATLAR', 'İNTEQRATORLAR', 'TƏRTİBATÇILAR'], '.closing-inner .button-primary': 'Paneli aç <span aria-hidden="true">↗</span>', '.footer-disclaimer': 'Layihə nümayişi · Sertifikat və ya məhsul orijinallığı zəmanəti deyil', '.back-top': 'Yuxarı qayıt ↑', '.console-copy > p': 'Hər qərarın izi var: məhsul kimliyi, sayğac vəziyyəti, doğrulama nəticəsi və operatorun nəzərdən keçirə biləcəyi siqnallar.', '.console-metrics small': ['nümunədə doğrulandı', 'qeyd edilmiş hadisə', 'açılan açar'], '.console-brand': 'AUTHENTICHAIN / CANLI', '.console-label': 'DOĞRULAMA QƏRARI', '.console-details strong': 'İmza qəbul edildi', '.console-details p': 'Məhsul qeydiyyatlı kimliklə uyğunlaşdı.', '.console-code span': ['UID', 'CTR', 'CMAC'], '.console-code b': ['04 A7 2C 91', '00 02 23', 'DOĞRUDUR'], '.console-footer span': ['API CAVABI 200', 'SAYĞAC ARTIRILDI ↗'],
    '.proof-live': 'HƏQİQİ', '.console-ring-core small': 'HƏQİQİ', '.result-band strong': ['HƏQİQİ', 'ŞÜBHƏLİ'], '.identity-record small': 'yoxlama izi', '#platform .card-index': ['A / DOĞRULA', 'B / QORU', 'C / İDARƏ ET', 'D / SİMULYASİYA ET'], '#roadmap article h3': ['Fiziki oxuyucunu qoş', 'Məkan sübutunu gücləndir', 'Faktiki əməliyyatlara hazırlaş']
  }
}

const originalMarkup = new Map()
const translationSelectors = new Set([
  ...Object.values(languageSelectors),
  ...Object.keys(detailedTranslations.tr),
  ...Object.keys(detailedTranslations.az)
])
translationSelectors.forEach((selector) => {
  document.querySelectorAll(selector).forEach((element) => {
    if (!originalMarkup.has(element)) originalMarkup.set(element, element.innerHTML)
  })
})

function applyDetailedLanguage(language) {
  const dictionary = detailedTranslations[language]
  if (!dictionary) return
  Object.entries(dictionary).forEach(([selector, value]) => {
    const elements = [...document.querySelectorAll(selector)]
    if (Array.isArray(value) && Array.isArray(value[0])) {
      elements.forEach((element, index) => {
        if (value[index]) element.innerHTML = value[index].map(item => `<span>${item}</span>`).join('')
      })
    } else if (Array.isArray(value) && typeof value[0] === 'string') {
      elements.forEach((element, index) => {
        if (value[index] !== undefined) element.innerHTML = value[index]
      })
    } else if (elements[0]) {
      elements[0].innerHTML = value
    }
  })
}

function applyLanguage(language) {
  if (language === 'en') {
    localStorage.removeItem('authentichain-language')
    originalMarkup.forEach((markup, element) => { element.innerHTML = markup })
    document.documentElement.lang = 'en'
    document.title = 'AuthentiChain — Product identity you can verify'
    document.querySelector('.site-nav')?.setAttribute('aria-label', 'Main navigation')
    document.querySelector('.trust-strip')?.setAttribute('aria-label', 'Project technology')
    document.querySelector('.hero-visual')?.setAttribute('aria-label', 'Illustration of an NFC product verification flow')
    document.querySelector('.identity-visual')?.setAttribute('aria-label', 'Product identity connection diagram')
    document.querySelector('meta[name="description"]')?.setAttribute('content', 'AuthentiChain connects NTAG 424 DNA products to cryptographic verification, protected key handling, and a practical product identity dashboard.')
    document.querySelectorAll('.language-button').forEach((button) => {
      button.classList.toggle('is-selected', button.dataset.language === 'en')
    })
    return
  }
  const dictionary = translations[language]
  if (!dictionary) return
  originalMarkup.forEach((markup, element) => { element.innerHTML = markup })
  Object.entries(dictionary).forEach(([key, value]) => {
    const element = document.querySelector(languageSelectors[key])
    if (element) element.innerHTML = value
  })
  applyDetailedLanguage(language)
  document.title = language === 'tr'
    ? 'AuthentiChain — Doğrulayabileceğiniz ürün kimliği'
    : 'AuthentiChain — Doğrulaya biləcəyiniz məhsul kimliyi'
  document.documentElement.lang = language === 'tr' ? 'tr' : language === 'az' ? 'az' : 'en'
  document.querySelectorAll('.language-button').forEach((button) => {
    button.classList.toggle('is-selected', button.dataset.language === language)
  })
  document.querySelector('.site-nav')?.setAttribute('aria-label', language === 'tr' ? 'Ana gezinme' : 'Əsas naviqasiya')
  document.querySelector('.trust-strip')?.setAttribute('aria-label', language === 'tr' ? 'Proje teknolojisi' : 'Layihə texnologiyası')
  document.querySelector('.hero-visual')?.setAttribute('aria-label', language === 'tr' ? 'NFC ürün doğrulama akışı çizimi' : 'NFC məhsul doğrulama axınının təsviri')
  document.querySelector('.identity-visual')?.setAttribute('aria-label', language === 'tr' ? 'Ürün kimliği bağlantı şeması' : 'Məhsul kimliyi əlaqə diaqramı')
  document.querySelector('meta[name="description"]')?.setAttribute('content', language === 'tr'
    ? 'AuthentiChain, NTAG 424 DNA ürün kimliklerini kriptografik doğrulama, korumalı anahtar yönetimi ve ürün kimliği paneliyle birleştirir.'
    : 'AuthentiChain NTAG 424 DNA məhsul kimliklərini kriptoqrafik doğrulama, qorunan açar idarəetməsi və məhsul kimliyi paneli ilə birləşdirir.')
  localStorage.setItem('authentichain-language', language)
}

document.querySelectorAll('.language-button').forEach((button) => {
  button.addEventListener('click', () => applyLanguage(button.dataset.language))
})
const savedLanguage = localStorage.getItem('authentichain-language')
if (savedLanguage && savedLanguage !== 'en') applyLanguage(savedLanguage)
