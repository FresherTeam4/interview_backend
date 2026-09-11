# Browser client mẫu cho VOICE_REALTIME

Các file trong thư mục này là module TypeScript độc lập để chuyển vào frontend thực tế. Repo hiện chưa chứa frontend/package manager nên module không được bundle cùng Spring Boot.

Luồng sử dụng:

```ts
import { RealtimeBackendClient } from "./voice-realtime/backend-client";
import { VoiceRealtimeInterview } from "./voice-realtime/voice-realtime-interview";

const backend = new RealtimeBackendClient("http://localhost:8080", () => accessToken);
const interview = new VoiceRealtimeInterview(
  backend,
  {
    onState: (state) => setConnectionState(state),
    onPartialUserTranscript: (text) => setDraftTranscript(text),
    onTurn: (turn) => appendTurn(turn),
    onError: (error) => showError(error.message),
  },
  {
    // Copy pcm16-capture-worklet.js vào public assets của frontend.
    workletUrl: "/pcm16-capture-worklet.js",
    maxResumeAttempts: 2,
  },
);

// Phải gọi từ click/tap để browser cho phép microphone và AudioContext.
await interview.start(sessionId, "Kore");

// Khi user bấm kết thúc: đóng media và chuyển session sang scoring.
await interview.finish();

// Khi chỉ unmount/reload và vẫn muốn giữ session IN_PROGRESS.
await interview.stop();
```

Client thực hiện sẵn các việc sau:

- Gọi `/start`, lấy ephemeral grant rồi kết nối thẳng tới provider; API key dài hạn không đi qua browser.
- Capture mono PCM16, resample 16 kHz và gửi chunk 20 ms.
- Phát PCM16 24 kHz; khi Gemini trả `interrupted=true`, dừng ngay các source đang phát và xóa playback queue.
- Đồng bộ partial/final transcript theo batch tối đa 100 event. Mỗi event có UUID và sequence để backend xử lý retry idempotent.
- Ghi resumption handle, thử resume hai lần khi WebSocket đóng hoặc nhận `goAway`, sau đó gọi fallback sang `VOICE_TURN_BASED`.
- Thu thập latency từng turn và gửi p50/p95 khi disconnect.
- Dùng `deadlineAt` từ `/start` để đóng media và gọi `/finish` đúng thời gian, kể cả sau khi resume.

`VoiceRealtimeOptions.createTransport` là điểm thay adapter. Khi thêm Deepgram hoặc provider WebRTC, implement `RealtimeTransportClient` rồi chọn adapter dựa vào `grant.provider` và `grant.transport`; phần microphone, event sync, resume/fallback và UI callback không cần đổi.

Microphone chỉ hoạt động trên HTTPS hoặc `localhost`. Frontend cần gọi `stop()` trước khi rời màn hình để đóng track và connection sạch sẽ.
