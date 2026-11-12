package com.catclapping.jetbrains;

import com.intellij.openapi.diagnostic.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

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
        Thread thread = new Thread(() -> {
            if (!playViaJavaSound()) {
                playViaOsFallback();
            }
        }, "cat-clapping-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean playViaJavaSound() {
        try (InputStream raw = SoundPlayer.class.getResourceAsStream(AUDIO_RESOURCE)) {
            if (raw == null) {
                LOG.warn("Audio resource not found: " + AUDIO_RESOURCE);
                return false;
            }
            try (BufferedInputStream buffered = new BufferedInputStream(raw);
                 AudioInputStream audioStream = AudioSystem.getAudioInputStream(buffered)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                final Object lock = new Object();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                });
                clip.start();
                synchronized (lock) {
                    while (clip.isRunning()) {
                        lock.wait(500);
                    }
                }
                clip.close();
                return true;
            }
        } catch (Exception e) {
            LOG.debug("Java Sound playback failed, trying OS fallback", e);
            return false;
        }
    }

    private static void playViaOsFallback() {
        try {
            Path audioFile = ensureTempAudioFile();
            if (audioFile == null) {
                return;
            }
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String path = audioFile.toAbsolutePath().toString();
            if (os.contains("mac")) {
                runPlayer(List.of("afplay", path));
            } else if (os.contains("win")) {
                String escaped = path.replace("'", "''");
                String cmd = "$player = New-Object System.Media.SoundPlayer; "
                        + "$player.SoundLocation = '" + escaped + "'; "
                        + "$player.Load(); $player.PlaySync();";
                runPlayer(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", cmd));
            } else {
                if (!runPlayer(List.of("paplay", path))) {
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
