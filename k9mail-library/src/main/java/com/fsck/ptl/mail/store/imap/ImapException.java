package com.fsck.ptl.mail.store.imap;

import com.fsck.ptl.mail.MessagingException;

class ImapException extends MessagingException {
    private static final long serialVersionUID = 3725007182205882394L;
    private final String mAlertText;

    public ImapException(String message, String alertText) {
        super(message, true);
        this.mAlertText = alertText;
    }
}
