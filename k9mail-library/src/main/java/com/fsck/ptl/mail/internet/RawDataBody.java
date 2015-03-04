package com.fsck.ptl.mail.internet;


import com.fsck.ptl.mail.Body;


/**
 * See {@link MimeUtility#decodeBody(Body)}
 */
public interface RawDataBody extends Body {
    String getEncoding();
}
