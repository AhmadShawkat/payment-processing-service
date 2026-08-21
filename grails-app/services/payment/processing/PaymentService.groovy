package payment.processing

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import payment.processing.api.ApiError
import payment.processing.api.ApiException
import payment.processing.dto.CreatePaymentCommand
import payment.processing.dto.ListPaymentsCommand
import payment.processing.dto.PaymentPageResponse
import payment.processing.dto.PaymentResponse

@Transactional
class PaymentService {

    MerchantService merchantService
    RequestValidationService requestValidationService

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

        PaymentTransaction payment = findOwnedPayment(reference, merchant, true)

        // TODO : ***
        // unique ( merchant , reference)

        //change the lock to optmestice and help
        if (payment.status != PaymentStatus.PENDING) {
            ApiError error = payment.status == PaymentStatus.SUCCESS
                    ? ApiError.PAYMENT_ALREADY_CAPTURED
                    : ApiError.CAPTURE_REQUIRES_PENDING
            throw new ApiException(error)
        }

        payment.status = PaymentStatus.SUCCESS



        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    PaymentResponse refund(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)
        PaymentTransaction payment = findOwnedPayment(reference, merchant, true)

        if (payment.status != PaymentStatus.SUCCESS) {
            ApiError error = payment.status == PaymentStatus.REFUNDED
                    ? ApiError.PAYMENT_ALREADY_REFUNDED
                    : ApiError.REFUND_REQUIRES_SUCCESS
            throw new ApiException(error)
        }

        payment.status = PaymentStatus.REFUNDED
        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    @ReadOnly
    PaymentResponse get(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)
        PaymentResponse.from(findOwnedPayment(reference, merchant, false))
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
            Merchant merchant,
            boolean lock
    ) {
        if (!reference?.trim()) {
            throw new ApiException(ApiError.PAYMENT_REFERENCE_REQUIRED)
        }

        PaymentTransaction payment = PaymentTransaction.findByReferenceAndMerchant(
                reference.trim(),
                merchant,
                [lock: lock]
        )

        if (!payment) {
            throw new ApiException(ApiError.PAYMENT_NOT_FOUND)
        }

        payment
    }
}
