# ScrollFit — Exercise Gate

پوستهٔ اولیهٔ یک برنامهٔ بومی اندروید با Kotlin و Jetpack Compose برای مدیریت زمان استفاده از برنامه‌ها و کسب زمان با ورزش. درخواست فعلی فقط **T01** است؛ وظایف T02 تا T16 اجرا نشده‌اند. مشخصات محصول در [brief](exercise_gate_codex_brief.md)، ترتیب وظایف در [tasks](exercise_gate_tasks.md) و وضعیت واقعی در [TASK_STATUS.md](TASK_STATUS.md) ثبت شده‌اند.

## وضعیت این نسخه

صفحهٔ آغاز، خانه، معرفی چهار ورزش، سابقهٔ خالی و نمایش پیش‌فرض‌ها به فارسی با چیدمان RTL اضافه شده‌اند. این نسخه هنوز برنامه‌ها را پایش یا مسدود نمی‌کند، دوربین را باز نمی‌کند، تکرار نمی‌شمارد و پاداش نمی‌دهد. این محدودیت در خود صفحه‌ها نیز نوشته شده است. همهٔ دکمه‌های فعال فقط جابه‌جایی واقعی بین صفحه‌ها انجام می‌دهند.

گوشی مقصد طبق پاسخ مالک: **Xiaomi Mi 11 Lite NE، Android 14 (API 34)**. حداقل نسخهٔ موجود پروژه، Android 9 (API 28)، حفظ شده است؛ compile/target SDK برابر 37 است. تست نصب، RTL و رفتار دکمهٔ بازگشت روی گوشی هنوز انجام نشده است.

**ساخت APK هنوز تأیید نشده است.** محیط WSL در شروع Java، Android SDK، adb و emulator نداشت. JDK موقت در `/tmp/scrollfit-toolchain` تهیه شد. در sandbox، سرورهای دانلود با خطای DNS مواجه شدند؛ خارج از sandbox، URLهای رسمی Android SDK و Google Maven پاسخ HTTP 404 دادند. دریافت مستندات رسمی با ابزار مرور وب و دریافت JDK از Adoptium امکان‌پذیر بود. دستور ساخت با exit 1 و خطای حل پلاگین AGP 9.4.0 در مرحلهٔ configuration متوقف شد؛ کامپایل، lint و تست اجرا نشدند و APK وجود ندارد. [گزارش کامل ساخت](docs/verification/T01-build.log) و [نسخهٔ ابزار اجراشده](docs/verification/T01-gradle-version.log) در پروژه نگه داشته شده‌اند. وضعیت T01 برابر blocked است. ابزارهای موقت در `/tmp` بخشی از پروژه نیستند و ممکن است حذف شوند.

## ساخت و نصب

در Android Studio سازگار با AGP 9.4، پوشهٔ پروژه را باز کنید. JDK 21 را به‌عنوان Gradle JDK انتخاب کنید؛ از SDK Manager، Android SDK Platform 37، Build Tools 36.0.0 و Platform Tools را نصب کنید. برای ساخت اولیه، دسترسی به Google Maven، Maven Central، Gradle Plugin Portal و سرور Gradle لازم است. مسیر SDK را Android Studio در `local.properties` ثبت می‌کند؛ این فایل نباید commit شود. در WSL باید SDK و JDK لینوکس استفاده شوند، نه ابزارهای ویندوز.

روی Linux/WSL:

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug :app:lintDebug :core:test --console=plain
```

روی Windows با PowerShell و JDK/SDK ویندوز:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :core:test --console=plain
```

فقط **بعد از BUILD SUCCESSFUL** خروجی مورد انتظار `app/build/outputs/apk/debug/app-debug.apk` است. تا وقتی فایل واقعاً ایجاد نشده، این مسیر تحویل APK محسوب نمی‌شود. پس از ساخت موفق، APK را به گوشی منتقل و باز کنید؛ اجازهٔ نصب از همان برنامهٔ فایل/مرورگر را بدهید. با USB debugging نیز می‌توان نصب کرد:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

