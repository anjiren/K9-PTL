package com.fsck.ptl.mail.store.imap;

interface UntaggedHandler {
    void handleAsyncUntaggedResponse(ImapResponse response);
}
