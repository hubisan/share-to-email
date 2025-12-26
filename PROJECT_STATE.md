# Share to Email – Project State

Repo: https://github.com/hubisan/share-to-email
Package: ch.hubisan.sharetoemail
Tech: Kotlin, Android Studio, Jetpack Compose, DataStore Preferences, Version Catalog

Share targets (activity-alias):
- Share to @A
- Share to @B
- Share to @C
  (no others / no picker)

Settings (MainActivity, Compose):
- Recipient email for @A
- Recipient email for @B
- Recipient email for @C
- Toggle: Fetch page titles (slow)
  Persisted via AppDataStore (DataStore Preferences)

AppDataStore keys:
- recipient_a_email
- recipient_b_email
- recipient_c_email
- fetch_titles_enabled

ShareActivity:
- Works and opens chooser; subject/body currently minimal placeholder.
  TODO: implement full ShareParser + EmailComposer (subject rules, HTML body list, attachments list, clipData, URL extraction), block video/*, optional title-fetch with timeouts.

Desired extra:
- Only show email apps (filter chooser), and optionally store a default email app (package/class) and launch it directly.
  Java/Kotlin:
- compileOptions Java 17, kotlinOptions jvmTarget 17 (Gradle JDK is JBR 21).
