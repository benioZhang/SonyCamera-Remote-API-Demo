/*
 * Copyright 2014 Sony Corporation
 */

package com.benio.sonycameradsc_qx100.utils;

import android.graphics.Point;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * A parser class for Liveview data Packet defined by Camera Remote API
 */
public class SimpleLiveviewSlicer {

    private static final String TAG = SimpleLiveviewSlicer.class.getSimpleName();

    /**
     * Payload data class. See also Camera Remote API specification document to
     * know the data structure.
     */
    public static final class Payload {
        /**
         * jpeg data container
         */
        public final byte[] jpegData;

        /**
         * padding data container
         */
        public final byte[] paddingData;

        private List<Frame> frames;

        public final Frame frame;

        /**
         * Constructor
         */
        private Payload(byte[] jpeg, byte[] padding) {
            this.jpegData = jpeg;
            this.paddingData = padding;
            frame = null;
        }

        private Payload(Frame frame, byte[] padding) {
            this.frame = frame;
            this.paddingData = padding;
            this.jpegData = null;
        }

        public List<Frame> getFrames() {
            return frames;
        }

        public void setFrames(List<Frame> frames) {
            this.frames = frames;
        }
    }

    public static final class Frame {
        public static final int STATUS_FOCUSED = 0x04;

        public static final int CATEGORY_CONTRAST_AF = 0x01;
        public static final int CATEGORY_PHASE_DETECTION_AF = 0x02;
        public static final int CATEGORY_FACE = 0x04;
        public static final int CATEGORY_TRACKING = 0x05;

        public final Point point1;
        public final Point point2;
        public final int category;
        public final int status;

        public boolean isAF() {
            return category == CATEGORY_CONTRAST_AF || category == CATEGORY_PHASE_DETECTION_AF;
        }

        private Frame(Point point1, Point point2, int category, int status) {
            this.point1 = point1;
            this.point2 = point2;
            this.category = category;
            this.status = status;
        }

        private Frame(byte[] frameData) {
            int x1 = bytesToInt(frameData, 2, 2);
            int y1 = bytesToInt(frameData, 0, 2);
            int x2 = bytesToInt(frameData, 6, 2);
            int y2 = bytesToInt(frameData, 4, 2);
            category = bytesToInt(frameData, 8, 1);
            status = bytesToInt(frameData, 9, 1);
            point1 = new Point(x1, y1);
            point2 = new Point(x2, y2);
            Log.d(TAG, "x1: " + x1 + " y1: " + y1 + " x2: " + x2 + " y2:" + y2 + " category: " + category + " status: " + status);
        }
    }

    private static final int CONNECTION_TIMEOUT = 2000; // [msec]

    private HttpURLConnection mHttpConn;

    private InputStream mInputStream;

    /**
     * Opens Liveview HTTP GET connection and prepares for reading Packet data.
     *
     * @param liveviewUrl Liveview data url that is obtained by DD.xml or result
     *                    of startLiveview API.
     * @throws IOException generic errors or exception.
     */
    public void open(String liveviewUrl) throws IOException {
        if (mInputStream != null || mHttpConn != null) {
            throw new IllegalStateException("Slicer is already open.");
        }

        final URL urlObj = new URL(liveviewUrl);
        mHttpConn = (HttpURLConnection) urlObj.openConnection();
        mHttpConn.setRequestMethod("GET");
        mHttpConn.setConnectTimeout(CONNECTION_TIMEOUT);
        mHttpConn.connect();

        if (mHttpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            mInputStream = mHttpConn.getInputStream();
        }
    }

