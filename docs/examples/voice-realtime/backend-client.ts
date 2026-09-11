import type { RealtimeEvent, RealtimeSessionGrant } from "./types";

export interface DisconnectResult {
  connectionId: number;
  sessionMode: "VOICE_REALTIME" | "VOICE_TURN_BASED";
  disconnectedAt: string;
  fellBackToTurnBased: boolean;
}

export interface InterviewStartResult {
  deadlineAt?: string;
}

export class RealtimeBackendClient {
  constructor(
    private readonly baseUrl: string,
    private readonly getAccessToken: () => string | Promise<string>,
  ) {}

  startInterview(sessionId: number): Promise<InterviewStartResult> {
    return this.request(`/api/interview-sessions/${sessionId}/start`, { method: "POST" });
  }

  async finishInterview(sessionId: number): Promise<void> {
    await this.request(`/api/interview-sessions/${sessionId}/finish`, { method: "POST" });
  }

  createGrant(sessionId: number, voiceName?: string): Promise<RealtimeSessionGrant> {
    return this.request(`/api/interview-sessions/${sessionId}/realtime/session-grants`, {
      method: "POST",
      body: JSON.stringify({ voiceName, clientPlatform: "web" }),
    });
  }

  resumeGrant(
    sessionId: number,
    connectionId: number,
    resumptionHandle: string,
  ): Promise<RealtimeSessionGrant> {
    return this.request(
      `/api/interview-sessions/${sessionId}/realtime/connections/${connectionId}/resume-grants`,
      {
        method: "POST",
        body: JSON.stringify({ resumptionHandle, clientPlatform: "web" }),
      },
    );
  }

  recordEvents(
    sessionId: number,
    connectionId: number,
    events: RealtimeEvent[],
  ): Promise<unknown> {
    return this.request(
      `/api/interview-sessions/${sessionId}/realtime/connections/${connectionId}/events`,
      { method: "POST", body: JSON.stringify({ events }) },
    );
  }

  disconnect(
    sessionId: number,
    connectionId: number,
    body: {
      reason: string;
      fallbackToTurnBased: boolean;
      p50LatencyMs?: number;
      p95LatencyMs?: number;
    },
  ): Promise<DisconnectResult> {
    return this.request(
      `/api/interview-sessions/${sessionId}/realtime/connections/${connectionId}/disconnect`,
      { method: "POST", body: JSON.stringify(body) },
    );
  }

  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const token = await this.getAccessToken();
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      credentials: "include",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        ...init.headers,
      },
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: response.statusText }));
      throw new Error(error.message ?? `Realtime API failed with HTTP ${response.status}`);
    }
    return response.json() as Promise<T>;
  }
}
