package com.fsck.ptl.mailstore;

import com.fsck.ptl.mail.Message;

public interface MessageRemovalListener {
    public void messageRemoved(Message message);
}
