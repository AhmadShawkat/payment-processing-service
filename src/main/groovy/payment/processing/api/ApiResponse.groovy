package payment.processing.api

import grails.converters.JSON
import payment.processing.dto.ApiErrorResponse

trait ApiResponse {

    void executeApi(Closure operation) {
        executeApi(200, operation)
    }

    void executeApi(int successStatus, Closure operation) {
        try {
            renderJson(operation.call(), successStatus)
        } catch (ApiException exception) {
            renderError(exception)
        }
    }

    private void renderJson(Object body, int status) {
        render(
                status: status,
                contentType: 'application/json',
                text: (body as JSON).toString()
        )
    }

    private void renderError(ApiException exception) {
        renderJson(
                new ApiErrorResponse(
                        errorCode: exception.error.errorCode,
                        error: exception.message
                ),
                exception.error.httpStatus.value()
        )
    }
}
