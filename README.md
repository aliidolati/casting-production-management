# Casting production management

سامانهٔ مدیریت تولید قطعات ریخته‌گری؛ یک پروژهٔ Spring Boot که Backend و رابط فارسی را با هم ارائه می‌کند.

## ساختار پروژه

| مسیر | کاربرد |
|---|---|
| `src/main/java/com/factory/productmanager/` | Controller، DTO، Service، Repository، Entity، Security و تنظیمات Backend |
| `src/main/resources/static/` | HTML، CSS، JavaScript کلاسیک، فونت‌ها و تصاویر رابط کاربری |
| `src/main/resources/application.properties` | تنظیمات عمومی با ارجاع به متغیرهای محیطی؛ بدون مقدار واقعی رمز یا کلید |
| `src/main/resources/db/migration/` | اسکریپت‌های تاریخی و تغییرات Schema؛ زنجیرهٔ کامل ساخت دیتابیس نیست |
| `src/test/` | تست‌های واحد، یکپارچه، MySQL مستقل و ابزارهای مرورگر/ممیزی با دادهٔ ساختگی |
| `pom.xml`، `mvnw`، `mvnw.cmd`، `.mvn/wrapper/` | وابستگی‌ها و Build با Maven |
| `.github/workflows/backend-tests.yml` | تست‌های H2 و MySQL روی GitHub-hosted runner |
| `AGENTS.md` و `docs/ai-context/` | قواعد توسعه و مستندات معماری/فرایندها |

Backend از Spring Boot 4.1، Java با هدف کامپایل 17، Spring MVC، JPA/Hibernate، Security و MySQL استفاده می‌کند. Frontend بخشی از همین برنامه است؛ پروژهٔ npm یا Build جداگانه‌ای ندارد. تاریخ API میلادی ISO است و نمایش رابط فارسی/RTL و جلالی باقی می‌ماند.

## Build و تست بدون دیتابیس اصلی

پیش‌نیاز: JDK 17 یا جدیدتر؛ برای دریافت اولیهٔ وابستگی‌ها دسترسی شبکه لازم است.

```bash
bash scripts/codex-setup.sh
bash scripts/test-backend.sh fast
bash mvnw -B -ntp -DskipTests package
```

در PowerShell ویندوز، Maven Wrapper را به‌شکل `.\mvnw.cmd -B -ntp test` اجرا کنید. اگر فایل اجرایی wrapper در لینوکس مجوز نداشت، استفاده از `bash mvnw` کافی است.

تست‌های معمول از H2 و فضای `target/test-uploads` استفاده می‌کنند و به رمزهای برنامه یا MySQL اصلی نیاز ندارند. تست‌های واقعی MySQL با Docker/Testcontainers:

```bash
bash scripts/test-backend.sh mysql
```

Workflow دو Job مستقل `fast` و `mysql` دارد. تست‌های MySQL، Schema بدون رکورد واقعی، migrationهای انتخاب‌شده و رفتار واقعی دیتابیس را بررسی می‌کنند. [جزئیات و محدودیت‌های تست](docs/ai-context/CLOUD_TESTING.md) را بخوانید؛ موفقیت H2 به‌معنای اعتبار migrationهای MySQL نیست.

## تنظیمات اجرای محلی

فهرست متغیرها در [.env.example](.env.example) است. Spring Boot فایل `.env` را خودکار نمی‌خواند؛ متغیرها را در Run Configuration یا محیط سیستم تعریف کنید، یا فایل properties خصوصیِ خارج از Repository را بارگذاری کنید:

```text
--spring.config.additional-location=file:///C:/private/application-local-private.properties
```

مسیر بالا نمونه است. فایل واقعی باید شامل تنظیمات همان محیط و کلید JWT پایدار باشد و خارج از Git بماند. دیتابیس خالی برای ساخت حساب اولیه به رمز صریح مدیر نیاز دارد؛ کاربران موجود تغییر نمی‌کنند. Build و تست‌ها نیازی به اجرای برنامه روی دیتابیس اصلی ندارند.

اجرای عادی برنامه با تنظیمات دیتابیس اصلی ممکن است Hibernate و تعمیرات startup را فعال کند؛ برای بررسی یا تست، برنامه را با آن تنظیمات راه‌اندازی نکنید. فایل‌های `uploads`، پشتیبان دیتابیس و رکوردهای واقعی بخشی از سورس قابل انتشار نیستند.

## توسعه از Cloud و آیفون

راهنمای [GitHub، Codex Cloud و آیفون](docs/ai-context/GITHUB_CLOUD_GUIDE_FA.md) شامل اتصال Repository خصوصی، اجرای تست‌ها، Pull Request و دریافت تغییرات روی لپ‌تاپ است. قواعد الزامی توسعه در [AGENTS.md](AGENTS.md) ثبت شده‌اند.
