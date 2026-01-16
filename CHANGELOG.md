# Changelog

## [1.0.1] - 2026-01-16

### Fixed

- Prevented unintended bullet prefixes from being added to every text line when sharing multi-line text. Bullet prefixes (`—`) are no longer added to text-only or text-containing shares.
- Shared plain text is now preserved exactly as provided by the source app.
- Fixed handling of mixed shares (text + attachments) so text is no longer suppressed by attachment handling.
- Url fetching only when necessary and optimized.

## [1.0.0] – 2026-04-01

### Added

- Initial release of Share to Email.
- Support for sharing text, URLs, and attachments via Android share intents.
- Settings screen for configuring recipient slots and default email app.
