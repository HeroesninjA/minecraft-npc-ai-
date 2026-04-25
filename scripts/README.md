# Scripts

## OpenAI Debug

Script: `scripts/debug-openai.ps1`

Exemple:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\debug-openai.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\debug-openai.ps1 -IncludeResponseTest
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\debug-openai.ps1 `
  -BaseUrl "https://api.openai.com/v1" `
  -Model "gpt-5.4-nano" `
  -ApiKey "sk-..."
```

Ce face:

- citeste `openai.base_url`, `openai.model` si `openai.api_key` din `config.yml` daca exista
- foloseste `OPENAI_API_KEY` si `OPENAI_BASE_URL` ca fallback
- sondeaza `GET /models/{model}`
- optional testeaza si `POST /responses`
- mascheaza cheia API in log

Output:

- log principal in `debug-logs/openai-debug-YYYYMMDD-HHMMSS.log`
