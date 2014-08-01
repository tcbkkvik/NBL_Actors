/*
 * Copyright (c) 2014 Tor C Bekkvik
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package flc.lambdactor.experimental;

import com.sun.imageio.plugins.gif.GIFImageWriter;
import com.sun.imageio.plugins.gif.GIFImageWriterSpi;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Date: 14.02.14
 *
 * @author Tor C Bekkvik
 */
public class BuddhaBrot2 {
    final int px, py;
    final int k = 4;
    final int maxRed = 40;
    final int maxGreen = maxRed * k;
    final int maxBlue = maxGreen * k;
    float[][] path = new float[2][maxBlue];
    final int[][] pxRed;
    final int[][] pxGreen;
    final int[][] pxBlue;
    final BufferedImage image;
    float factorRed;
    float factorGreen;
    float factorBlue;
    MyRender frame;
    GIF2 gifWriter;

    public BuddhaBrot2(int pixels) throws InterruptedException {
        px = py = pixels;
        pxRed = new int[px][py];
        pxGreen = new int[px][py];
        pxBlue = new int[px][py];
        image = new BufferedImage(px, py, BufferedImage.TYPE_INT_RGB);
        frame = new MyRender(image, pixels);
    }

    public void mainBuddhaLoop(float increm) {
//            float k = increm * 8000 / 4 * px / 800f;
        float k = increm * px * 2.8f;
        float kk = k * k;
        factorRed = 0.362f * kk;
        factorGreen = 0.226f * kk;
        factorBlue = 0.187f * kk;
        long t = System.currentTimeMillis();
        long t2 = t;
        int count = 0;
        for (float Cr = -2; Cr < 2; Cr += increm) {
            if (++count % 10 == 0) t2 = System.currentTimeMillis();
            if (t2 - t > 10000) {
                float progress = 100f * (Cr + 2) / 4;
                log("  " + progress);
                t = t2;
            }
            for (float Ci = -2; Ci < 2; Ci += increm)
                pathFrom(Cr, Ci);
        }
    }

    /**
     * Calculate real part of Z(n) = Z(n-1)^2 + C
     *
     * @param Zr real Z
     * @param Zi imaginary Z
     * @param Cr real constant
     * @return real part
     */
    static float calcReal(float Zr, float Zi, float Cr) {
        return Zr * Zr - Zi * Zi + Cr;
    }

    /**
     * Calculate imaginary part of Z(n) = Z(n-1)^2 + C
     *
     * @param Zr real Z
     * @param Zi imaginary Z
     * @param Ci imaginary constant
     * @return imaginary part
     */
    static float calcImaginary(float Zr, float Zi, float Ci) {
        return 2 * Zr * Zi + Ci;
    }

    static void addPixels(int n, int px, int py, float[][] trajectory, int[][] pixels) {
        for (int i = 0; i < n; i++) {
            int x = (int) ((trajectory[0][i] + 2) * px / 4);
            int y = (int) ((trajectory[1][i] + 2) * py / 4);
            if (x >= 0 && x < px && y >= 0 & y < py) {
                pixels[x][y]++;
            }
        }
    }

    static int rgb2pix(float r, float g, float b) {
        return Math.min(255, (int) r) << 16
                | Math.min(255, (int) g) << 8
                | Math.min(255, (int) b);
    }

    static void saveImg(BufferedImage imagen, File f) {
        try {
            ImageIO.write(imagen, "PNG", f);
            log(" OK wrote " + f.getAbsolutePath());
        } catch (Exception excepcion) { excepcion.printStackTrace(); }
    }

    private void pathFrom(float Cr, float Ci) {
        float Zr = 0, Zi = 0;
        for (int n = 0; n < maxBlue; n++) {
            float tmp = calcImaginary(Zr, Zi, Ci);
            path[0][n] = Zr = calcReal(Zr, Zi, Cr);
            path[1][n] = Zi = tmp;
            if (Zr * Zr + Zi * Zi > 4) {
                if (n < maxRed)
                    addPixels(n, px, py, path, pxRed);
                if (n < maxGreen)
                    addPixels(n, px, py, path, pxGreen);
                addPixels(n, px, py, path, pxBlue);
                drawPath(n);
                break;
            }
        }
    }

    private long time;