چک کوتاه T01 روی گوشی: برنامه از launcher باز شود؛ از معرفی وارد خانه شوید؛ خانه، ورزش‌ها، سابقه و تنظیمات قابل دسترسی باشند؛ چهار ورزش دیده شوند؛ چرخش صفحه، بازگشت، حالت تاریک و اندازهٔ بزرگ متن بررسی شوند. این چک هیچ ادعایی دربارهٔ دقت دوربین یا مسدودسازی ندارد. چک کامل محصول در brief باقی است و در T16 اجرا خواهد شد.

## مرزهای ماژول‌ها

| ماژول | مسئولیت فعلی و مسیر توسعه |
| --- | --- |
| `:app` | Activity، منابع فارسی، تم Compose، navigation و صفحه‌های معرفی؛ اتصال جریان چالش در T11 و تکمیل UI در T14 |
| `:core` | Kotlin/JVM بدون Android؛ نوع چهار ورزش، پیشنهادهای محصول، قرارداد pose/counter، ساعت قابل تزریق و قرارداد monitoring/blocking؛ منطق مستقل بودجه در T04 و شمارنده‌های مستقل در T07–T10 |
| `:platform` | کتابخانهٔ Android؛ پیاده‌سازی ساعت‌های wall/monotonic و وابستگی‌های CameraX/Room/DataStore/MediaPipe؛ adapterهای persistence در T03، monitoring/blocking در T02/T05 و pose در T06 |

جهت وابستگی: `app → platform → core` و `app → core`. قراردادهای شمارش هیچ تصویر یا کلاس Android دریافت نمی‌کنند؛ adapter تخمین pose مالک frame و منابع دوربین خواهد بود. واحدهای زمان در نام‌ها صریح‌اند: seconds برای پیش‌فرض‌ها و milliseconds برای timestamps. هیچ backend یا چارچوب DI اضافه نشده است. مدل Room و KSP عمداً همراه با schema واقعی در T03 اضافه خواهند شد؛ وجود وابستگی runtime به معنای پیاده‌سازی persistence نیست.

## انتخاب وابستگی‌ها و منابع رسمی

بررسی مستندات در **2026-10-12** انجام شد. این جدول انتخاب‌های مستند را ثبت می‌کند؛ حل وابستگی‌ها و سازگاری نهایی APK به ساخت موفق وابسته است. نسخه‌ها در `gradle/libs.versions.toml` ثابت‌اند؛ از `latest.release` استفاده نمی‌کنیم.

