# Kirana AI — Voice-Based Kirana Store Ordering System

Web-based AI assistant for a local Indian kirana store. Customers speak in the
browser to build a grocery order; the shopkeeper sees it live on a dashboard.

> No Twilio, no phone calls in this build. Microphone + AI run entirely
> through the browser and the Spring Boot backend.

## 1. Project description

Two sides, one backend:

- **Customer** (`/customer`): store menu with prices, basket, mic button,
  **always-on wake-word assistant (“Kirana…” / “Siri…”)**, AI Hinglish replies,
  confirm → order ID + live status.
- **Shopkeeper** (`/shopkeeper`): **login gate (Bearer token auth)**,
  status tabs, order cards, one-click
  `ACCEPT → PREPARING → READY → COMPLETED` (or `REJECT`/`CANCEL`), live
  `🔔 New Order Received` toasts over WebSocket — no refresh needed.

Legacy phone-call code (`/api/web-call`, `AiOrderAgentService`, static
`dashboard.html`/`call.html`) is preserved untouched for reference.

## 2. Architecture

```
                         CUSTOMER
                            │
                            ▼
                    ┌───────────────┐
                    │ React Web App │
                    └───────┬───────┘
                            │
                       🎤 Microphone
                            │
                            ▼
                    Speech-to-Text (Deepgram via backend, else Web Speech)
                            │
                            ▼
                     LLM (Groq via backend, else mock)
                            │
                    Structured Order (intent + items, validated)
                            │
                            ▼
                    ┌───────────────┐
                    │  Spring Boot  │─── MongoDB
                    │    Backend    │─── WebSocket (/topic/orders, /topic/calls)
                    └───────────────┘
                                   │
                                   ▼
                         ┌──────────────────┐
                         │ React Shopkeeper │
                         │    Dashboard     │
                         └──────────────────┘
```

Request flow for a new order:

```
Customer React → POST /api/orders → Spring Boot → MongoDB → WS event → Shopkeeper React
Shopkeeper → PATCH /api/orders/{id}/status → MongoDB → WS event → Customer sees status
```

## 3. Tech stack

| Layer    | Tech                                            |
|----------|-------------------------------------------------|
| Frontend | React 18, Vite 5, react-router, SockJS + STOMP  |
| Backend  | Java 17+, Spring Boot 3.2.5, Web, Validation, WebSocket, Data MongoDB, WebFlux (provider calls) |
| DB       | MongoDB 7 (docker-compose)                      |
| STT      | `SpeechToTextService` → `GroqSpeechToTextService` (Whisper) / `MockSpeechToTextService` / Web Speech API |
| LLM      | `AIService` → `GroqAiService` / `MockAiService` |
| TTS      | `TextToSpeechService` → `RimeTextToSpeechService` / `MockTextToSpeechService` |

Keys never leave the backend. The browser only sends audio clips / text and
receives transcripts, replies, and base64 audio.

## 4. Folder structure

```
.
├── backend/                     # Java Spring Boot Backend
│   ├── src/                     # Source code & resources
│   ├── pom.xml                  # Maven configuration
│   ├── .env                     # Backend environment configuration
│   └── .env.example
├── frontend/                    # Vite React app
│   ├── src/
│   │   ├── pages/               # CustomerPage, ShopkeeperPage, Home
│   │   ├── components/          # (reserved)
│   │   ├── services/            # api.js, audio.js
│   │   ├── hooks/               # useSpeech.js
│   │   ├── websocket/           # useOrdersSocket.js
│   │   ├── App.jsx main.jsx styles.css
│   ├── package.json vite.config.js index.html
│   └── .env.example
├── docker-compose.yml           # MongoDB
├── start-all.ps1                # Start full stack script
├── stop-all.ps1                 # Stop processes script
├── run.ps1                      # Backend launch script
├── load-env.ps1                 # Environment loader script
└── README.md
```

## 5. Prerequisites

- Java 17+ (`java -version`), Maven (`mvn -version`)
- Node 18+ (`node --version`), npm
- MongoDB — easiest via Docker: `docker compose up -d`
- Optional AI keys (app works without them via mocks): `GROQ_API_KEY`, `RIME_API_KEY`

