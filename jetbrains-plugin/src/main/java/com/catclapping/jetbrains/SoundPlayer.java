package com.catclapping.jetbrains;

import com.intellij.openapi.diagnostic.Logger;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Plays the bundled reaction WAV sounds across macOS, Linux, and Windows.
 * <p>
 * Use {@link #prepare(CatReaction, long, long, int)} off the EDT to open the audio line in advance, then {@link Playback#start()}
 * at the exact moment the animation starts. {@link Playback#positionMs()} reports how much audio has
 * actually been played so the animation can follow the sound.
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getInstance(SoundPlayer.class);
    /** Rough start-up latency of external players such as afplay. */
    private static final long OS_PLAYER_LATENCY_MS = 80L;
    private static final Map<String, Path> CACHED_TEMP_AUDIO_FILES = new ConcurrentHashMap<>();

    private SoundPlayer() {
    }

    /** Fire-and-forget playback for explicit reaction, clip window, sound toggle, and volume. */
    public static void play(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs,
            boolean soundEnabled,
            int volumePercent) {
        Objects.requireNonNull(reaction, "reaction");
        if (!soundEnabled || volumePercent <= 0) {
            return;
        }
        Thread thread = new Thread(() -> {
            Playback playback = prepare(reaction, clipStartMs, clipEndMs, volumePercent);
            if (playback != null) {
                playback.start();
            }
        }, "cat-clapping-audio-" + reaction.name().toLowerCase(Locale.ROOT));
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Opens the audio line for the given reaction, clip window, and volume (0..100). May block; call off the EDT.
     */
    public static Playback prepare(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs,
            int volumePercent) {
        Objects.requireNonNull(reaction, "reaction");
        int clamped = Math.max(0, Math.min(100, volumePercent));
        double volume = clamped / 100.0;
        if (volume <= 0.0) {
            return null;
        }
        long startMs = Math.max(0L, clipStartMs);
        long endMs = clipEndMs > startMs ? clipEndMs : -1L;
        Clip clip = openClip(reaction, startMs, endMs, volume);
        return new Playback(reaction, startMs, endMs, clip, volume);
    }

    public static final class Playback {
        private final CatReaction reaction;
        private final long clipStartMs;
        private final long clipEndMs;
        private final Clip clip;
        private final double volume;
        private volatile long startNanos = -1L;
        private volatile boolean finished;
        private volatile long lastClipMs;
        private volatile Process activeProcess;

        private Playback(CatReaction reaction, long clipStartMs, long clipEndMs, Clip clip, double volume) {
            this.reaction = reaction;
            this.clipStartMs = clipStartMs;
            this.clipEndMs = clipEndMs;
            this.clip = clip;
            this.volume = volume;
        }

        /** Starts playback asynchronously. */
        public void start() {
            if (startNanos >= 0) {
                return;
            }
            startNanos = System.nanoTime();
            Thread thread = new Thread(
                    clip != null
                            ? this::runClip
                            : () -> playViaOsFallback(reaction, clipStartMs, clipEndMs, volume, this),
                    "cat-clapping-audio-" + reaction.name().toLowerCase(Locale.ROOT));
            thread.setDaemon(true);
            thread.start();
        }

        /** Milliseconds of audio played so far, or -1 if not started. */
        public long positionMs() {
            if (startNanos < 0) {
                return -1L;
            }
            long wallMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (clip == null) {
                return Math.max(wallMs - OS_PLAYER_LATENCY_MS, 0L);
            }
            if (finished) {
                return Math.max(wallMs, lastClipMs);
            }
            long clipMs = clip.getMicrosecondPosition() / 1000L;
            if (clipMs > 0) {
                lastClipMs = clipMs;
                return clipMs;
            }
            // Safety net if the audio device never reports progress.
            return Math.max(wallMs - 500L, 0L);
        }

        /** Stops playback (whether started or only prepared) and releases resources. */
        public void stop() {
            finished = true;
            if (clip != null) {
                clip.stop();
                clip.close();
            }
            Process process = activeProcess;
            if (process != null) {
                process.destroy();
            }
        }

        private void runClip() {
            try {
                CountDownLatch finishedLatch = new CountDownLatch(1);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                        finishedLatch.countDown();
                    }
                });
                // Clip.start() is asynchronous and isRunning() is still false right after it,
                // so wait for the STOP event (bounded by the clip length) before closing.
                long lengthMs = Math.max(clip.getMicrosecondLength() / 1000L, 0L);
                clip.start();
                finishedLatch.await(lengthMs + 2000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finished = true;
                clip.close();
            }
        }
    }

    private static Clip openClip(CatReaction reaction, long clipStartMs, long clipEndMs, double volume) {
        String audioResource = reaction.audioResource();
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(audioResource)) {
            if (raw == null) {
                LOG.warn("Audio resource not found: " + audioResource);
                return null;
            }
            try (BufferedInputStream buffered = new BufferedInputStream(raw);
                 AudioInputStream rawStream = AudioSystem.getAudioInputStream(buffered);
                 AudioInputStream audioStream = sliceAudioStream(rawStream, clipStartMs, clipEndMs)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                applyVolume(clip, volume);
                return clip;
            }
        } catch (Exception e) {
            LOG.debug("Java Sound unavailable, using OS fallback", e);
            return null;
        }
    }

    private static AudioInputStream sliceAudioStream(
            AudioInputStream source,
            long clipStartMs,
            long clipEndMs) throws IOException {
        if (clipStartMs <= 0L && clipEndMs <= 0L) {
            return source;
        }
        AudioFormat format = source.getFormat();
        float sampleRate = format.getSampleRate();
        int frameSize = format.getFrameSize();
        if (sampleRate <= 0f || frameSize <= 0) {
            return source;
        }
        long totalFrames = source.getFrameLength();
        long startFrame = Math.max(0L, Math.round((clipStartMs / 1000.0) * sampleRate));
        if (totalFrames >= 0) {
            startFrame = Math.min(startFrame, totalFrames);
        }
        long bytesToSkip = startFrame * frameSize;
        while (bytesToSkip > 0) {
            long skipped = source.skip(bytesToSkip);
            if (skipped <= 0) {
                if (source.read() < 0) {
                    break;
                }
                bytesToSkip--;
            } else {
                bytesToSkip -= skipped;
            }
        }
        long remainingFrames = totalFrames >= 0
                ? Math.max(0L, totalFrames - startFrame)
                : AudioSystem.NOT_SPECIFIED;
        if (clipEndMs > clipStartMs) {
            long requestedFrames = Math.max(0L, Math.round(((clipEndMs - clipStartMs) / 1000.0) * sampleRate));
            remainingFrames = remainingFrames >= 0
                    ? Math.min(remainingFrames, requestedFrames)
                    : requestedFrames;
        }
        return new AudioInputStream(source, format, remainingFrames);
    }

    private static void applyVolume(Clip clip, double volume) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = (float) (20.0 * Math.log10(Math.max(volume, 0.0001)));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            } else if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
                float value = vol.getMinimum() + (float) volume * (vol.getMaximum() - vol.getMinimum());
                vol.setValue(value);
            }
        } catch (Exception e) {
            LOG.debug("Could not apply volume", e);
        }
    }

    private static void playViaOsFallback(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs,
            double volume,
            Playback playback) {
        try {
            Path audioFile = ensureTempAudioFile(reaction, clipStartMs, clipEndMs);
            if (audioFile == null || playback.finished) {
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String path = audioFile.toAbsolutePath().toString();
            if (os.contains("mac")) {
                runPlayer(List.of("afplay", "-v", String.format(Locale.ROOT, "%.2f", volume), path), playback);
            } else if (os.contains("win")) {
                String escaped = path.replace("'", "''");
                String cmd = "$player = New-Object System.Media.SoundPlayer; "
                        + "$player.SoundLocation = '" + escaped + "'; "
                        + "$player.Load(); $player.PlaySync();";
                runPlayer(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", cmd), playback);
            } else {
                int paVolume = (int) Math.round(volume * 65536);
                if (!runPlayer(List.of("paplay", "--volume=" + paVolume, path), playback) && !playback.finished) {
                    runPlayer(List.of("aplay", path), playback);
                }
            }
        } catch (Exception e) {
            LOG.warn("OS fallback audio playback failed", e);
        }
    }

    private static boolean runPlayer(List<String> command, Playback playback) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            playback.activeProcess = process;
            if (playback.finished) {
                process.destroy();
                return false;
            }
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } finally {
            playback.activeProcess = null;
        }
    }

    static synchronized Path ensureTempAudioFile(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs) throws IOException {
        long startMs = Math.max(0L, clipStartMs);
        long endMs = clipEndMs > startMs ? clipEndMs : -1L;
        String key = reaction.name() + "_" + startMs + "_" + endMs;
        Path existing = CACHED_TEMP_AUDIO_FILES.get(key);
        if (existing != null && Files.exists(existing)) {
            return existing;
        }
        String audioResource = reaction.audioResource();
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(audioResource)) {
            if (raw == null) {
                return null;
            }
            Path tempFile = Files.createTempFile(
                    "cat-clapping-" + reaction.name().toLowerCase(Locale.ROOT) + "-", ".wav");
            tempFile.toFile().deleteOnExit();
            if (startMs <= 0L && endMs <= 0L) {
                Files.copy(raw, tempFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                try (BufferedInputStream buffered = new BufferedInputStream(raw);
                     AudioInputStream rawStream = AudioSystem.getAudioInputStream(buffered);
                     AudioInputStream sliced = sliceAudioStream(rawStream, startMs, endMs)) {
                    AudioSystem.write(sliced, AudioFileFormat.Type.WAVE, tempFile.toFile());
                } catch (Exception e) {
                    Files.deleteIfExists(tempFile);
                    throw new IOException("Could not slice audio resource: " + audioResource, e);
                }
            }
            CACHED_TEMP_AUDIO_FILES.put(key, tempFile);
            return tempFile;
        }
    }
}
