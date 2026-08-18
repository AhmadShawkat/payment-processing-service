package payment.processing.api

import grails.converters.JSON
import payment.processing.dto.ApiErrorResponse

trait ApiResponse {

    private static final String AUTHENTICATION_ERROR = '01'
    private static final String VALIDATION_ERROR = '02'
    private static final String NOT_FOUND_ERROR = '03'
    private static final String CONFLICT_ERROR = '04'
    private static final String PAYMENT_STATE_ERROR = '10'

    void executeApi(Closure operation) {
        executeApi(200, operation)
    }

    void executeApi(int successStatus, Closure operation) {
        try {
            renderJson(operation.call(), successStatus)
        } catch (IllegalArgumentException exception) {
            if (exception.message == 'API key is required') {
                renderError(AUTHENTICATION_ERROR, exception.message, 401)
                return
            }

            renderError(VALIDATION_ERROR, exception.message, 400)
        } catch (NoSuchElementException exception) {
            if (exception.message == 'Active merchant not found') {
                renderError(AUTHENTICATION_ERROR, exception.message, 401)
                return
            }

            renderError(NOT_FOUND_ERROR, exception.message, 404)
        } catch (IllegalStateException exception) {
            String errorCode = isPaymentStateError(exception.message)
                    ? PAYMENT_STATE_ERROR
                    : CONFLICT_ERROR

            renderError(errorCode, exception.message, 409)
        }
    }

    private void renderJson(Object body, int status) {
        render(
                status: status,
                contentType: 'application/json',
                text: (body as JSON).toString()
        )
    }

    private void renderError(String errorCode, String message, int status) {
        renderJson(
                new ApiErrorResponse(errorCode: errorCode, error: message),
                status
        )
    }

    private static boolean isPaymentStateError(String message) {
        message == 'Payment already captured' ||
                message == 'Payment already refunded' ||
                message?.startsWith('Only ')
    }
}
