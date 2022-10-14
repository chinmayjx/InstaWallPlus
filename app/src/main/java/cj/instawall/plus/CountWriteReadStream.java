package cj.instawall.plus;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

class CountWriteReadStream extends PipedOutputStream {
    private boolean writing = true;
    private long count = 0;
    PipedInputStream inputStream;

    public CountWriteReadStream() throws IOException {
        super();
        inputStream = new PipedInputStream(this, 1024*1024*50);
    }

    @Override
    public void write(int b) throws IOException {
        count++;
        super.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        count += len;
        super.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        super.write(b);
    }

    @Override
    public void close() throws IOException {
        writing = false;
        super.close();
    }

    public InputStream getInputStream() throws IOException {
        if (writing) {
            throw new IOException("Still writing bro");
        }
        return inputStream;
    }

    public long getCount() {
        return count;
    }
    public boolean isWriting(){
        return writing;
    }
}
