package ch.zhaw.pm2.multichat.protocol;

/**
 * Exception class to handle different errors related to protocol.
 */

public class ChatProtocolException extends Exception {

    /**
     * The constructor with message.
     * @param message the message of the exception
     */
    public ChatProtocolException(String message) {
        super(message);
    }

    /**
     * The constructor with message and cause.
     * @param message the message of the exception
     * @param cause the cause that is responsible for this exception
     */
    public ChatProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The constructor with cause of the exception.
     * @param cause the cause that is responsible for this exception
     */
    public ChatProtocolException(Throwable cause) {
        super(cause);
    }

}
