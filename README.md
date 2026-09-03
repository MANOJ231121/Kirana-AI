# Kirana Store AI Voice Assistant

A real phone-based Hinglish AI voice assistant for a local kirana store.

## Project Overview

This system allows customers to:
1. Send grocery lists through WhatsApp
2. Call the shopkeeper's phone number
3. Talk naturally to an AI assistant in Hindi, English, or Hinglish
4. Add/remove items and change quantities during the call
5. Reference their WhatsApp grocery list during the call
6. Specify pickup time and confirm the order

The **phone call itself is the user interface** - no website, app, or login needed. Orders are stored in MongoDB and appear **live on the shopkeeper dashboard** as they are placed.

## Architecture

```
                 CUSTOMER
                    │
              Real Phone Call
                    │
                    ▼
          ┌───────────────────┐
          │    TELEPHONY      │
          │      Twilio       │
          └─────────┬─────────┘
                    │   Media Streams (WebSocket, G.711 μ-law)
                    ▼
          ┌───────────────────┐
          │ Speech-to-Text    │
          │   Deepgram (nova) │  streaming Hinglish STT
          └─────────┬─────────┘
                    ▼
          ┌───────────────────┐
          │     AI AGENT      │
          │   Groq (llama-3.3)│  order-aware
          └───────┬─┬─────────┘
                  │ │
          ┌───────┘ └───────────┐
          ▼                     ▼
     WhatsApp list          MongoDB
     (reference)       (orders, customers,
     Inventory                sessions)
          │
          ▼
          ┌──────────────┐
          │   RIME API   │
          │     TTS      │  MANDATORY, audio/PCMU
          └──────┬───────┘
                 │
                 ▼
          ┌──────────────┐
          │  TELEPHONY   │
          └──────┬───────┘
                 ▼
           CUSTOMER PHONE
                 
 ┌─────────────────────────────────────┐
 │   SHOPKEEPER DASHBOARD (STOMP/WSS)  │
 │   /dashboard.html - live orders     │
 └─────────────────────────────────────┘
```

## Flow (How a Call Works)

1. **Customer dials** the Twilio number.
2. **Twilio** POSTs to `/voice`, which returns TwiML that opens a **Media Stream** (WebSocket) to `/media-stream`.
3. Customer audio (μ-law 8kHz base64) streams through the WebSocket to **Deepgram** for live Hinglish STT.
4. The transcript is sent to **Groq** (`llama-3.3-70b-versatile`) via `AiOrderAgentService`, which manages the order state and references the customer's WhatsApp list.
5. The AI's Hinglish text reply is synthesized by **Rime TTS** (`coda`, `lang=hi`, `audio/PCMU`) and streamed back to Twilio as audio.
6. The resulting order is persisted to **MongoDB** and pushed to the **shopkeeper dashboard** in real time via STOMP WebSocket.

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Telephony | Twilio (Media Streams) | Answers calls, real-time bidirectional audio |
| Backend | Java + Spring Boot 3.2.5 | Server, AI orchestration, WebSocket |
| STT | Deepgram (nova-3, language=hi) | Streaming speech-to-text |
| LLM | Groq (llama-3.3-70b-versatile) | Understands requests, manages order state |
| TTS | **Rime** (coda, nadi/taru) | AI voice responses (**mandatory**) |
| Database | MongoDB | Customers, orders, inventory, call sessions |
| Dashboard | HTML/JS + STOMP/SockJS | Live shopkeeper order view |

## Setup Instructions

### Prerequisites

