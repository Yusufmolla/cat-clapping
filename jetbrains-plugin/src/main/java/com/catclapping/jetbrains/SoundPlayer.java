package com.catclapping.jetbrains;

import com.intellij.openapi.diagnostic.Logger;

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
 * Use {@link #prepare(CatReaction, int)} off the EDT to open the audio line in advance, then {@link Playback#start()}
 * at the exact moment the animation starts. {@link Playback#positionMs()} reports how much audio has
 * actually been played so the animation can follow the sound.
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getInstance(SoundPlayer.class);
    /** Rough start-up latency of external players such as afplay. */
    private static final long OS_PLAYER_LATENCY_MS = 80L;
    private static final Map<CatReaction, Path> CACHED_TEMP_AUDIO_FILES = new ConcurrentHashMap<>();

    private SoundPlayer() {
    }

    /** Fire-and-forget playback for explicit reaction, sound toggle, and volume. */
    public static void play(CatReaction reaction, boolean soundEnabled, int volumePercent) {
        Objects.requireNonNull(reaction, "reaction");
        if (!soundEnabled || volumePercent <= 0) {
            return;
        }
        Thread thread = new Thread(() -> {
            Playback playback = prepare(reaction, volumePercent);
            if (playback != null) {
                playback.start();
            }
        }, "cat-clapping-audio-" + reaction.name().toLowerCase(Locale.ROOT));
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Opens the audio line for the given reaction and volume (0..100). May block; call off the EDT.
     */
    public static Playback prepare(CatReaction reaction, int volumePercent) {
        Objects.requireNonNull(reaction, "reaction");
        int clamped = Math.max(0, Math.min(100, volumePercent));
        double volume = clamped / 100.0;
        if (volume <= 0.0) {
            return null;
        }
        Clip clip = openClip(reaction, volume);
        return new Playback(reaction, clip, volume);
    }

    public static final class Playback {
        private final CatReaction reaction;
        private final Clip clip;
        private final double volume;
        private volatile long startNanos = -1L;
        private volatile boolean finished;
        private volatile long lastClipMs;
        private volatile Process activeProcess;

        private Playback(CatReaction reaction, Clip clip, double volume) {
            this.reaction = reaction;
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
                    clip != null ? this::runClip : () -> playViaOsFallback(reaction, volume, this),
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

    private static Clip openClip(CatReaction reaction, double volume) {
        String audioResource = reaction.audioResource();
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(audioResource)) {
            if (raw == null) {
                LOG.warn("Audio resource not found: " + audioResource);
                return null;
            }
            try (BufferedInputStream buffered = new BufferedInputStream(raw);
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(buffered)) {
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

    private static void playViaOsFallback(CatReaction reaction, double volume, Playback playback) {
        try {
            Path audioFile = ensureTempAudioFile(reaction);
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

    private static synchronized Path ensureTempAudioFile(CatReaction reaction) throws IOException {
        Path existing = CACHED_TEMP_AUDIO_FILES.get(reaction);
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
            Files.copy(raw, tempFile, StandardCopyOption.REPLACE_EXISTING);
            CACHED_TEMP_AUDIO_FILES.put(reaction, tempFile);
            return tempFile;
        }
    }
}
