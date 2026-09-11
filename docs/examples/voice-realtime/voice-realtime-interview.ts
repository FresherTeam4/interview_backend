import { RealtimeBackendClient } from "./backend-client";
import { GeminiLiveWebSocketClient } from "./gemini-live-websocket";
import { MicrophonePcm16Capture } from "./pcm-audio";
import type {
  RealtimeEvent,
  RealtimeEventType,
  RealtimeSessionGrant,
  RealtimeTransportClient,
  RealtimeTransportHandlers,
} from "./types";

export interface VoiceRealtimeCallbacks {
  onState(state: "CONNECTING" | "LIVE" | "RECONNECTING" | "FALLBACK" | "STOPPED"): void;
  onPartialUserTranscript(text: string): void;
  onTurn(turn: { userTranscript?: string; assistantTranscript?: string; interrupted: boolean }): void;
  onError(error: Error): void;
}

export interface VoiceRealtimeOptions {
  workletUrl: string;
  maxResumeAttempts?: number;
  createTransport?: (grant: RealtimeSessionGrant) => RealtimeTransportClient;
}

export class VoiceRealtimeInterview {
  private readonly microphone = new MicrophonePcm16Capture();
  private transport?: RealtimeTransportClient;
  private sessionId?: number;
  private connectionId?: number;
  private sequence = 0;
  private pendingEvents: RealtimeEvent[] = [];
  private flushPromise?: Promise<void>;
  private resumptionHandle?: string;
  private resumeAttempts = 0;
  private reconnecting = false;
  private stopped = false;
  private sentStartSignal = false;
  private latencySamples: number[] = [];
  private lastPartial = "";
  private lastPartialAt = 0;
  private deadlineTimer?: number;

  constructor(
    private readonly backend: RealtimeBackendClient,
    private readonly callbacks: VoiceRealtimeCallbacks,
    private readonly options: VoiceRealtimeOptions,
  ) {}

  async start(sessionId: number, voiceName?: string): Promise<void> {
    this.stopped = false;
    this.sessionId = sessionId;
    this.callbacks.onState("CONNECTING");
    const conversation = await this.backend.startInterview(sessionId);
    this.scheduleDeadline(conversation.deadlineAt);
    const grant = await this.backend.createGrant(sessionId, voiceName);
    await this.connect(grant);
  }

  async stop(): Promise<void> {
    if (this.stopped) return;
    this.stopped = true;
    if (this.deadlineTimer !== undefined) window.clearTimeout(this.deadlineTimer);
    this.transport?.endAudioStream();
    await this.microphone.stop();
    this.enqueue("SESSION_DISCONNECTED", undefined, "CLIENT_STOPPED");
    await this.flush();
    if (this.sessionId !== undefined && this.connectionId !== undefined) {
      await this.backend.disconnect(
        this.sessionId,
        this.connectionId,
        this.disconnectPayload("CLIENT_STOPPED", false),
      );
    }
    this.transport?.close();
    this.callbacks.onState("STOPPED");
  }

  async finish(): Promise<void> {
    const sessionId = this.sessionId;
    if (sessionId === undefined) return;
    await this.stop();
    await this.backend.finishInterview(sessionId);
  }

  private async connect(grant: RealtimeSessionGrant): Promise<void> {
    this.connectionId = grant.connectionId;
    this.sequence = 0;
    this.pendingEvents = [];
    this.transport ??= (this.options.createTransport ?? createTransport)(grant);
    await this.transport.connect(grant, this.handlers());
  }

  private handlers(): RealtimeTransportHandlers {
    return {
      onReady: () => void this.onReady(),
      onPartialUserTranscript: (text) => this.onPartial(text),
      onFinalTurn: (turn) => {
        if (turn.userTranscript) {
          this.enqueue("USER_TRANSCRIPT_FINAL", turn.userTranscript);
        }
        if (turn.assistantTranscript) {
          this.enqueue(
            "ASSISTANT_TRANSCRIPT_FINAL",
            turn.assistantTranscript,
            undefined,
            turn.latencyMs,
          );
        }
        if (turn.interrupted) this.enqueue("ASSISTANT_INTERRUPTED");
        if (turn.latencyMs !== undefined) this.latencySamples.push(turn.latencyMs);
        this.callbacks.onTurn(turn);
        void this.flush();
      },
      onResumptionHandle: (handle) => {
        this.resumptionHandle = handle;
        this.enqueue("SESSION_RESUMPTION_UPDATED", undefined, handle);
        void this.flush();
      },
      onReconnectRequested: () => void this.reconnect("PROVIDER_GO_AWAY"),
      onClose: (reason) => void this.reconnect(reason),
      onError: (error) => {
        this.enqueue("PROVIDER_ERROR", undefined, error.message.slice(0, 65535));
        void this.flush();
        this.callbacks.onError(error);
      },
    };
  }

