package ch.zhaw.pm2.multichat.protocol;

public class ChatProtocolException extends Exception {

    public ChatProtocolException(String message) {
        super(message);
    }

    public ChatProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChatProtocolException(Throwable cause) {
        super(cause);
    }

}
