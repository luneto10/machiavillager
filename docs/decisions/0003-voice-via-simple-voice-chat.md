# 0003. Voice through Simple Voice Chat

Date: 2026-10-18 · Status: proposed (confirm in Checkpoint 11)

## Context

We want push-to-talk to the NPC and positional NPC speech without reimplementing audio capture,
Opus encoding, proximity, and UDP voice transport.

Verified 2026-10-18: SVC 2.6.24 publishes builds for 26.3 (Fabric, NeoForge, Forge, Quilt,
Paper). Addon API `de.maxhenkel.voicechat:voicechat-api:2.6.24` on `https://maven.maxhenkel.de/repository/public`.

## Proposed decision

Build an SVC server-side addon: receive the player's microphone packets, stream them to the
backend for Azure STT, and play backend TTS audio from an entity-bound audio channel at the NPC.

## Open questions for Checkpoint 11

- TTS provider: NVIDIA-hosted Chatterbox (only if genuinely available to our account and terms allow)
  vs Azure Neural TTS. Compare latency, PT-BR/EN quality, streaming, cost.
- Whether audio should go Java → backend directly or via a separate stream.
- Don't store raw recordings.
