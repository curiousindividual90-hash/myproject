package com.satmeter.app;

/** Something that can ask the receiver for its current signal values. */
public interface ReceiverClient {
    Reading read() throws Exception;
}
