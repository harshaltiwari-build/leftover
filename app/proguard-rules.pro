# Room and Compose ship their own consumer rules. Add app-specific keep rules here.

# Tesseract4Android's native code reads and writes Java fields and calls back by name, and the library ships no rules.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