1. **Java 17+** and **Maven** installed
2. **MongoDB** running locally (default `mongodb://localhost:27017`)
3. **Twilio account** - https://www.twilio.com/try-twilio
4. **ngrok** - https://ngrok.com/download (to expose localhost to Twilio)
5. API keys:
   - Twilio Account SID & Auth Token + phone number
   - **Rime** API key (https://app.rime.ai/tokens)
   - Deepgram API key (https://console.deepgram.com)
   - Groq API key (https://console.groq.com/keys)

### Step 1: Configure Environment Variables

Copy `.env.example` to `.env` and fill in your API keys. These are read from the environment at runtime.

On Windows (PowerShell):
```powershell
$env:TWILIO_ACCOUNT_SID="your_account_sid"
$env:TWILIO_AUTH_TOKEN="your_auth_token"
$env:TWILIO_PHONE_NUMBER="+14155552671"
$env:RIME_API_KEY="your_rime_key"
$env:RIME_SPEAKER="nadi"          # nadi (female) or taru (male)
$env:DEEPGRAM_API_KEY="your_deepgram_key"
$env:GROQ_API_KEY="your_groq_key"
$env:MONGODB_URI="mongodb://localhost:27017/kirana_store"
$env:SERVER_PORT="8080"
$env:PUBLIC_BASE_URL="https://abc123.ngrok.io"
```

### Step 2: Run MongoDB

MongoDB must be running:
```bash
mongod --dbpath C:\data\db
```

### Step 3: Run the Application

```bash
mvn spring-boot:run
```

Verify at `http://localhost:8080/health`.

### Step 4: Expose with ngrok

```bash
ngrok http 8080
```
Copy the HTTPS URL (e.g., `https://abc123.ngrok.io`) and set it as `PUBLIC_BASE_URL`.

### Step 5: Configure Twilio Webhook

1. [Twilio Console](https://console.twilio.com) → **Phone Numbers** → **Your Number**
2. Under **A CALL COMES IN**, select **Webhook**, POST, and enter:
   `https://your-ngrok-url.ngrok.io/voice`
3. Save.

### Step 6: Test

1. Open `http://localhost:8080/dashboard.html` to see the live shopkeeper dashboard.
2. Call your Twilio number and speak in Hindi/Hinglish.
3. Watch the order appear live on the dashboard.

## Current Status

- ✅ Twilio inbound calls answered via `/voice`
- ✅ Real-time **Media Streams** WebSocket (`/media-stream`) for bidirectional audio
- ✅ **Deepgram** streaming STT (Hinglish, `language=hi`)
- ✅ **Groq** LLM order agent (`AiOrderAgentService`) with order/customer state
- ✅ **Rime** mandatory TTS (PCMU) responses streamed back to the caller
- ✅ **MongoDB** persistence (customers, orders, inventory, call sessions, WhatsApp messages)
- ✅ **OrderParsingService** parses grocery list text into structured `OrderItem`s
- ✅ **Shopkeeper dashboard** (`/dashboard.html`) with live WebSocket order updates
- ✅ REST API (`/api/orders`) for the dashboard

## Project Structure

```
src/main/java/com/kirana/assistant/
├── KiranaAssistantApplication.java
├── config/
│   ├── WebSocketConfig.java              # STOMP broker for dashboard (/ws-dashboard)
│   └── MediaStreamWebSocketConfig.java   # Twilio Media Streams endpoint (/media-stream)
├── controller/
│   ├── VoiceWebhookController.java       # POST /voice  (call entry)
│   ├── OrderController.java              # GET/POST /api/orders
│   └── HealthController.java             # GET /health
├── dto/
│   └── OrderResponse.java
├── model/                                # MongoDB entities
│   ├── Customer.java
│   ├── Order.java / OrderItem.java
│   ├── InventoryItem.java
│   ├── WhatsAppMessage.java
│   └── CallSession.java
├── repository/                           # Spring Data Mongo repositories
├── service/
│   ├── TwilioService.java                # TwiML generation (Stream/Connect)
│   ├── DeepgramService.java              # streaming STT
│   ├── GroqLlmService.java               # Groq chat completions
│   ├── RimeTtsService.java               # Rime TTS (PCMU) - mandatory
│   ├── AiOrderAgentService.java          # order-aware conversational agent
│   ├── OrderParsingService.java          # text -> OrderItem list
│   └── DashboardNotifierService.java     # pushes updates to dashboard
└── websocket/
    └── MediaStreamsHandler.java          # handles Twilio stream events
```

## Security

- Never commit `.env` - it's in `.gitignore`.
- API keys are read from environment variables, never hardcoded.
- Logs do not print API keys.

## Troubleshooting

### Dashboard never connects to WebSocket
Ensure the app is running and open `dashboard.html` through the app (not file://). Check the connection badge turns green.

### Call connects but no voice / no STT
- Set `PUBLIC_BASE_URL` to the ngrok HTTPS URL **before** starting the app (it is used to build the `/media-stream` WebSocket URL).
- Check `mvn spring-boot:run` logs for `Media Stream started`.
- Verify `RIME_API_KEY`, `DEEPGRAM_API_KEY`, and `GROQ_API_KEY` are set.

### App won't start
- MongoDB must be running.
- Verify Java 17+ (`java -version`) and Maven (`mvn -version`).
- Check for port conflicts (8080).
