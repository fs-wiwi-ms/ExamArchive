import logging
import os
import requests
import azure.functions as func
from azure.identity import DefaultAzureCredential

app = func.FunctionApp()

@app.timer_trigger(schedule="0 */15 * * * *", arg_name="timer", run_on_startup=False, use_monitor=False)
def credit_reporter_cron(timer: func.TimerRequest) -> None:
    if timer.past_due:
        logging.info("The timer is running late.")

    billing_account_id = os.environ.get("BILLING_ACCOUNT_ID")
    billing_profile_id = os.environ.get("BILLING_PROFILE_ID")
    webhook_urls_raw = os.environ.get("WEBHOOK_URLS", "")
    webhook_bearer_token = os.environ.get("WEBHOOK_BEARER_TOKEN")

    if not billing_account_id or not billing_profile_id:
        logging.error("Missing BILLING_ACCOUNT_ID or BILLING_PROFILE_ID settings.")
        return

    webhook_urls = [u.strip() for u in webhook_urls_raw.split(",") if u.strip()]
    if not webhook_urls:
        logging.error("No valid URLs found in WEBHOOK_URLS.")
        return
    try:
        credential = DefaultAzureCredential()
        token_object = credential.get_token("https://management.azure.com/.default")
        azure_token = token_object.token
    except Exception as e:
        logging.error(f"Failed to obtain Managed Identity credential: {e}")
        return

    arm_headers = {
        "Authorization": f"Bearer {azure_token}",
        "Content-Type": "application/json"
    }
    lots_url = (
        f"https://management.azure.com/providers/Microsoft.Billing/billingAccounts/{billing_account_id}"
        f"/billingProfiles/{billing_profile_id}/providers/Microsoft.Consumption/lots?api-version=2024-08-01"
    )

    total_credit_eur = 0.0
    try:
        res_lots = requests.get(lots_url, headers=arm_headers, timeout=10)
        res_lots.raise_for_status()
        lots_data = res_lots.json()

        for lot in lots_data.get("value", []):
            props = lot.get("properties", {})
            orig_billing = props.get("originalAmountInBillingCurrency", {})
            if orig_billing.get("value") is not None:
                total_credit_eur += float(orig_billing["value"])
    except Exception as e:
        logging.error(f"Error fetching Lots API: {e}")
        return
    balance_url = (
        f"https://management.azure.com/providers/Microsoft.Billing/billingAccounts/{billing_account_id}"
        f"/billingProfiles/{billing_profile_id}/providers/Microsoft.Consumption/credits/balanceSummary?api-version=2026-06-01"
    )

    current_amount_eur = 0.0
    try:
        res_balance = requests.get(balance_url, headers=arm_headers, timeout=10)
        res_balance.raise_for_status()
        balance_data = res_balance.json()

        props = balance_data.get("properties", {})
        summary = props.get("balanceSummary", {})
        est_billing = summary.get("estimatedBalanceInBillingCurrency", {})

        if est_billing.get("value") is not None:
            current_amount_eur = float(est_billing["value"])
    except Exception as e:
        logging.error(f"Error fetching Balance Summary API: {e}")
        return
    payload = {
        "credit": round(total_credit_eur, 2),
        "currentAmount": round(current_amount_eur, 2)
    }
    webhook_headers = {"Content-Type": "application/json"}
    if webhook_bearer_token:
        webhook_headers["Authorization"] = f"Bearer {webhook_bearer_token}"

    for url in webhook_urls:
        try:
            resp = requests.post(url, json=payload, headers=webhook_headers, timeout=10)
            logging.info(f"Webhook pushed to {url} - Status Code: {resp.status_code}")
        except Exception as e:
            logging.error(f"Failed to post webhook to {url}: {e}")