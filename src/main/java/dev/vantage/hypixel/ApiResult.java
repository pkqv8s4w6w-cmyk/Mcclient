package dev.vantage.hypixel;

/** Outcome of one API call, with the failure modes named rather than left as a null. */
public final class ApiResult {

    public enum Status {
        /** Parsed successfully. */
        OK,
        /** The key was rejected. Asking again will not help until it is replaced. */
        INVALID_KEY,
        /** Budget spent. Back off until the window resets. */
        RATE_LIMITED,
        /** Network or server problem. Worth retrying later. */
        UNAVAILABLE,
        /** A 200 whose body was not what we expected. */
        MALFORMED
    }

    private final Status status;
    private final BedwarsStats stats;
    private final String detail;

    private ApiResult(Status status, BedwarsStats stats, String detail) {
        this.status = status;
        this.stats = stats;
        this.detail = detail;
    }

    public static ApiResult ok(BedwarsStats stats) {
        return new ApiResult(Status.OK, stats, "");
    }

    public static ApiResult failure(Status status, String detail) {
        return new ApiResult(status, BedwarsStats.UNKNOWN, detail == null ? "" : detail);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public BedwarsStats getStats() {
        return stats;
    }

    public String getDetail() {
        return detail;
    }
}
