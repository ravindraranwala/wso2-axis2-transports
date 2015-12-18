package org.apache.axis2.transport.websocket;

public class AxisWebsocketException extends Exception {
    public AxisWebsocketException(Throwable cause) {
        super(cause);
    }

    public AxisWebsocketException(String message) {
        super(message);
    }

    public AxisWebsocketException(String message, Throwable cause) {
        super(message, cause);
    }

}