| مورد | انتخاب | مبنای تصمیم |
| --- | --- | --- |
| AGP / Gradle | 9.4.0 / 9.6.0 | [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes): SDK 37، Gradle 9.6 و حداقل JDK 17؛ 9.4.0 در مستندات تأیید شد، در حالی که artifact نسخهٔ اولیهٔ 9.4.1 قابل دریافت نبود |
| JVM ساخت / bytecode | JDK 21 / Java 17 | JDK 21 برای daemon به‌جای الزام اولیهٔ 25؛ ابزار به پروژه یا سیستم نصب نشده است |
| Kotlin / Compose compiler | 2.4.10 برای هر دو | [Kotlin releases](https://kotlinlang.org/docs/releases.html)، [Kotlin/AGP requirements](https://developer.android.com/build/kotlin-support)، [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): Kotlin داخلی AGP، بدون `kotlin-android`؛ KGP در buildscript صریحاً هم‌نسخه می‌شود |
| Compose | stable BOM 2026.09.00، Material 3 | [BOM guide](https://developer.android.com/develop/ui/compose/bom)؛ compiler نسخهٔ مستقل و هم‌نسخه با Kotlin دارد طبق [compatibility guide](https://developer.android.com/jetpack/androidx/releases/compose-kotlin) |
| Activity / Navigation Compose | 1.13.0 / 2.10.2 | [Activity stable releases](https://developer.android.com/jetpack/androidx/releases/activity)، [Navigation releases](https://developer.android.com/jetpack/androidx/releases/navigation)؛ برای این پوسته NavHost ساده کافی است |
| CameraX | 1.6.2 | [CameraX stable releases](https://developer.android.com/jetpack/androidx/releases/camera)؛ camera2، lifecycle و view با نسخهٔ یکسان |
| Room | 2.8.5 | [Room stable releases and KSP guidance](https://developer.android.com/jetpack/androidx/releases/room)؛ تراکنش‌ها و schema در T03 |
| DataStore Preferences | 1.2.1 | [DataStore stable releases](https://developer.android.com/jetpack/androidx/releases/datastore)؛ ذخیرهٔ تنظیمات در T03 |
| Pose engine | MediaPipe Tasks Vision 1.0.0 | نسخهٔ ثابت در [نمونهٔ رسمی Google](https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/pose_landmarker/android/app/build.gradle)؛ [Android pose guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android) و [platform requirements](https://developers.google.com/edge/mediapipe/solutions/setup_android)؛ حداقل SDK آن از حداقل پروژه کمتر است |

### مدل pose و اجرای آفلاین

انتخاب: **Pose Landmarker Lite، float16، نسخهٔ مدل 1، CPU delegate و یک بدن**. MediaPipe بستهٔ مدل صریح، confidence و landmarkهای نرمال‌شده دارد. گزینهٔ ML Kit نیز بررسی شد، اما [Pose Detection آن beta است](https://developers.google.com/ml-kit/vision/pose-detection/android)؛ برای این پروژه بسته‌بندی صریح مدل در MediaPipe انتخاب شد.

در T06 فایل از [منبع رسمی مدل Lite](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task) یک‌بار هنگام آماده‌سازی build گرفته، SHA-256 و شرایط مدل ثبت و در `platform/src/main/assets/pose_landmarker_lite.task` بسته‌بندی خواهد شد. فعلاً فقط نام و URL مدل در `PoseModel.kt` تعریف شده؛ **مدل هنوز دریافت یا در APK بسته‌بندی نشده و inference فعال نیست**. T06 باید وجود و checksum مدل را پیش از build الزام کند و در حالت airplane، اولین اجرای بعد از نصب را آزمایش کند. هیچ دانلود runtime، ثبت frame یا شبکهٔ inference مجاز نیست. نبود یا خراب بودن مدل باید خطای قابل فهم بدهد. tuning دوربین روی گوشی انجام می‌شود.

## پیشنهادهای قابل تغییر محصول

سهمیهٔ اولیهٔ هر برنامه ۳۰ دقیقه، سقف مصرف مجموع ۶۰ دقیقه، سقف زمان پاداش روزانهٔ هر برنامه ۲۰ دقیقه و نوبت کوتاه ۵ دقیقه است. حالت سهمیهٔ صفر پیش‌فرض خاموش، صدا روشن و دوربین پیشنهادی پشت است. پیشنهاد پاداش: ۲ شنا، ۱۰ اسکوات، ۲۰ پروانه یا ۲۰ بالا آوردن متناوب زانو، هرکدام ۲ دقیقه. این مقادیر نسخهٔ آینده قابل ویرایش‌اند؛ توصیهٔ تمرینی یا اندازه‌گیری تناسب اندام نیستند.

## دسترسی‌ها و محدودیت‌ها

پوستهٔ T01 هیچ دسترسی camera، internet، Usage Access، Accessibility یا overlay درخواست نمی‌کند. در مراحل بعد، پایش تنها با فعال‌سازی صریح مالک و دسترسی‌های لازم و دوربین تنها روی صفحهٔ ورزش اجرا خواهد شد. مکانیزم مسدودسازی باید در T02 روی Mi 11 Lite NE اثبات شود؛ به باز شدن arbitrary activity در پس‌زمینه تکیه نمی‌کنیم. Usage Access به‌تنهایی برنامه را suspend نمی‌کند. تنظیمات، launcher و مسیرهای ضروری باید در دسترس بمانند؛ غیرفعال کردن دسترسی، force stop و حذف برنامه می‌توانند محدودیت را دور بزنند.

تصمیم‌های حسابداری برای مراحل بعد: rollover بر مبنای نیمه‌شب محلی، انقضای اعتبار مصرف‌نشده و لغو چالش پاداش قدیمی بدون پاداش روز جدید؛ elapsed duration با ساعت monotonic. تغییر عمدی ساعت گوشی در v1 سخت‌سازی نمی‌شود. سیاست split-screen/PiP، بازیابی پس از reboot و میزان بیشینهٔ اتلاف حسابداری هنوز به آزمایش monitoring وابسته‌اند و قطعی نشده‌اند. دادهٔ اجرای T01 وجود ندارد؛ نسخهٔ آینده فقط metadata، زمان، تکرار و ledger را ذخیره می‌کند.
