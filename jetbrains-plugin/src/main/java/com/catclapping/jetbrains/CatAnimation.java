package com.catclapping.jetbrains;

import com.intellij.openapi.diagnostic.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.LongSupplier;

/**
 * Decodes the clapping GIF frame by frame on a background thread, scaled to the requested size.
 * <p>
 * Unlike AWT's built-in GIF animation (which sleeps the frame delay <em>after</em> decoding each frame
 * and therefore drifts), frames are tagged with their exact start time so the overlay can show the frame
 * that belongs to the current audio position. Frames that are already late are skipped.
 */
final class CatAnimation {

    private static final Logger LOG = Logger.getInstance(CatAnimation.class);
    private static final String GIF_RESOURCE = "/media/cat-clapping.gif";
    private static final String GIF_METADATA_FORMAT = "javax_imageio_gif_image_1.0";
    private static final int QUEUE_CAPACITY = 8;

    private static volatile byte[] cachedGifBytes;
    private static volatile int[] cachedRawSize;

    static final class Frame {
        final BufferedImage image;
        final long startMs;
        final long endMs;

        Frame(BufferedImage image, long startMs, long endMs) {
            this.image = image;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final int pixelWidth;
    private final int pixelHeight;
    private final LongSupplier clockMs;
    private final Thread thread;
    private volatile boolean finished;
    private volatile boolean failed;

    /**
     * @param pixelWidth  width of produced frames in device pixels
     * @param pixelHeight height of produced frames in device pixels
     * @param clockMs     current playback position in ms, or a negative value while not started yet
     */
    CatAnimation(int pixelWidth, int pixelHeight, LongSupplier clockMs) {
        this.pixelWidth = Math.max(pixelWidth, 1);
        this.pixelHeight = Math.max(pixelHeight, 1);
        this.clockMs = clockMs;
        this.thread = new Thread(this::decodeLoop, "cat-clapping-gif-decoder");
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    void stop() {
        thread.interrupt();
        queue.clear();
    }

    /** Returns the next decoded frame without removing it, or null. */
    Frame peek() {
        return queue.peek();
    }

    Frame poll() {
        return queue.poll();
    }

    int bufferedFrames() {
        return queue.size();
    }

    boolean isFinished() {
        return finished;
    }

    boolean isFailed() {
        return failed;
    }

    /** Raw GIF size {width, height}, or null if it cannot be read. */
    static int[] rawSize() {
        int[] size = cachedRawSize;
        if (size != null) {
            return size;
        }
        byte[] bytes = gifBytes();
        if (bytes == null) {
            return null;
        }
        ImageReader reader = null;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader = gifReader();
            reader.setInput(in, false);
            size = new int[]{reader.getWidth(0), reader.getHeight(0)};
            cachedRawSize = size;
            return size;
        } catch (Exception e) {
            LOG.warn("Could not read GIF size", e);
            return null;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    static byte[] gifBytes() {
        byte[] bytes = cachedGifBytes;
        if (bytes != null) {
            return bytes;
        }
        synchronized (CatAnimation.class) {
            if (cachedGifBytes == null) {
                try (InputStream raw = CatAnimation.class.getResourceAsStream(GIF_RESOURCE)) {
                    if (raw == null) {
                        LOG.warn("GIF resource not found: " + GIF_RESOURCE);
                        return null;
                    }
                    cachedGifBytes = raw.readAllBytes();
                } catch (IOException e) {
                    LOG.warn("Could not load GIF resource", e);
                    return null;
                }
            }
            return cachedGifBytes;
        }
    }

    private static ImageReader gifReader() throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available");
        }
        return readers.next();
    }

    private void decodeLoop() {
        byte[] bytes = gifBytes();
        if (bytes == null) {
            failed = true;
            finished = true;
            return;
        }
        ImageReader reader = null;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader = gifReader();
            reader.setInput(in, false);
            int canvasW = reader.getWidth(0);
            int canvasH = reader.getHeight(0);
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvasG = canvas.createGraphics();
            try {
                long timeMs = 0;
                for (int index = 0; !Thread.currentThread().isInterrupted(); index++) {
                    BufferedImage patch;
                    try {
                        patch = reader.read(index);
                    } catch (IndexOutOfBoundsException end) {
                        break;
                    }
                    FrameInfo info = FrameInfo.read(reader.getImageMetadata(index));

                    BufferedImage previous = null;
                    if ("restoreToPrevious".equals(info.disposal)) {
                        previous = copyRegion(canvas, info);
                    }
                    canvasG.setComposite(AlphaComposite.SrcOver);
                    canvasG.drawImage(patch, info.left, info.top, null);

                    long startMs = timeMs;
                    long endMs = timeMs + info.delayMs;
                    timeMs = endMs;

                    long now = clockMs.getAsLong();
                    boolean late = now >= 0 && endMs <= now;
                    if (!late) {
                        queue.put(new Frame(scale(canvas), startMs, endMs));
                    }

                    // Apply disposal for the next frame.
                    if ("restoreToBackgroundColor".equals(info.disposal)) {
                        canvasG.setComposite(AlphaComposite.Clear);
                        canvasG.fillRect(info.left, info.top, info.width, info.height);
                    } else if (previous != null) {
                        canvasG.setComposite(AlphaComposite.Src);
                        canvasG.drawImage(previous, info.left, info.top, null);
                    }
                }
            } finally {
                canvasG.dispose();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("GIF decoding failed", e);
            failed = true;
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            finished = true;
        }
    }

    private BufferedImage scale(BufferedImage canvas) {
        BufferedImage src = canvas;
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        // Halve step by step for strong downscaling to keep it smooth.
        while (w / 2 >= pixelWidth && h / 2 >= pixelHeight) {
            w /= 2;
            h /= 2;
            src = draw(src, w, h);
        }
        return draw(src, pixelWidth, pixelHeight);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage copyRegion(BufferedImage canvas, FrameInfo info) {
        int x = Math.max(0, info.left);
        int y = Math.max(0, info.top);
        int w = Math.min(info.width, canvas.getWidth() - x);
        int h = Math.min(info.height, canvas.getHeight() - y);
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.drawImage(canvas.getSubimage(x, y, w, h), 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private static final class FrameInfo {
        int left;
        int top;
        int width;
        int height;
        int delayMs = 100;
        String disposal = "none";

        static FrameInfo read(IIOMetadata metadata) {
            FrameInfo info = new FrameInfo();
            Node root = metadata.getAsTree(GIF_METADATA_FORMAT);
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                NamedNodeMap attrs = child.getAttributes();
                if ("ImageDescriptor".equals(child.getNodeName())) {
                    info.left = intAttr(attrs, "imageLeftPosition", 0);
                    info.top = intAttr(attrs, "imageTopPosition", 0);
                    info.width = intAttr(attrs, "imageWidth", 0);
                    info.height = intAttr(attrs, "imageHeight", 0);
                } else if ("GraphicControlExtension".equals(child.getNodeName())) {
                    int hundredths = intAttr(attrs, "delayTime", 10);
                    // Same convention as browsers: tiny delays are treated as 100 ms.
                    info.delayMs = hundredths <= 1 ? 100 : hundredths * 10;
                    Node d = attrs.getNamedItem("disposalMethod");
                    if (d != null) {
                        info.disposal = d.getNodeValue();
                    }
                }
            }
            return info;
        }

        private static int intAttr(NamedNodeMap attrs, String name, int fallback) {
            Node n = attrs.getNamedItem(name);
            if (n == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(n.getNodeValue());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }
}
