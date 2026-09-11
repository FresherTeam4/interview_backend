export class Pcm16Player {
  private readonly context: AudioContext;
  private readonly active = new Set<AudioBufferSourceNode>();
  private nextStartAt = 0;

  constructor(private readonly sourceSampleRate: number) {
    this.context = new AudioContext();
  }

  async resume(): Promise<void> {
    await this.context.resume();
  }

  enqueue(base64Pcm: string): void {
    const bytes = base64ToBytes(base64Pcm);
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    const samples = Math.floor(bytes.byteLength / 2);
    const buffer = this.context.createBuffer(1, samples, this.sourceSampleRate);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < samples; index++) {
      channel[index] = view.getInt16(index * 2, true) / 32768;
    }

    const source = this.context.createBufferSource();
    source.buffer = buffer;
    source.connect(this.context.destination);
    const startAt = Math.max(this.context.currentTime, this.nextStartAt);
    this.nextStartAt = startAt + buffer.duration;
    this.active.add(source);
    source.onended = () => this.active.delete(source);
    source.start(startAt);
  }

  interrupt(): void {
    for (const source of this.active) {
      try {
        source.stop();
      } catch {
        // Source may already have ended between iteration and stop.
      }
    }
    this.active.clear();
    this.nextStartAt = this.context.currentTime;
  }

  async close(): Promise<void> {
    this.interrupt();
    await this.context.close();
  }
}

export class MicrophonePcm16Capture {
  private context?: AudioContext;
  private stream?: MediaStream;

  async start(workletUrl: string, onChunk: (chunk: ArrayBuffer) => void): Promise<void> {
    if (this.context) return;
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
    this.context = new AudioContext();
    await this.context.audioWorklet.addModule(workletUrl);
    const source = this.context.createMediaStreamSource(this.stream);
    const capture = new AudioWorkletNode(this.context, "pcm16-capture", {
      processorOptions: { targetSampleRate: 16000, chunkSamples: 320 },
    });
    const silent = this.context.createGain();
    silent.gain.value = 0;
    capture.port.onmessage = (event: MessageEvent<ArrayBuffer>) => onChunk(event.data);
    source.connect(capture).connect(silent).connect(this.context.destination);
    await this.context.resume();
  }

  async stop(): Promise<void> {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = undefined;
    await this.context?.close();
    this.context = undefined;
  }
}

export function bytesToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
