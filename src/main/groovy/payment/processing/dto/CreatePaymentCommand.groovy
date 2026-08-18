package payment.processing.dto

import grails.validation.Validateable

class CreatePaymentCommand implements Validateable {

    String reference
    BigDecimal amount
    String currency
    String description

    static constraints = {
        reference blank: false
        amount nullable: false, min: 0.01G
        currency blank: false
        description nullable: true
    }
}
