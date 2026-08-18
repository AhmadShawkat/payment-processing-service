package payment.processing

class Merchant {

    String name
    String email
    String apiKey
    Boolean active = true

    Date dateCreated
    Date lastUpdated

    static hasMany = [paymentTransactions: PaymentTransaction]

    static constraints = {
        name blank: false
        email blank: false, unique: true, email: true
        apiKey blank: false, unique: true
    }
}