# Interview Speech API

Speech là lớp gateway của backend. Frontend không gọi ElevenLabs trực tiếp và không biết provider nào đang được dùng. STT và TTS có thể đổi độc lập qua cấu hình.

## Cấu hình ElevenLabs

Speech tắt mặc định để môi trường chưa có API key vẫn khởi động bình thường.

```env
SPEECH_ENABLED=true
SPEECH_STT_PROVIDER=elevenlabs
SPEECH_TTS_PROVIDER=elevenlabs
ELEVENLABS_API_KEY=<secret>
ELEVENLABS_VOICE_EN=<voice-id>
ELEVENLABS_VOICE_JA=<voice-id>
ELEVENLABS_VOICE_VI=<voice-id>
```

Các giá trị mặc định:

```text
STT model: scribe_v2
TTS model: eleven_flash_v2_5
TTS format: mp3_44100_128
Audio upload limit: 10 MiB
Connect timeout: 5 seconds
Read timeout: 45 seconds
```

Không đưa `ELEVENLABS_API_KEY` vào frontend. Các lỗi provider được chuyển thành error code chung của backend.

## Flow push-to-talk

1. Frontend ghi một câu trả lời bằng `MediaRecorder`.
2. Upload blob qua API transcription.
3. Cho người dùng kiểm tra transcript nếu UI hỗ trợ chỉnh sửa.
4. Gửi transcript qua `POST /api/interview-sessions/{id}/answers` và giữ nguyên `Idempotency-Key` khi retry.
5. Lấy `interviewerTurn.id` trong response.
6. Gọi API audio của turn và phát MP3 trả về.

## Transcribe candidate audio

```http
POST /api/interview-sessions/{sessionId}/speech/transcriptions
Authorization: Bearer <access-token>
Content-Type: multipart/form-data
```

Form field bắt buộc là `audio`. Backend lấy ngôn ngữ từ session và chấp nhận các MIME type `audio/*`, `video/webm`, `video/mp4`.
Session phải đang ở trạng thái `IN_PROGRESS`.

```json
{
  "text": "Tôi đã sử dụng Spring Boot trong hai dự án.",
  "languageCode": "vi"
}
```

Audio ứng viên chỉ tồn tại trong bộ nhớ backend trong lúc request được xử lý; MVP không lưu audio này vào MinIO. Audio vẫn được gửi tới provider và chịu chính sách lưu giữ dữ liệu của gói ElevenLabs đang sử dụng.

## Generate interviewer audio

```http
POST /api/interview-sessions/{sessionId}/speech/turns/{turnId}/audio
Authorization: Bearer <access-token>
Accept: audio/mpeg
```

Backend chỉ tổng hợp turn có role `INTERVIEWER` và thuộc session của user hiện tại. Response là MP3. Audio được cache trong MinIO theo provider, model, voice, ngôn ngữ và nội dung turn để thao tác nghe lại không gọi TTS lần nữa.

## Đổi provider

Core chỉ phụ thuộc hai interface:

```text
SpeechToTextProvider
TextToSpeechProvider
```

Để thêm Azure Speech:

1. Tạo `AzureSpeechToTextProvider` và implement `SpeechToTextProvider`.
2. Tạo `AzureTextToSpeechProvider` và implement `TextToSpeechProvider`.
3. Mỗi adapter trả `name()` là `azure`.
4. Thêm credential, model và voice của Azure dưới `app.speech.providers.azure`.
5. Đổi `SPEECH_STT_PROVIDER` hoặc `SPEECH_TTS_PROVIDER` thành `azure`.

Controller, DTO, frontend và interview conversation service không cần thay đổi. TTS adapter mới phải trả MP3 để giữ nguyên HTTP contract.

## Phạm vi MVP

MVP xử lý một file audio sau khi người dùng dừng ghi âm. Realtime partial transcript và interruption chưa nằm trong phạm vi này. Khi bổ sung WebSocket, backend cần phát event trung lập như `transcript.partial` và `transcript.final`; frontend vẫn không kết nối trực tiếp tới WebSocket của provider.
