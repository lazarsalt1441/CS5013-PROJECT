package in.ac.iitm.slotbook.server;

/** The request itself is wrong (HTTP 400); its message is safe to show to the admin. */
public final class BadRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
