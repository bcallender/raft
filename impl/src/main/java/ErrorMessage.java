/**
 * ErrorMessage helper class to enable easy serialization of error message for sending out on zmq.
 */
public class ErrorMessage extends Message {

    private String error;

    public ErrorMessage(MessageType type, String destination, int id, String source, String error) {
        super(type, destination, id, source);
        this.error = error;
    }

}
