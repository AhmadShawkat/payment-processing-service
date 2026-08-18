package payment.processing.dto

import grails.validation.Validateable

class CreatePaymentCommand implements Validateable {

    String reference
    BigDecimal amount
    String currency
    String description

    static constraints = {
        reference blank: false
        amount nullable: false, validator: { value ->
            if (value != null && value <= 0) {
                return 'amount.invalid'
            }
        }
        currency blank: false
        description nullable: true
    }
}
