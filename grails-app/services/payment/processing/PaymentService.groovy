package payment.processing

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.springframework.dao.OptimisticLockingFailureException
import payment.processing.api.ApiError
import payment.processing.api.ApiException
import payment.processing.dto.CreatePaymentCommand
import payment.processing.dto.ListPaymentsCommand
import payment.processing.dto.PaymentPageResponse
import payment.processing.dto.PaymentResponse

class PaymentService {

    MerchantService merchantService
    PaymentStateTransitionService paymentStateTransitionService
    RequestValidationService requestValidationService

    @Transactional
    PaymentResponse create(String apiKey, CreatePaymentCommand command) {
        requestValidationService.validate(command, ApiError.PAYMENT_REQUEST_REQUIRED)
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)

        String normalizedReference = command.reference.trim()
        if (PaymentTransaction.findByReference(normalizedReference)) {
            throw new ApiException(ApiError.PAYMENT_REFERENCE_EXISTS)
        }

        PaymentTransaction payment = new PaymentTransaction(
                reference: normalizedReference,
                amount: command.amount,
                currency: command.currency.trim().toUpperCase(Locale.ROOT),
                description: command.description?.trim(),
                status: PaymentStatus.PENDING,
                merchant: merchant
        )
        // TODO : signature

        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    PaymentResponse capture(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)

        try {
            return paymentStateTransitionService.capture(merchant.id, reference)
        } catch (OptimisticLockingFailureException ignored) {
            return paymentStateTransitionService.resolveCaptureConflict(
                    merchant.id,
                    reference
            )
        }
    }

    PaymentResponse refund(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)

        try {
            return paymentStateTransitionService.refund(merchant.id, reference)
        } catch (OptimisticLockingFailureException ignored) {
            return paymentStateTransitionService.resolveRefundConflict(
                    merchant.id,
                    reference
            )
        }
    }

    @ReadOnly
    PaymentResponse get(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)
        PaymentResponse.from(findOwnedPayment(reference, merchant))
    }

    @ReadOnly

    PaymentPageResponse list(String apiKey, ListPaymentsCommand command) {
        ListPaymentsCommand filters = command ?: new ListPaymentsCommand()
        requestValidationService.validate(filters, ApiError.PAYMENT_FILTERS_REQUIRED)
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)

        def result = PaymentTransaction.createCriteria().list(
                max: filters.max,
                offset: filters.offset
        ) {
            eq('merchant', merchant)

            if (filters.status) {
                eq('status', filters.status)
            }
            if (filters.fromDate) {
                ge('dateCreated', filters.fromDate)
            }
            if (filters.toDate) {
                le('dateCreated', filters.toDate)
            }

            // Add index
            order('dateCreated', 'desc')
        }

        new PaymentPageResponse(
                payments: result.collect { PaymentTransaction payment ->
                    PaymentResponse.from(payment)
                },
                max: filters.max,
                offset: filters.offset,
                total: result.totalCount as Long
        )
    }

    private static PaymentTransaction findOwnedPayment(
            String reference,
            Merchant merchant
    ) {
        if (!reference?.trim()) {
            throw new ApiException(ApiError.PAYMENT_REFERENCE_REQUIRED)
        }

        PaymentTransaction payment = PaymentTransaction.findByReferenceAndMerchant(
                reference.trim(),
                merchant
        )

        if (!payment) {
            throw new ApiException(ApiError.PAYMENT_NOT_FOUND)
        }

        payment
    }
}