    @SuppressWarnings("SuspiciousNameCombination")
    private void drawPath(int n) {
        for (int i = 0; i < n; i++) {
            int x = (int) ((path[0][i] + 2) * px / 4);
            int y = (int) ((path[1][i] + 2) * py / 4);
            if (x >= 0 && x < px && y >= 0 & y < py) {
                int pix = rgb2pix(
                        pxRed[x][y] * factorRed,
                        pxGreen[x][y] * factorGreen,
                        pxBlue[x][y] * factorBlue
                );
                image.setRGB(y, x, pix);
            }
        }
        long t = System.currentTimeMillis();
        if (t - time > 200) {
            time = t;
            frame.repaint();
            if (gifWriter != null)
                try {
                    gifWriter.addImage(image);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public void drawFullImage() {
        int[] pixels = new int[px * py];
        for (int x = 0; x < px; x++) {
            for (int y = 0; y < py; y++) {
                int pix = rgb2pix(
                        pxRed[x][y] * factorRed,
                        pxGreen[x][y] * factorGreen,
                        pxBlue[x][y] * factorBlue
                );
                pixels[x * py + y] = pix;
            }
        }
        image.setRGB(0, 0, px, py, pixels, 0, py);
    }

    static class MyRender extends JFrame {
        final BufferedImage image;

        //        Graphics2D g2d;
//        private final BufferStrategy strategy;
        public MyRender(BufferedImage img, int pixWidth) throws HeadlessException {
//            createBufferStrategy(2); //IllegalStateException: Component must have a valid peer
//            strategy = getBufferStrategy();
            image = img;
//            g2d = image.createGraphics();
            JFrame frame = this;
            frame.setTitle("Points");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//        frame.add(new Surface());
            frame.setSize(pixWidth, (int) (0.68 * pixWidth));
            frame.setLocationRelativeTo(null);
//            frame.setIgnoreRepaint(true);
            //show:
//            frame.pack();
            frame.setVisible(true);
        }

        @Override
        public void paint(Graphics gr) {
//            try
            {
//                gr = strategy.getDrawGraphics();
                Graphics2D g2d = (Graphics2D) gr;
                g2d.drawImage(image, null, 0, 0);
//            } finally {
//                gr.dispose();
            }
//            strategy.show();
        }
    }

/*
    @SuppressWarnings("UnusedDeclaration")
    static class GIF {
        GifSequenceWriter writer;
        final ImageOutputStream output;
        final int millis;

        public GIF(int millis, File outFile) throws IOException {
            this.millis = millis;
            output = new FileImageOutputStream(outFile);
        }

        public void addImage(BufferedImage img) throws IOException {
            if (writer == null) {
                writer = new GifSequenceWriter(output, img.getType(), millis, false);
            }
            writer.writeToSequence(img);
        }

        public void close() throws IOException {
            writer.close();
            output.close();
        }
    }
*/

    @SuppressWarnings("UnusedDeclaration")
    static class GIF2 {
        final ImageOutputStream output;
        final int millis;
        GIFImageWriter gifW;

        public GIF2(int millis, File outFile) throws IOException {
            gifW = new GIFImageWriter(new GIFImageWriterSpi());
            this.millis = millis;
            output = new FileImageOutputStream(outFile);
        }

        public void addImage(BufferedImage img) throws IOException {
            gifW.write(img);
        }

        public void close() throws IOException {
            output.close();
        }
    }


    static void log(Object obj) {
        System.out.println(obj);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        int pixels = 400;
        float increment = 4f / 3000;
        String filePre = "C:/temp/BuddhaBrot" + pixels;
        File f = new File(filePre + ".png");
        log("Generating BuddhaBrot to file: " + f.getAbsolutePath() + " ..");
        long t = System.currentTimeMillis();
        //todo Do in parallel using green-threads/actors?
        BuddhaBrot2 buddhaBrot = new BuddhaBrot2(pixels);
//        buddhaBrot.gifWriter = new GIF2(200, new File(filePre + ".gif"));
        buddhaBrot.mainBuddhaLoop(increment);
//        buddhaBrot.gifWriter.close();
//            buddhaBrot.drawFullImage();
//            buddhaBrot.frame.repaint();
        saveImg(buddhaBrot.image, f);
        long sec = (System.currentTimeMillis() - t) / 1000;
        log("Duration[sec]: " + sec);
    }
}