    /**
     * Closes the connection.
     *
     * @throws IOException generic errors or exception.
     */
    public void close() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
                mInputStream = null;
            }
        } catch (IOException e) {
            Log.w(TAG, "Close() IOException.");
        }

        if (mHttpConn != null) {
            mHttpConn.disconnect();
            mHttpConn = null;
        }

    }

    public Payload nextPayload() throws IOException {

        Payload payload = null;

        while (mInputStream != null && payload == null) {
            // Common Header
            int readLength = 1 + 1 + 2 + 4;
            byte[] commonHeader = readBytes(mInputStream, readLength);
            if (commonHeader == null || commonHeader.length != readLength) {
                throw new IOException("Cannot read stream for common header.");
            }

            if (commonHeader[0] != (byte) 0xFF) {
                throw new IOException("Unexpected data format. (Start byte)");
            }

            switch (commonHeader[1]) {
                case (byte) 0x02:// For Liveview Frame Information
                    // This is information header for Liveview.
                    // skip this packet.
//                    readLength = 4 + 3 + 1 + 2 + 2 + 2 + 114 + 4 + 4 + 1 + 1 + 1 + 5;
                    commonHeader = null;
                    Log.d(TAG, "Liveview information header ");
//                    readBytes(mInputStream, readLength);
                    readLiveviewPayload();
                    break;
                case (byte) 0x01:// For liveview images
                    Log.d(TAG, "liveview images");
                    payload = readPayload();
                    break;
                case (byte) 0x11:// For Streaming Images
                    Log.d(TAG, "Streaming images");
                    payload = readPayload();
                    break;
                case (byte) 0x12://For Streaming Playback Information
                    // This is information header for streaming.
                    // skip this packet.
                    readLength = 4 + 3 + 1 + 2 + 118 + 4 + 4 + 24;
                    commonHeader = null;
                    Log.d(TAG, "Streaming information header ");
                    readBytes(mInputStream, readLength);
                    break;
                default:
                    break;
            }
        }
        return payload;
    }

    public Payload readLiveviewPayload() throws IOException {

        if (mInputStream != null) {
            // Payload Header
            int readLength = 128;
            byte[] payloadHeader = readBytes(mInputStream, readLength);
            if (payloadHeader == null || payloadHeader.length != readLength) {
                throw new IOException("Cannot read stream for payload header.");
            }
            if (payloadHeader[0] != (byte) 0x24 || payloadHeader[1] != (byte) 0x35
                    || payloadHeader[2] != (byte) 0x68
                    || payloadHeader[3] != (byte) 0x79) {
                throw new IOException("Unexpected data format. (Start code)");
            }
            int frameSize = bytesToInt(payloadHeader, 4, 3);
            int paddingSize = bytesToInt(payloadHeader, 7, 1);

            // Payload Data
            byte[] frameData = readBytes(mInputStream, frameSize);
            byte[] paddingData = readBytes(mInputStream, paddingSize);

            int frameCount = bytesToInt(payloadHeader, 10, 2);
            int singleFrameSize = bytesToInt(payloadHeader, 12, 2);

            Log.d(TAG, "frameCount: " + frameCount + " singleFrameSize: " + singleFrameSize);
            if (frameData == null || frameData.length != frameCount * singleFrameSize) {
                throw new IOException("Unexpected data format.(Frame information data)");
            }

            Frame frame = null;
            for (int i = 0; i < frameCount; i++) {
                Frame f = readFrame(frameData, i * singleFrameSize);
                if (f.isAF()) {
                    frame = f;
                }
            }

            return new Payload(frame, paddingData);
        }
        return null;
    }

    /**
     * Reads liveview stream and slice one Packet. If server is not ready for
     * liveview data, this API calling will be blocked until server returns next
     * data.
     *
     * @return Payload data of sliced Packet
     * @throws IOException generic errors or exception.
     */
    public Payload readPayload() throws IOException {

        if (mInputStream != null) {
            // Payload Header
            int readLength = 128;
            byte[] payloadHeader = readBytes(mInputStream, readLength);
            if (payloadHeader == null || payloadHeader.length != readLength) {
                throw new IOException("Cannot read stream for payload header.");
            }
            if (payloadHeader[0] != (byte) 0x24 || payloadHeader[1] != (byte) 0x35
                    || payloadHeader[2] != (byte) 0x68
                    || payloadHeader[3] != (byte) 0x79) {
                throw new IOException("Unexpected data format. (Start code)");
            }
            int jpegSize = bytesToInt(payloadHeader, 4, 3);
            int paddingSize = bytesToInt(payloadHeader, 7, 1);

            // Payload Data
            byte[] jpegData = readBytes(mInputStream, jpegSize);
            byte[] paddingData = readBytes(mInputStream, paddingSize);

            return new Payload(jpegData, paddingData);
        }
        return null;
    }

    /**
     * Converts byte array to int.
     *
     * @param byteData
     * @param startIndex
     * @param count
     * @return
     */
    private static int bytesToInt(byte[] byteData, int startIndex, int count) {
        int ret = 0;
        for (int i = startIndex; i < startIndex + count; i++) {
            ret = (ret << 8) | (byteData[i] & 0xff);
        }
        return ret;
    }

    /**
     * Reads byte array from the indicated input stream.
     *
     * @param in
     * @param length
     * @return
     * @throws IOException
     */
    private static byte[] readBytes(InputStream in, int length) throws IOException {
        ByteArrayOutputStream tmpByteArray = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (true) {
            int trialReadlen = Math.min(buffer.length, length - tmpByteArray.size());
            int readlen = in.read(buffer, 0, trialReadlen);
            if (readlen < 0) {
                break;
            }
            tmpByteArray.write(buffer, 0, readlen);
            if (length <= tmpByteArray.size()) {
                break;
            }
        }
        byte[] ret = tmpByteArray.toByteArray();
        tmpByteArray.close();
        return ret;
    }

    private static Frame readFrame(byte[] frameData, int startIndex) {
        int x1 = bytesToInt(frameData, startIndex, 2);
        int y1 = bytesToInt(frameData, startIndex + 2, 2);
        int x2 = bytesToInt(frameData, startIndex + 4, 2);
        int y2 = bytesToInt(frameData, startIndex + 6, 2);
        int category = bytesToInt(frameData, startIndex + 8, 1);
        int status = bytesToInt(frameData, startIndex + 9, 1);
        Point point1 = new Point(x1, y1);
        Point point2 = new Point(x2, y2);
        Log.d(TAG, "x1: " + x1 + " y1: " + y1 + " x2: " + x2 + " y2:" + y2 + " category: " + category + " status: " + status);
        return new Frame(point1, point2, category, status);
    }
}
