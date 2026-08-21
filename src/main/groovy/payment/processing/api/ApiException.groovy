package payment.processing.api

class ApiException extends RuntimeException {

    final ApiError error

    ApiException(ApiError error) {
        this(error, error.message)
    }

    ApiException(ApiError error, String message) {
        super(message)
        this.error = error
    }
}
