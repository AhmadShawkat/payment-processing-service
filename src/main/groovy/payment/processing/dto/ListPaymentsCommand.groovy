package payment.processing.dto

import grails.validation.Validateable
import payment.processing.PaymentStatus

class ListPaymentsCommand implements Validateable {

    PaymentStatus status
    Date fromDate
    Date toDate
    Integer max = 10
    Integer offset = 0

    static constraints = {
        status nullable: true
        fromDate nullable: true, validator: { Date value, ListPaymentsCommand command ->
            if (value && command.toDate && value.after(command.toDate)) {
                return 'dateRange.invalid'
            }
        }
        toDate nullable: true
        max range: 1..100
        offset min: 0
    }
}
