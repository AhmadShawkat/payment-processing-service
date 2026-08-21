package payment.processing

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import payment.processing.api.ApiError
import payment.processing.api.ApiException
import payment.processing.dto.PaymentResponse

class PaymentStateTransitionService {

    @Transactional
    PaymentResponse capture(Long merchantId, String reference) {
        PaymentTransaction payment = requireOwnedPayment(merchantId, reference)

        if (payment.status == PaymentStatus.SUCCESS) {
            return PaymentResponse.from(payment)
        }

        if (payment.status != PaymentStatus.PENDING) {
            throw new ApiException(ApiError.CAPTURE_REQUIRES_PENDING)
        }

        payment.status = PaymentStatus.SUCCESS
        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    @ReadOnly
    PaymentResponse resolveCaptureConflict(Long merchantId, String reference) {
        PaymentTransaction payment = requireOwnedPayment(merchantId, reference)

        if (payment.status == PaymentStatus.SUCCESS) {
            return PaymentResponse.from(payment)
        }

        throw new ApiException(ApiError.CAPTURE_REQUIRES_PENDING)
    }

    @Transactional
    PaymentResponse refund(Long merchantId, String reference) {
        PaymentTransaction payment = requireOwnedPayment(merchantId, reference)

        if (payment.status == PaymentStatus.REFUNDED) {
            throw new ApiException(ApiError.PAYMENT_ALREADY_REFUNDED)
        }

        if (payment.status != PaymentStatus.SUCCESS) {
            throw new ApiException(ApiError.REFUND_REQUIRES_SUCCESS)
        }

        payment.status = PaymentStatus.REFUNDED
        payment.save(failOnError: true, flush: true)

        PaymentResponse.from(payment)
    }

    @ReadOnly
    PaymentResponse resolveRefundConflict(Long merchantId, String reference) {
        PaymentTransaction payment = requireOwnedPayment(merchantId, reference)

        if (payment.status == PaymentStatus.REFUNDED) {
            throw new ApiException(ApiError.PAYMENT_ALREADY_REFUNDED)
        }

        throw new ApiException(ApiError.REFUND_REQUIRES_SUCCESS)
    }

    private static PaymentTransaction requireOwnedPayment(
            Long merchantId,
            String reference
    ) {
        if (!reference?.trim()) {
            throw new ApiException(ApiError.PAYMENT_REFERENCE_REQUIRED)
        }

        Merchant merchant = Merchant.get(merchantId)
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
