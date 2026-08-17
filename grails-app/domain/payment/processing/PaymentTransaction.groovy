package payment.processing

class PaymentTransaction {

    String reference
    BigDecimal amount
    String currency
    String description
    PaymentStatus status = PaymentStatus.PENDING

    Date dateCreated
    Date lastUpdated

    static belongsTo = [merchant: Merchant]

    static constraints = {
        reference blank: false, unique: true
        amount nullable: false, validator: { value ->
            if (value != null && value <= 0) {
                return 'amount.invalid'
            }
        }
        currency blank: false
        description nullable: true
        status nullable: false
    }
}