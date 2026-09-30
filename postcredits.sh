curl -X POST http://127.0.0.1:1910/api/azure_webhook \
  -H "Authorization: Bearer $(grep EXAMARCHIVE_AZURE_WEBHOOK_SECRET .env | cut -d '=' -f2 | tr -d '\"' | tr -d "'")" \
  -H "Content-Type: application/json" \
  -d '{"credits": 190.0, "currentAmount": 160.0}' \
  --verbose