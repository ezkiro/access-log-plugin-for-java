package com.example.accesslog.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * request body cache가 지정된 byte 수를 넘지 않도록 제한하고 overflow 여부를 노출한다.
 */
public class BoundedContentCachingRequestWrapper extends ContentCachingRequestWrapper {

    private boolean overflowed;

    public BoundedContentCachingRequestWrapper(HttpServletRequest request, int cacheLimit) {
        super(request, requirePositive(cacheLimit));
    }

    @Override
    protected void handleContentOverflow(int contentCacheLimit) {
        overflowed = true;
    }

    public boolean isOverflowed() {
        return overflowed;
    }

    private static int requirePositive(int cacheLimit) {
        if (cacheLimit <= 0) {
            throw new IllegalArgumentException("cacheLimit must be greater than zero");
        }
        return cacheLimit;
    }
}
