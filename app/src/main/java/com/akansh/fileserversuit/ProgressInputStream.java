package com.akansh.fileserversuit;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class ProgressInputStream extends InputStream {

    private InputStream in;
    private int length, sumRead;
    private java.util.List<ProgressListener> listeners;
    private double percent;

    public ProgressInputStream(InputStream inputStream, int length) {
        this.in = inputStream;
        listeners = new ArrayList<>();
        sumRead = 0;
        this.length = length;
    }


    @Override
    public int read(byte[] b) throws IOException {
        int readCount = in.read(b);
        evaluatePercent(readCount);
        return readCount;
    }


    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int readCount = in.read(b, off, len);
        evaluatePercent(readCount);
        return readCount;
    }

    @Override
    public long skip(long n) throws IOException {
        long skip = in.skip(n);
        evaluatePercent(skip);
        return skip;
    }

    @Override
    public int read() throws IOException {
        int read = in.read();
        if (read != -1) {
            evaluatePercent(1);
        }
        return read;
    }

    public ProgressInputStream addListener(ProgressListener listener) {
        this.listeners.add(listener);
        return this;
    }

    private void evaluatePercent(long readCount) {
        if (readCount != -1) {
            sumRead += readCount;
            percent = sumRead * 1.0 / length;
        }
        notifyListener();
    }

    private void notifyListener() {
        for (ProgressListener listener : listeners) {
            listener.process(mapOneRangeToAnother(percent,0,1,0,100,1));
        }
    }

    public interface ProgressListener {
        void process(double percent);
    }

    private double mapOneRangeToAnother(double sourceNumber, double fromA, double fromB, double toA, double toB, int decimalPrecision ) {
        double deltaA = fromB - fromA;
        double deltaB = toB - toA;
        double scale  = deltaB / deltaA;
        double negA   = -1 * fromA;
        double offset = (negA * scale) + toA;
        double finalNumber = (sourceNumber * scale) + offset;
        int calcScale = (int) Math.pow(10, decimalPrecision);
        return (double) Math.round(finalNumber * calcScale) / calcScale;
    }
}
