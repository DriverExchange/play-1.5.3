package play;

/**
 * Main logger of the application.
 * Free to use from the application code.
 */
public class LoggedObject {

    private Object correlationId;
    private Object sessionId;
    private Object message;

    public LoggedObject(final Object correlationId, final Object sessionId, final Object message) {
        this.correlationId = correlationId;
        this.sessionId = sessionId;
        this.message = message;
    }

    public Object getCorrelationId() {
        return correlationId;
    }

    public Object getSessionId() {
        return sessionId;
    }

    public Object getMessage() {
        return message;
    }
}
