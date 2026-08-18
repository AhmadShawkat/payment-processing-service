package payment.processing

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import payment.processing.dto.CreatePaymentCommand
import payment.processing.dto.ListPaymentsCommand
import payment.processing.dto.PaymentPageResponse
import payment.processing.dto.PaymentResponse

@Transactional
class PaymentService {

    MerchantService merchantService

    PaymentResponse create(String apiKey, CreatePaymentCommand command) {
        validate(command)
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)

        String normalizedReference = command.reference.trim()
        if (PaymentTransaction.findByReference(normalizedReference)) {
            throw new IllegalStateException('A payment with this reference already exists')
        }

        PaymentTransaction payment = new PaymentTransaction(
                reference: normalizedReference,
                amount: command.amount,
                currency: command.currency.trim().toUpperCase(Locale.ROOT),
                description: command.description?.trim(),
                status: PaymentStatus.PENDING,
                merchant: merchant
        )

        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    PaymentResponse capture(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)
        PaymentTransaction payment = findOwnedPayment(reference, merchant, true)

        if (payment.status != PaymentStatus.PENDING) {
            String message = payment.status == PaymentStatus.SUCCESS
                    ? 'Payment already captured'
                    : 'Only pending payments can be captured'
            throw new IllegalStateException(message)
        }

        payment.status = PaymentStatus.SUCCESS
        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    PaymentResponse refund(String apiKey, String reference) {
        Merchant merchant = merchantService.requireActiveMerchant(apiKey)
        PaymentTransaction payment = findOwnedPayment(reference, merchant, true)

        if (payment.status != PaymentStatus.SUCCESS) {
            String message = payment.status == PaymentStatus.REFUNDED
                    ? 'Payment already refunded'
                    : 'Only successful payments can be refunded'
            throw new IllegalStateException(message)
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
        validate(filters)
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

    private static void validate(CreatePaymentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException('Payment request is required')
        }

        if (!command.validate()) {
            throw new IllegalArgumentException(
                    command.errors.allErrors*.defaultMessage.join(', ')
            )
        }
    }

    private static void validate(ListPaymentsCommand command) {
        if (!command.validate()) {
            throw new IllegalArgumentException(
                    command.errors.allErrors*.defaultMessage.join(', ')
            )
        }
    }

    private static PaymentTransaction findOwnedPayment(
            String reference,
            Merchant merchant,
            boolean lock
    ) {
        if (!reference?.trim()) {
            throw new IllegalArgumentException('Payment reference is required')
        }

        PaymentTransaction payment = PaymentTransaction.findByReferenceAndMerchant(
                reference.trim(),
                merchant,
                [lock: lock]
        )

        if (!payment) {
            throw new NoSuchElementException('Payment not found')
        }

        payment
    }
}
