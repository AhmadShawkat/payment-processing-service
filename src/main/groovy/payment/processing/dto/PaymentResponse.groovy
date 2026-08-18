package payment.processing.dto

import payment.processing.PaymentStatus
import payment.processing.PaymentTransaction

class PaymentResponse {

    Long id
    String reference
    BigDecimal amount
    String currency
    String description
    PaymentStatus status
    Long merchantId
    Date dateCreated
    Date lastUpdated

    static PaymentResponse from(PaymentTransaction payment) {
        new PaymentResponse(
                id: payment.id,
                reference: payment.reference,
                amount: payment.amount,
                currency: payment.currency,
                description: payment.description,
                status: payment.status,
                merchantId: payment.merchant.id,
                dateCreated: payment.dateCreated,
                lastUpdated: payment.lastUpdated
        )
    }
}
