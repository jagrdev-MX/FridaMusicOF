# FridaMusic TM Google Play Distribution Policy

## Official release authority
Only releases explicitly authorized by the owner (@jagrdev-MX) are official FridaMusic TM releases.

## Reserved items
The following are reserved for official distribution:
- Official Google Play listing
- Official app name and branding presentation
- Official screenshots and listing artwork
- Official package name used for production release
- Official APK/AAB release identity

## Fork and redistribution guidance
Forks are allowed under Apache 2.0 for source code, but must not be published in Google Play as official FridaMusic TM builds or under confusingly similar branding.

## Security and signing policy
- Production signing keys are private and must never be committed to the repository.
- Keystores, secrets, and signing credentials must stay outside version control.
- CI/CD signing material must be managed through secure secret stores.
