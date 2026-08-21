package payment.processing

import payment.processing.dto.CreateMerchantCommand
import payment.processing.dto.MerchantResponse
import payment.processing.api.ApiError
import payment.processing.api.ApiException

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

import java.security.SecureRandom
import java.util.Base64

@Transactional
class MerchantService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom()

    RequestValidationService requestValidationService

    MerchantResponse create(CreateMerchantCommand command) {
        requestValidationService.validate(command, ApiError.MERCHANT_REQUEST_REQUIRED)

        String normalizedEmail = command.email.trim().toLowerCase(Locale.ROOT)
        if (Merchant.findByEmail(normalizedEmail)) {
            throw new ApiException(ApiError.MERCHANT_EMAIL_EXISTS)
        }

        Merchant merchant = new Merchant(
                name: command.name.trim(),
                email: normalizedEmail,
                apiKey: generateUniqueApiKey(),
                active: true
        )

        merchant.save(failOnError: true, flush: true)

        MerchantResponse.from(merchant)
    }

    @ReadOnly
    Merchant requireActiveMerchant(String apiKey) {
        if (!apiKey?.trim()) {
            throw new ApiException(ApiError.API_KEY_REQUIRED)
        }

        Merchant merchant = Merchant.findByApiKeyAndActive(apiKey.trim(), true)
        if (!merchant) {
            throw new ApiException(ApiError.ACTIVE_MERCHANT_NOT_FOUND)
        }

        merchant
    }

    private String generateUniqueApiKey() {
        String apiKey

        do {
            byte[] randomBytes = new byte[32]
            SECURE_RANDOM.nextBytes(randomBytes)

            apiKey = Base64.urlEncoder
                    .withoutPadding()
                    .encodeToString(randomBytes)
        } while (Merchant.findByApiKey(apiKey))

        apiKey
    }
}
