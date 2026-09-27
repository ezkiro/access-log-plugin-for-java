package io.github.ezkiro.accesslog.support;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * response를 클라이언트로 즉시 전달하면서 로그용 body만 제한된 크기로 복사한다.
 */
public class BoundedContentCachingResponseWrapper extends HttpServletResponseWrapper {

    private final int cacheLimit;
    private final ByteArrayOutputStream cachedContent;

    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean overflowed;

    public BoundedContentCachingResponseWrapper(HttpServletResponse response, int cacheLimit) {
        super(response);
        if (cacheLimit <= 0) {
            throw new IllegalArgumentException("cacheLimit must be greater than zero");
        }
        this.cacheLimit = cacheLimit;
        this.cachedContent = new ByteArrayOutputStream(Math.min(cacheLimit, 1024));
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (outputStream == null) {
            outputStream = new CachingServletOutputStream(super.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer != null) {
            return writer;
        }
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        Charset charset = resolveCharset(getCharacterEncoding());
        outputStream = new CachingServletOutputStream(super.getOutputStream());
        writer = new PrintWriter(new OutputStreamWriter(outputStream, charset));
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        flushCapturedContent();
        super.flushBuffer();
    }

    public void flushCapturedContent() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        cachedContent.reset();
        overflowed = false;
    }

    @Override
    public void reset() {
        super.reset();
        cachedContent.reset();
        overflowed = false;
    }

    public byte[] getContentAsByteArray() {
        return cachedContent.toByteArray();
    }

    public boolean isOverflowed() {
        return overflowed;
    }

    private void cache(byte[] bytes, int offset, int length) {
        int remaining = cacheLimit - cachedContent.size();
        if (remaining > 0) {
            int bytesToCache = Math.min(remaining, length);
            cachedContent.write(bytes, offset, bytesToCache);
        }
        if (length > remaining) {
            overflowed = true;
        }
    }

    private static Charset resolveCharset(String characterEncoding) {
        if (characterEncoding == null || characterEncoding.isBlank()) {
            return StandardCharsets.ISO_8859_1;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (Exception exception) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    private final class CachingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private CachingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            if (cachedContent.size() < cacheLimit) {
                cachedContent.write(value);
            } else {
                overflowed = true;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
            cache(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
