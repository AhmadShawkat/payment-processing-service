package payment.processing

import payment.processing.api.ApiResponse
import payment.processing.dto.CreateMerchantCommand

class MerchantController implements ApiResponse {

    MerchantService merchantService

    def save(CreateMerchantCommand command) {
        executeApi(201) {
            merchantService.create(command)
        }
    }
}
