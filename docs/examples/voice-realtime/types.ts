export type RealtimeEventType =
  | "SESSION_CONNECTED"
  | "SESSION_RESUMPTION_UPDATED"
  | "USER_SPEECH_STARTED"
  | "USER_TRANSCRIPT_PARTIAL"
  | "USER_TRANSCRIPT_FINAL"
  | "ASSISTANT_SPEECH_STARTED"
  | "ASSISTANT_TRANSCRIPT_FINAL"
  | "ASSISTANT_INTERRUPTED"
  | "SESSION_DISCONNECTED"
  | "PROVIDER_ERROR";

export interface AudioFormat {
  mimeType: string;
  sampleRate: number;
  bitDepth: number;
  channels: number;
}

export interface RealtimeSessionGrant {
  connectionId: number;
  provider: string;
  transport: "WEBSOCKET" | "WEBRTC";
  endpoint: string;
  accessToken: string;
  modelName: string;
  voiceName: string;
  inputAudio: AudioFormat;
  outputAudio: AudioFormat;
  expiresAt: string;
  sessionSetup: Record<string, unknown>;
}

export interface RealtimeEvent {
  providerEventId: string;
  sequenceNumber: number;
  eventType: RealtimeEventType;
  transcriptText?: string;
  detail?: string;
  latencyMs?: number;
  occurredAt: string;
}

export interface RealtimeTransportHandlers {
  onReady(): void;
  onPartialUserTranscript(text: string): void;
  onFinalTurn(turn: {
    userTranscript?: string;
    assistantTranscript?: string;
    interrupted: boolean;
    latencyMs?: number;
  }): void;
  onResumptionHandle(handle: string): void;
  onReconnectRequested(): void;
  onClose(reason: string): void;
  onError(error: Error): void;
}

export interface RealtimeTransportClient {
  connect(grant: RealtimeSessionGrant, handlers: RealtimeTransportHandlers): Promise<void>;
  sendAudio(pcm16: ArrayBuffer): void;
  sendText(text: string): void;
  endAudioStream(): void;
  close(): void;
}
