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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Plays the bundled celebration WAV sound across macOS, Linux, and Windows.
 * <p>
 * Use {@link #prepare()} off the EDT to open the audio line in advance, then {@link Playback#start()}
 * at the exact moment the animation starts. {@link Playback#positionMs()} reports how much audio has
 * actually been played so the animation can follow the sound.
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getInstance(SoundPlayer.class);
    private static final String AUDIO_RESOURCE = "/media/cat-clapping.wav";
    /** Rough start-up latency of external players such as afplay. */
    private static final long OS_PLAYER_LATENCY_MS = 80L;
    private static volatile Path cachedTempAudioFile;

    private SoundPlayer() {
    }

    /** Fire-and-forget playback (used when no animation is shown). */
    public static void play() {
        Thread thread = new Thread(() -> {
            Playback playback = prepare();
            if (playback != null) {
                playback.start();
            }
        }, "cat-clapping-audio");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Opens the audio line so playback can start without delay. May block; call off the EDT.
     *
     * @return a ready playback, or null when the volume is 0.
     */
    public static Playback prepare() {
        double volume = CatClappingSettings.getInstance().getVolumePercent() / 100.0;
        if (volume <= 0.0) {
            return null;
        }
        Clip clip = openClip(volume);
        return new Playback(clip, volume);
    }

    public static final class Playback {
        private final Clip clip;
        private final double volume;
        private volatile long startNanos = -1L;

        private Playback(Clip clip, double volume) {
            this.clip = clip;
            this.volume = volume;
        }

        /** Starts playback asynchronously. */
        public void start() {
            if (startNanos >= 0) {
                return;
            }
            startNanos = System.nanoTime();
            Thread thread = new Thread(clip != null ? this::runClip : () -> playViaOsFallback(volume),
                    "cat-clapping-audio");
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
            long clipMs = clip.getMicrosecondPosition() / 1000L;
            if (clipMs > 0) {
                return clipMs;
            }
            // Safety net if the audio device never reports progress.
            return Math.max(wallMs - 500L, 0L);
        }

        /** Releases the audio line if playback was never started. */
        public void discard() {
            if (startNanos < 0 && clip != null) {
                clip.close();
            }
        }

        private void runClip() {
            try {
                CountDownLatch finished = new CountDownLatch(1);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                        finished.countDown();
                    }
                });
                // Clip.start() is asynchronous and isRunning() is still false right after it,
                // so wait for the STOP event (bounded by the clip length) before closing.
                long lengthMs = Math.max(clip.getMicrosecondLength() / 1000L, 0L);
                clip.start();
                finished.await(lengthMs + 2000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                clip.close();
            }
        }
    }

    private static Clip openClip(double volume) {
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(AUDIO_RESOURCE)) {
            if (raw == null) {
                LOG.warn("Audio resource not found: " + AUDIO_RESOURCE);
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

    private static void playViaOsFallback(double volume) {
        try {
            Path audioFile = ensureTempAudioFile();
            if (audioFile == null) {
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String path = audioFile.toAbsolutePath().toString();
            if (os.contains("mac")) {
                runPlayer(List.of("afplay", "-v", String.format(Locale.ROOT, "%.2f", volume), path));
            } else if (os.contains("win")) {
                String escaped = path.replace("'", "''");
                String cmd = "$player = New-Object System.Media.SoundPlayer; "
                        + "$player.SoundLocation = '" + escaped + "'; "
                        + "$player.Load(); $player.PlaySync();";
                runPlayer(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", cmd));
            } else {
                int paVolume = (int) Math.round(volume * 65536);
                if (!runPlayer(List.of("paplay", "--volume=" + paVolume, path))) {
                    runPlayer(List.of("aplay", path));
                }
            }
        } catch (Exception e) {
            LOG.warn("OS fallback audio playback failed", e);
        }
    }

    private static boolean runPlayer(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static synchronized Path ensureTempAudioFile() throws IOException {
        if (cachedTempAudioFile != null && Files.exists(cachedTempAudioFile)) {
            return cachedTempAudioFile;
        }
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(AUDIO_RESOURCE)) {
            if (raw == null) {
                return null;
            }
            Path tempFile = Files.createTempFile("cat-clapping-", ".wav");
            tempFile.toFile().deleteOnExit();
            Files.copy(raw, tempFile, StandardCopyOption.REPLACE_EXISTING);
            cachedTempAudioFile = tempFile;
            return tempFile;
        }
    }
}
