package payment.processing.dto

import payment.processing.Merchant

class MerchantResponse {

    Long id
    String name
    String email
    String apiKey

    static MerchantResponse from(Merchant merchant) {
        new MerchantResponse(
                id: merchant.id,
                name: merchant.name,
                email: merchant.email,
                apiKey: merchant.apiKey
        )
    }
}
