package payment.processing.dto

import payment.processing.PaymentTransaction

class PaymentResponse {

    Long id
    String reference
    BigDecimal amount
    String currency
    String description
    String status
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
                status: payment.status.name(),
                merchantId: payment.merchant.id,
                dateCreated: payment.dateCreated,
                lastUpdated: payment.lastUpdated
        )
    }
}
