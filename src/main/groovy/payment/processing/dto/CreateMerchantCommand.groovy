package payment.processing.dto

import grails.validation.Validateable

class CreateMerchantCommand implements Validateable {

    String name
    String email

    static constraints = {
        name blank: false
        email blank: false, email: true
    }
}
