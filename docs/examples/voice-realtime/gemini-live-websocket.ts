import { bytesToBase64, Pcm16Player } from "./pcm-audio";
import type {
  RealtimeSessionGrant,
  RealtimeTransportClient,
  RealtimeTransportHandlers,
} from "./types";

interface GeminiServerMessage {
  setupComplete?: Record<string, never>;
  sessionResumptionUpdate?: { newHandle?: string; resumable?: boolean };
  goAway?: { timeLeft?: string };
  serverContent?: {
    modelTurn?: { parts?: Array<{ inlineData?: { data?: string; mimeType?: string } }> };
    interimInputTranscription?: { text?: string };
    inputTranscription?: { text?: string };
    outputTranscription?: { text?: string };
    interrupted?: boolean;
    turnComplete?: boolean;
  };
}

export class GeminiLiveWebSocketClient implements RealtimeTransportClient {
  private socket?: WebSocket;
  private player?: Pcm16Player;
  private handlers?: RealtimeTransportHandlers;
  private userTranscript = "";
  private assistantTranscript = "";
  private interrupted = false;
  private userTranscriptAt?: number;
  private firstAudioLatencyMs?: number;
  private replacingSocket = false;
  private closing = false;

  async connect(
    grant: RealtimeSessionGrant,
    handlers: RealtimeTransportHandlers,
  ): Promise<void> {
    if (grant.transport !== "WEBSOCKET") {
      throw new Error(`Gemini adapter cannot use ${grant.transport}`);
    }
    this.handlers = handlers;
    this.closing = false;
    this.resetTurn();
    if (!this.player) this.player = new Pcm16Player(grant.outputAudio.sampleRate);
    await this.player.resume();

    if (this.socket && this.socket.readyState < WebSocket.CLOSING) {
      this.replacingSocket = true;
      this.socket.close(1000, "replaced by resumed connection");
    }
    const endpoint = new URL(grant.endpoint);
    endpoint.searchParams.set("access_token", grant.accessToken);
    const socket = new WebSocket(endpoint);
    this.socket = socket;

    await new Promise<void>((resolve, reject) => {
      socket.onopen = () => {
        socket.send(JSON.stringify({ setup: grant.sessionSetup }));
        resolve();
      };
      socket.onerror = () => reject(new Error("Could not open Gemini Live WebSocket"));
    });
    socket.onmessage = (event) => void this.receive(event.data);
    socket.onerror = () => handlers.onError(new Error("Gemini Live WebSocket failed"));
    socket.onclose = (event) => {
      if (this.replacingSocket) {
        this.replacingSocket = false;
        return;
      }
      if (!this.closing) handlers.onClose(event.reason || `WebSocket closed (${event.code})`);
    };
  }

  sendAudio(pcm16: ArrayBuffer): void {
    if (!this.canSend() || this.socket!.bufferedAmount > 1_048_576) return;
    this.send({
      realtimeInput: {
        audio: { data: bytesToBase64(pcm16), mimeType: "audio/pcm;rate=16000" },
      },
    });
  }

  sendText(text: string): void {
    if (this.canSend()) this.send({ realtimeInput: { text } });
  }

  endAudioStream(): void {
    if (this.canSend()) this.send({ realtimeInput: { audioStreamEnd: true } });
  }

  close(): void {
    this.closing = true;
    this.socket?.close(1000, "client stopped");
    void this.player?.close();
    this.socket = undefined;
    this.player = undefined;
  }

  private async receive(data: string | Blob | ArrayBuffer): Promise<void> {
    const raw = typeof data === "string"
      ? data
      : data instanceof Blob
        ? await data.text()
        : new TextDecoder().decode(data);
    let message: GeminiServerMessage;
    try {
      message = JSON.parse(raw) as GeminiServerMessage;
    } catch {
      this.handlers?.onError(new Error("Gemini Live returned invalid JSON"));
      return;
    }

    if ("setupComplete" in message) this.handlers?.onReady();
    const handle = message.sessionResumptionUpdate?.newHandle;
    if (handle) this.handlers?.onResumptionHandle(handle);
    if (message.goAway) this.handlers?.onReconnectRequested();

    const content = message.serverContent;
    if (!content) return;
    const partial = content.interimInputTranscription?.text?.trim();
    if (partial) this.handlers?.onPartialUserTranscript(partial);
    const input = content.inputTranscription?.text;
    if (input) {
      this.userTranscript = appendTranscript(this.userTranscript, input);
      this.userTranscriptAt ??= performance.now();
    }
    const output = content.outputTranscription?.text;
    if (output) this.assistantTranscript = appendTranscript(this.assistantTranscript, output);
    for (const part of content.modelTurn?.parts ?? []) {
      const audio = part.inlineData?.data;
      if (!audio) continue;
      if (this.userTranscriptAt !== undefined && this.firstAudioLatencyMs === undefined) {
        this.firstAudioLatencyMs = Math.round(performance.now() - this.userTranscriptAt);
      }
      this.player?.enqueue(audio);
    }
    if (content.interrupted) {
      this.interrupted = true;
      this.player?.interrupt();
    }
    if (content.turnComplete) {
      this.handlers?.onFinalTurn({
        userTranscript: optional(this.userTranscript),
        assistantTranscript: optional(this.assistantTranscript),
        interrupted: this.interrupted,
        latencyMs: this.firstAudioLatencyMs,
      });
      this.resetTurn();
    }
  }

  private canSend(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }

  private send(value: unknown): void {
    this.socket!.send(JSON.stringify(value));
  }

  private resetTurn(): void {
    this.userTranscript = "";
    this.assistantTranscript = "";
    this.interrupted = false;
    this.userTranscriptAt = undefined;
    this.firstAudioLatencyMs = undefined;
  }
}

function appendTranscript(current: string, nextValue: string): string {
  const next = nextValue.trim();
  if (!current) return next;
  if (next.startsWith(current)) return next;
  if (current.endsWith(next)) return current;
  return `${current} ${next}`.replace(/\s+/g, " ").trim();
}

function optional(value: string): string | undefined {
  const normalized = value.trim();
  return normalized || undefined;
}
