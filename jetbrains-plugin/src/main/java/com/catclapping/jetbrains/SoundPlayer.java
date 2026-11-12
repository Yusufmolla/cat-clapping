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
 */
public final class SoundPlayer {

    private static final Logger LOG = Logger.getInstance(SoundPlayer.class);
    private static final String AUDIO_RESOURCE = "/media/cat-clapping.wav";
    private static volatile Path cachedTempAudioFile;

    private SoundPlayer() {
    }

    public static void play() {
        double volume = CatClappingSettings.getInstance().getVolumePercent() / 100.0;
        if (volume <= 0.0) {
            return;
        }
        Thread thread = new Thread(() -> {
            if (!playViaJavaSound(volume)) {
                playViaOsFallback(volume);
            }
        }, "cat-clapping-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean playViaJavaSound(double volume) {
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(AUDIO_RESOURCE)) {
            if (raw == null) {
                LOG.warn("Audio resource not found: " + AUDIO_RESOURCE);
                return false;
            }
            try (BufferedInputStream buffered = new BufferedInputStream(raw);
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(buffered)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                applyVolume(clip, volume);
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
                clip.close();
                return true;
            }
        } catch (Exception e) {
            LOG.debug("Java Sound playback failed, trying OS fallback", e);
            return false;
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
