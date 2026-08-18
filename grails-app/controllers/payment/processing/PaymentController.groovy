package payment.processing

import payment.processing.api.ApiResponse
import payment.processing.dto.CreatePaymentCommand
import payment.processing.dto.ListPaymentsCommand

class PaymentController implements ApiResponse {

    PaymentService paymentService

    def save(CreatePaymentCommand command) {
        executeApi(201) {
            paymentService.create(apiKey, command)
        }
    }

    def index(ListPaymentsCommand command) {
        executeApi {
            paymentService.list(apiKey, command)
        }
    }

    def capture(String reference) {
        executeApi {
            paymentService.capture(apiKey, reference)
        }
    }

    def refund(String reference) {
        executeApi {
            paymentService.refund(apiKey, reference)
        }
    }

    def show(String reference) {
        executeApi {
            paymentService.get(apiKey, reference)
        }
    }

    private String getApiKey() {
        request.getHeader('X-API-KEY')
    }
}