## 6. Environment variables

Backend (`backend/.env`, see `backend/.env.example`):

```
MONGODB_URI=mongodb://localhost:27017/kirana_store
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
GROQ_API_KEY=        # or LLM_API_KEY=
GROQ_LLM_MODEL=llama-3.3-70b-versatile
GROQ_WHISPER_MODEL=whisper-large-v3-turbo
RIME_API_KEY=
RIME_SPEAKER=nadi
SEED_DATA=true
```

Load into PowerShell: `. .\load-env.ps1`

Frontend (`frontend/.env`, see `frontend/.env.example`):

```
VITE_API_URL=http://localhost:8080
VITE_WS_URL=http://localhost:8080/ws-dashboard
```

## 7. MongoDB setup

```bash
docker compose up -d
docker ps   # kirana-mongo on localhost:27017
```

Or run a local `mongod` with dbpath of your choice.

## 8. Backend setup

```bash
cd backend
mvn clean compile
mvn test -Dtest='OrderServiceTest,ConversationServiceTest'
```

## 9. Frontend setup

```bash
cd frontend
npm install
npm run dev
```

## 10. How to run

```bash
docker compose up -d          # MongoDB
cd backend; mvn spring-boot:run # backend :8080
cd frontend; npm run dev      # frontend :5173
```

Open:

```
Frontend:   http://localhost:5173
Customer:   http://localhost:5173/customer     # menu + wake-word assistant + basket
Shopkeeper: http://localhost:5173/shopkeeper   # login, then live orders
Backend:    http://localhost:8080
Health:     http://localhost:8080/health
MongoDB:    localhost:27017
```

Default shopkeeper login: `shopkeeper / changeme` — set
`SHOPKEEPER_USERNAME` / `SHOPKEEPER_PASSWORD` in `.env` immediately.

Manual end-to-end check:

```
Customer creates order → MongoDB stores → Shopkeeper sees 🔔
→ ACCEPTED → customer sees ACCEPTED → PREPARING → READY → COMPLETED
```

