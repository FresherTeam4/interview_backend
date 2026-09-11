class Pcm16CaptureProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    this.targetSampleRate = options.processorOptions?.targetSampleRate ?? 16000;
    this.chunkSamples = options.processorOptions?.chunkSamples ?? 320;
    this.phase = 0;
    this.pending = [];
  }

  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input) return true;
    for (let index = 0; index < input.length; index++) {
      this.phase += this.targetSampleRate;
      if (this.phase < sampleRate) continue;
      this.phase -= sampleRate;
      const sample = Math.max(-1, Math.min(1, input[index]));
      this.pending.push(sample < 0 ? sample * 32768 : sample * 32767);
      if (this.pending.length === this.chunkSamples) this.flush();
    }
    return true;
  }

  flush() {
    const pcm = new Int16Array(this.pending);
    this.pending = [];
    this.port.postMessage(pcm.buffer, [pcm.buffer]);
  }
}

registerProcessor("pcm16-capture", Pcm16CaptureProcessor);
