# راهنمای GitHub خصوصی، Codex Cloud و آیفون

این راهنما برای یک Repository مشترک Backend و Frontend با نام `casting-production-management` است. ایجاد مخزن، Commit محلی، تأیید Push و بررسی نتیجه مراحل جداگانه‌اند؛ وجود این فایل به‌معنای انتشار موفق نیست. نتیجهٔ واقعی در `GITHUB_TRANSFER_REVIEW_FA.md` ثبت می‌شود.

نشانی مخزن تهیه‌شده: [aliidolati/casting-production-management](https://github.com/aliidolati/casting-production-management). Private بودن آن با API بررسی شده است؛ وضعیت انتشار سورس را از گزارش انتقال بخوانید.

## ورود GitHub و انتشار اولیه

پس از آماده بودن Git و GitHub CLI، وضعیت ورود را بررسی کنید:

```powershell
gh auth status
```

اگر وارد نشده‌اید، ورود را خودتان در ترمینال انجام دهید؛ رمز یا Token را در چت ارسال نکنید:

```powershell
gh auth login
```

نام کاربری مالک مخزن باید از حساب تأییدشده خوانده شود. پیش از ایجاد، بررسی شود که مخزنی با همین نام وجود ندارد. اگر وجود داشت، بدون هماهنگی از آن استفاده یا آن را بازنویسی نکنید. مخزن جدید باید با `--private` و بدون `--push` ساخته شود. قبل از Push، محتوای Commit و وضعیت امنیتی را مرور و تأیید کنید؛ سپس Push معمولی و بدون Force انجام شود. مقدار `visibility=PRIVATE` و شناسهٔ Commit محلی/Remote باید بعد از Push دوباره بررسی شوند.

طبق [راهنمای رسمی GitHub CLI](https://cli.github.com/manual/gh_repo_create)، ایجاد Repository و ارسال Commitها عملیات جداگانه‌اند. احراز هویت نیز با [gh auth login](https://cli.github.com/manual/gh_auth_login) انجام می‌شود.

## آماده‌سازی اولیهٔ محیط Cloud

طبق [راهنمای رسمی Codex Cloud](https://learn.chatgpt.com/docs/cloud)، محیط جدید را از نسخهٔ وب یا دسکتاپ بسازید؛ روی موبایل محیط منتشرشده را انتخاب کنید. نام گزینه‌ها و دسترسی ممکن است با حساب یا نسخهٔ رابط فرق کند.

1. با همان حساب ChatGPT وارد نسخهٔ وب شوید. از `Work in → Cloud → Select environment → Create environment` یا تنظیمات `Codex Cloud → Environments` محیط بسازید.
2. GitHub را متصل کنید و دسترسی را به Repository خصوصی همین پروژه محدود کنید. اگر مخزن نمایش داده نشد، اجازهٔ دسترسی GitHub App به آن Repository را بررسی کنید.
3. `casting-production-management` را انتخاب و `Get started` را بزنید. پیش‌نیاز پروژه JDK 17 یا جدیدتر و Maven Wrapper است؛ Frontend نصب npm ندارد.
4. برای Install/Setup، این دستور را مشخص کنید:

   ```bash
   bash scripts/codex-setup.sh
   ```

5. تأکید کنید که برنامه نباید با دیتابیس اصلی اجرا شود و هیچ رمز یا فایل واقعی تولید در اختیار Cloud قرار نگیرد. تست‌های معمول به این اطلاعات نیاز ندارند.
6. تست آماده‌سازی را اجرا کنید، نتیجه را مرور و محیط را `Publish` کنید. Private بودن Repository با تنظیم دسترسی محیط Cloud متفاوت است؛ برای محیط شخصی، دسترسی `Only me` را انتخاب کنید.

[مستندات محیط Cloud](https://learn.chatgpt.com/docs/environments/cloud-environments) توضیح می‌دهد که Install script برای وابستگی‌ها، Start skill برای سرویس‌ها و Publish برای آماده‌کردن محیط قابل استفاده است. این پروژه برای تست‌های معمول نیازی به Start برنامهٔ اصلی یا اتصال MySQL لپ‌تاپ ندارد.

## کار روزمره با آیفون

1. در برنامهٔ ChatGPT روی آیفون وارد حساب خود شوید و بخش `Codex` را باز کنید. محیط منتشرشدهٔ پروژه را انتخاب کنید. در صورت نیاز به ساخت یا تنظیم محیط، از نسخهٔ وب یا دسکتاپ استفاده کنید.
2. درخواست کوچک و مشخص بدهید؛ مثلاً:

   > ابتدا AGENTS.md و مستندات مرتبط را بخوان. در یک Branch جداگانه، مشکل … را با کمترین تغییر برطرف کن. قابلیت‌های موجود، قرارداد API، مجوزها و طراحی فعلی را حفظ کن. از دیتابیس اصلی استفاده نکن. تست‌های مرتبط را اجرا کن و نتیجه را در یک Pull Request برای بررسی آماده کن؛ در main ادغام نکن.

3. Diff، نتیجهٔ تست‌ها و توضیح تغییرات را بررسی کنید. اگر چیزی مبهم بود، در همان کار اصلاح بخواهید. کارهای Cloud روی زیرساخت راه‌دور انجام می‌شوند و پس از آماده‌سازی محیط، روشن‌بودن لپ‌تاپ لازم نیست.
4. Pull Request را در Codex یا GitHub باز کنید. تب‌های Files changed و Checks را مرور کنید. اگر `fast` یا `mysql` خطا دارد، از Codex بخواهید همان Branch را اصلاح کند.
5. پس از بررسی و تأیید، Pull Request را در GitHub Merge کنید. تغییرات مستقیم و بدون بازبینی در main انجام نشوند؛ Force Push لازم نیست.

## کدام تست کجا اجرا می‌شود؟

| بررسی | دستور | محل اجرا |
|---|---|---|
| تست واحد و Spring Boot/H2 | `bash scripts/test-backend.sh fast` | Cloud، لپ‌تاپ یا GitHub Actions؛ بدون دیتابیس اصلی |
| Build | `bash mvnw -B -ntp -DskipTests package` | Cloud یا لپ‌تاپ؛ بدون شروع برنامه |
| MySQL واقعی و migrationهای انتخاب‌شده | `bash scripts/test-backend.sh mysql` | محیط دارای Docker؛ مسیر پیش‌فرض GitHub Actions با Job نام `mysql` |

Workflow روی GitHub-hosted Ubuntu اجرا می‌شود و به روشن‌بودن لپ‌تاپ وابسته نیست. در محیط Cloud فاقد Docker، تست‌های MySQL باید در Actions اجرا شوند؛ پشتیبانی native اختیاری و محدودیت‌ها در [CLOUD_TESTING.md](CLOUD_TESTING.md) آمده است. تست‌های مرورگر هنوز opt-in هستند و پیش‌نیازهای Chrome/Node خودشان را دارند.

## دریافت تغییرات تأییدشده روی لپ‌تاپ

در همان پوشهٔ پروژه، ابتدا وضعیت تغییرات محلی را بررسی کنید:

```powershell
git status
git switch main
git pull --ff-only origin main
```

اگر تغییر محلی دارید، ابتدا آن را با دقت Commit یا در محل امن نگهداری کنید؛ از `reset --hard`، حذف فایل یا Force برای حل تعارض استفاده نکنید. اگر `--ff-only` خطا داد، شاخه‌ها جدا شده‌اند و باید تعارض آگاهانه بررسی شود.

روی لپ‌تاپ تازه، پس از ورود GitHub:

```powershell
gh repo clone aliidolati/casting-production-management
```

برای اجرای محلی، JDK و تنظیمات خصوصی دیتابیس/JWT را جداگانه فراهم کنید؛ آن تنظیمات و فایل‌های `uploads` عمداً از Git دریافت نمی‌شوند. قبل از اجرای تغییری که Schema را تغییر می‌دهد، migration و سازگاری نسخه را بررسی کنید؛ اجرای برنامه روی دیتابیس اصلی یک مرحلهٔ مستقل از دریافت سورس است.