## 11. API endpoints

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/api/orders` | `{customerName, customerPhone?, items:[{name,quantity,unit}], pickupTime?}` | → 201, **public** |
| GET | `/api/orders` | — | optional `?status=PENDING`, **🔒 shopkeeper** |
| GET | `/api/orders?status=` | — | filter, **🔒 shopkeeper** |
| GET | `/api/orders/{id}` | — | **public** (customer tracks own order), → 404 `{timestamp,status,message,path}` |
| PATCH | `/api/orders/{id}/status` | `{status}` | enforced transition graph, **🔒 shopkeeper** |
| DELETE | `/api/orders/{id}` | — | → 204, **🔒 shopkeeper** |
| POST | `/api/auth/login` | `{username, password}` | → `{token, username, expiresAt}` (12h Bearer) |
| POST | `/api/auth/logout` | — | revokes token |
| GET | `/api/auth/me` | — | **🔒** returns `{username}` |
| GET | `/api/inventory` | — | public menu `{id, name, price, category, available, unit}` |
| POST | `/api/conversation/message` | `{transcript, currentItems}` | → `{intent, items, replyText, …}` |
| POST | `/api/stt/transcribe` | multipart `audio` | Deepgram or mock |
| POST | `/api/tts/synthesize` | `{text}` | → `{audioBase64, mimeType, provider}` |
| GET | `/api/voice/status` | — | provider diagnostics |
| GET | `/health` | — | liveness |

Legacy aliases (kept): `POST /api/orders/{id}/status`,
`GET /api/orders/status/{status}`, `/api/web-call/*`.

Order statuses: `PENDING ACCEPTED PREPARING READY COMPLETED REJECTED CANCELLED`.
Valid flow: `PENDING→ACCEPTED→PREPARING→READY→COMPLETED`,
`PENDING→REJECTED`, cancel from `PENDING/ACCEPTED/PREPARING/READY`.
`COMPLETED→PENDING` etc. return 400.

## 12. WebSocket architecture

- STOMP broker at `/ws-dashboard` (SockJS), topics `/topic/orders`, `/topic/calls`
  (`WebSocketConfig`, `DashboardNotifierService`).
- `OrderService` broadcasts `CREATED / STATUS_* / DELETED` on every mutation,
  so the shopkeeper list refreshes live and the customer gets status toasts.
- React `useOrdersSocket.js` subscribes with reconnect-friendly defaults; the
  customer page additionally polls `GET /api/orders/{id}` every 5s as fallback.

## 13. AI/voice architecture

```
Browser mic (MediaRecorder 8s clips, or Web Speech API)
  → POST /api/stt/transcribe → Groq Whisper (large-v3-turbo) or browser WebSpeech / mock
  → transcript → POST /api/conversation/message → Groq LLM (json_object) or MockAiService
  → validated {intent, items, pickupTime, needsClarification, …}
  → React basket → POST /api/orders → MongoDB → WS → shopkeeper
LLM reply text → POST /api/tts/synthesize → Rime MP3 or mock ""
  → browser Audio play, fallback speechSynthesis (hi-IN)
```

**Always-on wake word** (`frontend/src/hooks/useWakeWord.js`, Chrome):
toggle “Wake: ON” → passive Web Speech listener scans everything →
“Kirana, 2 kilo atta” (same sentence) or “Kirana!” … “2 kilo atta”
(next sentence) → command flows through the same pipeline above into
the basket. Wake words: `kirana`, `siri` (`DEFAULT_WAKE_WORDS`).

**Shopkeeper auth** (`ShopkeeperAuthService` + `TokenStore` +
`ShopkeeperAuthInterceptor`): env credentials → 12h Bearer token in
`localStorage` → `Authorization: Bearer …` on list/status/delete calls.
401 auto-clears the token and shows the login screen again.

Swap providers by implementing `SpeechToTextService` / `AIService` /
`TextToSpeechService` — controllers and UI depend only on the interfaces.
Ambiguous input (`2 milk dena`) returns `needsClarification: true` with
`Kaunsa milk chahiye — Amul ya koi aur?` instead of guessing.

## 14. Troubleshooting

| Symptom | Fix |
|---------|-----|
| Backend won't start | MongoDB running? `docker compose up -d`; Java 17+? port 8080 free? |
| `Order must contain at least one item` (400) | Cart empty — add items first |
| `Invalid status transition` (400) | Follow PENDING→ACCEPTED→PREPARING→READY→COMPLETED |
| Shopkeeper sees nothing live | Open pages via `http://localhost:5173` (not `file://`); check WS badge; API URL in `frontend/.env` |
| Mic does nothing | Allow mic permission; use localhost/HTTPS; typed input always works |
| AI replies generic | No `GROQ_API_KEY` → deterministic mock; add key + restart for LLM |
| No TTS audio | No `RIME_API_KEY` → reply shown as text + browser speechSynthesis |
| Tests fail on new JDK | `OrderServiceTest`/`ConversationServiceTest` are Mockito-free and Mongo-free; run `mvn clean test -Dtest='OrderServiceTest,ConversationServiceTest'` |

## 15. Future Architecture & Extensibility

This system is built using a clean interface-driven architecture (`SpeechToTextService`, `AIService`, `TextToSpeechService`). If you wish to extend the platform in the future, follow these guides:

### Adding Deepgram Speech-To-Text
1. Create a class implementing `SpeechToTextService` (e.g. `DeepgramSpeechToTextService.java`).
2. Add `@Value("${DEEPGRAM_API_KEY:}")` property to read from `backend/.env`.
3. Wire the implementation in `ConversationController.java`:
   ```java
   if (groqStt.isAvailable()) return groqStt;
   if (deepgramStt.isAvailable()) return deepgramStt;
   return mockStt;
   ```

### Adding Twilio Telephony Support
1. Create a dedicated controller `/api/web-call/stream` handling Twilio Media Streams over WebSocket.
2. Ingest mu-law 8kHz audio chunks into a streaming STT pipeline (`GroqWhisperService` or `DeepgramSpeechToTextService`).
3. Return synthesized Rime audio chunks back through Twilio WebSocket.
