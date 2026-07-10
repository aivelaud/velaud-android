# Velaud Android

Native Kotlin Android app — AI chat aggregator supporting 7 models via Azure endpoints.

## Setup

### 1. Clone
```bash
git clone https://github.com/aivelaud/velaud-android.git
cd velaud-android
```

### 2. Secrets (`gradle.properties`)
Copy `gradle.properties.example` → `gradle.properties` and fill in your values:
```bash
cp gradle.properties.example gradle.properties
# Edit gradle.properties with real keys
```

### 3. `google-services.json`
Place your `google-services.json` in `app/google-services.json`.

### 4. Build
```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## CI / GitHub Actions
Set these repository secrets in **Settings → Secrets → Actions**:

| Secret | Value |
|---|---|
| `AZURE_KEY_PRIMARY` | DPG73W9c… |
| `AZURE_KEY_SECONDARY` | AIerXRjF… |
| `AZURE_CLAUDE_URL` | Claude endpoint |
| `AZURE_GPT_URL` | GPT 5.4 endpoint |
| `AZURE_GPT_PRO_URL` | GPT 5.4 Pro endpoint |
| `AZURE_KIMI_URL` | Kimi k-2.6 endpoint |
| `AZURE_DEEPSEEK_URL` | DeepSeek V4 endpoint |
| `TAVILY_KEY` | tvly-dev-… |
| `BACKEND_URL` | https://your-replit-backend |
| `GOOGLE_SERVICES_JSON` | Full JSON content of google-services.json |

## Backend
The Node.js + Express + SQLite backend lives in `backend/` and in the Replit api-server artifact.

## Screens
| Screen | Description |
|---|---|
| Login | Cream-bg auth with Google SSO + Email OTP |
| Verify | 6-box OTP with 30s resend |
| Home | Dark bg, floating logo, model pill, composer |
| Chat | SSE streaming, per-second thinking timer, action buttons |
| Drawer | 88% width nav with chat history |

## Models
- Claude Opus 4.7, Sonnet 4.6, Opus 4.6 (Anthropic / Azure)
- GPT 5.4, GPT 5.4 Pro (OpenAI Responses API / Azure)
- Kimi k-2.6 (Azure)
- DeepSeek V4 (Azure)