  private async onReady(): Promise<void> {
    this.resumeAttempts = 0;
    this.reconnecting = false;
    this.enqueue("SESSION_CONNECTED");
    await this.flush();
    if (!this.sentStartSignal) {
      this.transport!.sendText("START_INTERVIEW");
      this.sentStartSignal = true;
    }
    await this.microphone.start(this.options.workletUrl, (chunk) => {
      this.transport?.sendAudio(chunk);
    });
    this.callbacks.onState("LIVE");
  }

  private onPartial(text: string): void {
    this.callbacks.onPartialUserTranscript(text);
    const now = performance.now();
    if (text === this.lastPartial || now - this.lastPartialAt < 250) return;
    this.lastPartial = text;
    this.lastPartialAt = now;
    this.enqueue("USER_TRANSCRIPT_PARTIAL", text);
  }

  private async reconnect(reason: string): Promise<void> {
    if (this.stopped || this.reconnecting) return;
    this.reconnecting = true;
    this.callbacks.onState("RECONNECTING");
    await this.microphone.stop();
    try {
      await this.flush();
      const maxAttempts = this.options.maxResumeAttempts ?? 2;
      while (this.resumeAttempts < maxAttempts && this.resumptionHandle) {
        this.resumeAttempts++;
        await delay(250 * this.resumeAttempts);
        try {
          const grant = await this.backend.resumeGrant(
            this.sessionId!, this.connectionId!, this.resumptionHandle,
          );
          await this.connect(grant);
          return;
        } catch (error) {
          this.callbacks.onError(asError(error));
        }
      }
      await this.fallback(reason);
    } catch (error) {
      this.callbacks.onError(asError(error));
      await this.fallback(reason);
    } finally {
      this.reconnecting = false;
    }
  }

  private async fallback(reason: string): Promise<void> {
    this.stopped = true;
    if (this.sessionId !== undefined && this.connectionId !== undefined) {
      await this.backend.disconnect(
        this.sessionId,
        this.connectionId,
        this.disconnectPayload(reason.slice(0, 255), true),
      );
    }
    this.transport?.close();
    this.callbacks.onState("FALLBACK");
  }

  private enqueue(
    eventType: RealtimeEventType,
    transcriptText?: string,
    detail?: string,
    latencyMs?: number,
  ): void {
    this.pendingEvents.push({
      providerEventId: crypto.randomUUID(),
      sequenceNumber: this.sequence++,
      eventType,
      transcriptText,
      detail,
      latencyMs,
      occurredAt: new Date().toISOString(),
    });
  }

  private async flush(): Promise<void> {
    if (this.flushPromise) return this.flushPromise;
    this.flushPromise = this.flushBatches().finally(() => {
      this.flushPromise = undefined;
    });
    return this.flushPromise;
  }

  private async flushBatches(): Promise<void> {
    while (this.pendingEvents.length > 0) {
      const batch = this.pendingEvents.slice(0, 100);
      await this.backend.recordEvents(this.sessionId!, this.connectionId!, batch);
      this.pendingEvents.splice(0, batch.length);
    }
  }

  private disconnectPayload(reason: string, fallbackToTurnBased: boolean) {
    const sorted = [...this.latencySamples].sort((a, b) => a - b);
    return {
      reason,
      fallbackToTurnBased,
      p50LatencyMs: percentile(sorted, 0.5),
      p95LatencyMs: percentile(sorted, 0.95),
    };
  }

  private scheduleDeadline(deadlineAt?: string): void {
    if (!deadlineAt) return;
    const delayMs = Math.max(0, Date.parse(deadlineAt) - Date.now());
    this.deadlineTimer = window.setTimeout(() => {
      this.deadlineTimer = undefined;
      void this.finish()
        .catch((error) => this.callbacks.onError(asError(error)));
    }, Math.min(delayMs, 2_147_483_647));
  }
}

function createTransport(grant: RealtimeSessionGrant): RealtimeTransportClient {
  if (grant.provider === "gemini-live" && grant.transport === "WEBSOCKET") {
    return new GeminiLiveWebSocketClient();
  }
  throw new Error(`No browser adapter for ${grant.provider}/${grant.transport}`);
}

function percentile(sorted: number[], ratio: number): number | undefined {
  if (sorted.length === 0) return undefined;
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)];
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function asError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error));
}
