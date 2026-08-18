package payment.processing

import payment.processing.dto.CreateMerchantCommand
import payment.processing.dto.MerchantResponse

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

import java.security.SecureRandom
import java.util.Base64

@Transactional
class MerchantService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom()

    RequestValidationService requestValidationService

    MerchantResponse create(CreateMerchantCommand command) {
        requestValidationService.validate(command, 'Merchant request is required')

        String normalizedEmail = command.email.trim().toLowerCase(Locale.ROOT)
        if (Merchant.findByEmail(normalizedEmail)) {
            throw new IllegalStateException('A merchant with this email already exists')
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
            throw new IllegalArgumentException('API key is required')
        }

        Merchant merchant = Merchant.findByApiKeyAndActive(apiKey.trim(), true)
        if (!merchant) {
            throw new NoSuchElementException('Active merchant not found')
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
