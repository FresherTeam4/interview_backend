# Realtime Voice API

Backend dùng provider SPI và hiện có adapter `gemini-live`. API key chỉ được dùng trên backend để tạo ephemeral token; frontend không nhận API key dài hạn hoặc system instruction nội bộ.

## Cấu hình develop

```env
# Tùy chọn: đặt false khi cần tắt chức năng realtime
REALTIME_ENABLED=true
REALTIME_PROVIDER=gemini-live
GEMINI_API_KEY=<google-ai-studio-key>
GEMINI_LIVE_MODEL=gemini-3.1-flash-live-preview
GEMINI_LIVE_DEFAULT_VOICE=Kore
```

Các giá trị mặc định khác nằm dưới `app.realtime.providers.gemini-live` trong `application.yaml`. Backend mặc định chấp nhận 30 prebuilt voice hiện được Gemini hỗ trợ. Có thể giới hạn lại bằng `GEMINI_LIVE_SUPPORTED_VOICES`.

Realtime được bật mặc định trong môi trường develop. Khi `REALTIME_ENABLED=false`, option `VOICE_REALTIME` bị ẩn, API tạo session từ chối mode này và API cấp grant trả `503 REALTIME_NOT_ENABLED`.

## Tạo session realtime

Khi tạo interview session, gửi thêm mode:

```json
{
  "templateId": 101,
  "profileId": 35,
  "languageCode": "vi",
  "durationMinutes": 30,
  "interviewerStyle": "PROFESSIONAL",
  "mode": "VOICE_REALTIME"
}
```

Request cũ không có `mode` tiếp tục dùng `VOICE_TURN_BASED`.

## Cấp Gemini Live session grant

Session phải thuộc user, có mode `VOICE_REALTIME` và đang ở `READY` hoặc `IN_PROGRESS`.

```http
POST /api/interview-sessions/{sessionId}/realtime/session-grants
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "voiceName": "Kore",
  "clientPlatform": "web"
}
```

`voiceName` có thể bỏ trống để dùng `GEMINI_LIVE_DEFAULT_VOICE`.

```json
{
  "connectionId": 91,
  "provider": "gemini-live",
  "transport": "WEBSOCKET",
  "endpoint": "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained",
  "accessToken": "short-lived-token",
  "modelName": "gemini-3.1-flash-live-preview",
  "voiceName": "Kore",
  "inputAudio": {
    "mimeType": "audio/pcm;rate=16000",
    "sampleRate": 16000,
    "bitDepth": 16,
    "channels": 1
  },
  "outputAudio": {
    "mimeType": "audio/pcm;rate=24000",
    "sampleRate": 24000,
    "bitDepth": 16,
    "channels": 1
  },
  "expiresAt": "2026-09-10T08:45:00Z",
  "sessionSetup": {
    "model": "models/gemini-3.1-flash-live-preview"
  }
}
```

Frontend nối `access_token` đã URL-encode vào query của `endpoint`, sau đó gửi `{ "setup": sessionSetup }` làm WebSocket message đầu tiên và chờ `setupComplete` trước khi stream audio.

Ephemeral token bị khóa theo model, voice, audio output, input/output transcription, context compression, session resumption và system instruction đã dựng từ interview plan. `sessionSetup` trả cho client không chứa system instruction hoặc candidate context.

## Đồng bộ event và transcript

Client gửi batch tối đa 100 event. `providerEventId` phải ổn định khi retry; `sequenceNumber` tăng từ 0 trong phạm vi một `connectionId`.

```http
POST /api/interview-sessions/{sessionId}/realtime/connections/{connectionId}/events
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "events": [
    {
      "providerEventId": "a0ae6510-4e10-4c33-bcd7-16c97427e3de",
      "sequenceNumber": 0,
      "eventType": "SESSION_CONNECTED",
      "occurredAt": "2026-09-10T08:01:00Z"
    },
    {
      "providerEventId": "eb4638df-f470-48c1-80e2-b6326d954758",
      "sequenceNumber": 1,
      "eventType": "USER_TRANSCRIPT_FINAL",
      "transcriptText": "Tôi đã dùng Spring Boot trong dự án gần nhất.",
      "occurredAt": "2026-09-10T08:01:08Z"
    },
    {
      "providerEventId": "641d27b1-b84a-4657-898f-19d32d490ac8",
      "sequenceNumber": 2,
      "eventType": "ASSISTANT_TRANSCRIPT_FINAL",
      "transcriptText": "Bạn có thể mô tả cách xử lý transaction không?",
      "latencyMs": 284,
      "occurredAt": "2026-09-10T08:01:09Z"
    }
  ]
}
```

