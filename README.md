# MyAssistant — Human Chat v4

Premium Android AI chat focused on natural Hindi/Hinglish/English conversation and coding.

- Persistent local chat history
- New chat
- Sidebar history
- Long press chat: Rename/Delete
- Coding help
- No phone/app control
- No accessibility control
- No background wake word
- Microphone is only used when the user taps the mic button for speech input
- Groq API key is NOT entered in the app
- Local builds read `GROQ_API_KEY` from the build environment
- GitHub Actions reads `GROQ_API_KEY` from GitHub Actions Secrets

## Local build

```bash
export GROQ_API_KEY="YOUR_KEY"
./gradlew assembleDebug
```

## GitHub

Repository Settings → Secrets and variables → Actions → New repository secret

Name:
`GROQ_API_KEY`

Value:
your Groq API key

The APK will receive the key at build time through BuildConfig. The key is never stored in the repository source.