Final transcript tạo `InterviewTurn` với `inputMode=VOICE_REALTIME`. Opening transcript đầu tiên của Gemini không tạo turn trùng vì `/start` đã lưu opening ở index 0. `ASSISTANT_INTERRUPTED` đánh dấu `wasInterrupted=true` trên lượt interviewer gần nhất. Partial transcript được lưu làm event để quan sát nhưng không tạo turn.

Response cho biết số event mới, event retry và turn đã tạo:

```json
{
  "connectionId": 91,
  "acceptedEventCount": 3,
  "duplicateEventCount": 0,
  "createdTurnCount": 2,
  "currentTurnIndex": 2
}
```

Các event có cùng `providerEventId` được xử lý idempotent. Dùng lại một sequence cho event ID khác trả `409 REALTIME_EVENT_SEQUENCE_CONFLICT`. Final transcript rỗng hoặc resumption event thiếu handle trả `400 REALTIME_EVENT_INVALID`.

## Resume WebSocket

Gemini định kỳ trả `sessionResumptionUpdate.newHandle`. Client nên gửi ngay event `SESSION_RESUMPTION_UPDATED` với handle trong field `detail`. Khi WebSocket đóng hoặc nhận `goAway`, dùng handle mới nhất:

```http
POST /api/interview-sessions/{sessionId}/realtime/connections/{connectionId}/resume-grants
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "resumptionHandle": "<latest-handle>",
  "clientPlatform": "web"
}
```

Response có cùng schema với session grant ban đầu nhưng dùng `connectionId` mới. Sequence event của connection mới bắt đầu lại từ 0. Backend đánh dấu connection cũ đã được resume và ràng buộc ephemeral token mới với resumption handle.

Thời hạn token resume được tính theo `deadlineAt` còn lại, không cấp lại toàn bộ thời lượng ban đầu. Browser client mẫu cũng đóng media và gọi `/finish` khi deadline đến để tránh kéo dài thời gian sử dụng provider.

## Disconnect và fallback

Khi user dừng hoặc resume thất bại, đóng connection và gửi latency:

```http
POST /api/interview-sessions/{sessionId}/realtime/connections/{connectionId}/disconnect
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "reason": "resume failed after 2 attempts",
  "fallbackToTurnBased": true,
  "p50LatencyMs": 260,
  "p95LatencyMs": 640
}
```

Nếu `fallbackToTurnBased=true`, backend đổi session mode thành `VOICE_TURN_BASED`. Nếu WebSocket rớt sau khi candidate transcript đã được lưu nhưng trước assistant transcript, backend dùng conversation engine hiện có để tạo interviewer turn kế tiếp. Nếu engine tạm lỗi, backend tạo một lượt yêu cầu ứng viên nhắc lại để hội thoại không mắc kẹt ở candidate turn. Frontend sau đó refetch conversation và dùng speech/answer API. `p50LatencyMs` không được lớn hơn `p95LatencyMs`.

## Client trình duyệt mẫu

Module tại [examples/voice-realtime](./examples/voice-realtime/README.md) gồm AudioWorklet capture PCM16 16 kHz, player PCM16 24 kHz, Gemini WebSocket adapter, transcript/event sync, barge-in, resume khi `goAway` và fallback sau số lần retry cấu hình.

UI orchestration chỉ phụ thuộc interface `RealtimeTransportClient`. Muốn đổi sang Deepgram hoặc provider WebRTC, thêm adapter theo `grant.provider`/`grant.transport`; backend lifecycle và UI callbacks giữ nguyên.

