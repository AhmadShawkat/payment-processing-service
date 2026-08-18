package payment.processing

import grails.testing.mixin.integration.Integration
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.http.MediaType
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@Integration
class PaymentApiSpec extends Specification {

    private static final String API_KEY_HEADER = 'X-API-KEY'

    void cleanup() {
        PaymentTransaction.withNewTransaction {
            PaymentTransaction.executeUpdate('delete from PaymentTransaction')
            Merchant.executeUpdate('delete from Merchant')
        }
    }

    void 'POST merchants creates a normalized merchant and generated API key'() {
        when:
        ApiResult result = postJson('/api/merchants', [
                name : '  Test Store  ',
                email: '  STORE@Test.COM  '
        ])
        Map response = json(result)

        then:
        result.status == 201
        result.contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)
        response.id instanceof Number
        response.name == 'Test Store'
        response.email == 'store@test.com'
        response.apiKey instanceof String
        response.apiKey.size() >= 32

        and: 'the merchant is persisted with the expected defaults'
        Map persisted = merchantSnapshot(response.id as Long)
        persisted.name == 'Test Store'
        persisted.email == 'store@test.com'
        persisted.apiKey == response.apiKey
        persisted.active
        persisted.dateCreated
        persisted.lastUpdated
    }

    void 'merchant validation and duplicate email return stable API errors'() {
        given:
        postJson('/api/merchants', [name: 'First Store', email: 'same@test.com'])

        when:
        ApiResult invalid = postJson('/api/merchants', [name: '', email: 'not-an-email'])

        then:
        invalid.status == 400
        json(invalid).errorCode == '02'
        json(invalid).error

        when:
        ApiResult duplicate = postJson('/api/merchants', [
                name : 'Second Store',
                email: ' SAME@test.com '
        ])

        then:
        duplicate.status == 409
        json(duplicate) == [
                errorCode: '04',
                error    : 'A merchant with this email already exists'
        ]
    }

    void 'payment endpoints require a valid active merchant API key'() {
        when:
        ApiResult missingKey = getRequest('/api/payments')

        then:
        missingKey.status == 401
        json(missingKey) == [errorCode: '01', error: 'API key is required']

        when:
        ApiResult unknownKey = getRequest('/api/payments', 'unknown-key')

        then:
        unknownKey.status == 401
        json(unknownKey) == [errorCode: '01', error: 'Active merchant not found']

        when:
        Map merchant = createMerchant('Inactive Store', 'inactive@test.com')
        Merchant.withNewTransaction {
            Merchant storedMerchant = Merchant.get(merchant.id as Long)
            storedMerchant.active = false
            storedMerchant.save(failOnError: true, flush: true)
        }
        ApiResult inactiveKey = getRequest('/api/payments', merchant.apiKey as String)

        then:
        inactiveKey.status == 401
        json(inactiveKey) == [errorCode: '01', error: 'Active merchant not found']
    }

    void 'payment can be created, retrieved, captured, and refunded'() {
        given:
        Map merchant = createMerchant('Lifecycle Store', 'lifecycle@test.com')
        String apiKey = merchant.apiKey

        when:
        ApiResult createdResult = createPayment(apiKey, [
                reference  : ' INV-10001 ',
                amount     : 120.50,
                currency   : ' usd ',
                description: ' Order payment '
        ])
        Map created = json(createdResult)

        then:
        createdResult.status == 201
        created.reference == 'INV-10001'
        created.amount == 120.50
        created.currency == 'USD'
        created.description == 'Order payment'
        created.status == 'PENDING'
        created.merchantId == merchant.id
        created.dateCreated
        created.lastUpdated

        when:
        ApiResult detailsResult = getRequest('/api/payments/INV-10001', apiKey)
        Map details = json(detailsResult)

        then:
        detailsResult.status == 200
        details.id == created.id
        details.status == 'PENDING'

        when:
        ApiResult capturedResult = postRequest('/api/payments/INV-10001/capture', apiKey)

        then:
        capturedResult.status == 200
        json(capturedResult).status == 'SUCCESS'
        paymentStatus('INV-10001') == PaymentStatus.SUCCESS

        when:
        ApiResult refundedResult = postRequest('/api/payments/INV-10001/refund', apiKey)

        then:
        refundedResult.status == 200
        json(refundedResult).status == 'REFUNDED'
        paymentStatus('INV-10001') == PaymentStatus.REFUNDED
    }

    void 'payment validation rejects non-positive amounts and duplicate references'() {
        given:
        String apiKey = createMerchant('Validation Store', 'validation@test.com').apiKey

        when:
        ApiResult invalid = createPayment(apiKey, [
                reference: 'INV-BAD',
                amount   : 0,
                currency : 'USD'
        ])

        then:
        invalid.status == 400
        json(invalid).errorCode == '02'
        json(invalid).error

        when:
        ApiResult first = createPayment(apiKey, paymentBody('INV-DUPLICATE', 10))
        ApiResult duplicate = createPayment(apiKey, paymentBody('INV-DUPLICATE', 20))

        then:
        first.status == 201
        duplicate.status == 409
        json(duplicate) == [
                errorCode: '04',
                error    : 'A payment with this reference already exists'
        ]
    }

    void 'invalid status transitions and cross-merchant access are rejected'() {
        given:
        Map owner = createMerchant('Owner Store', 'owner@test.com')
        Map other = createMerchant('Other Store', 'other@test.com')
        createPayment(owner.apiKey as String, paymentBody('INV-STATE', 50))

        when: 'a pending payment is refunded'
        ApiResult pendingRefund = postRequest(
                '/api/payments/INV-STATE/refund',
                owner.apiKey as String
        )

        then:
        pendingRefund.status == 409
        json(pendingRefund) == [
                errorCode: '10',
                error    : 'Only successful payments can be refunded'
        ]

        when: 'a payment is captured twice'
        postRequest('/api/payments/INV-STATE/capture', owner.apiKey as String)
        ApiResult secondCapture = postRequest(
                '/api/payments/INV-STATE/capture',
                owner.apiKey as String
        )

        then:
        secondCapture.status == 409
        json(secondCapture) == [errorCode: '10', error: 'Payment already captured']

        when: 'another merchant tries to retrieve the payment'
        ApiResult wrongOwner = getRequest(
                '/api/payments/INV-STATE',
                other.apiKey as String
        )

        then:
        wrongOwner.status == 404
        json(wrongOwner) == [errorCode: '03', error: 'Payment not found']
    }

    void 'payment listing is merchant-scoped and supports status and pagination filters'() {
        given:
        Map merchant = createMerchant('List Store', 'list@test.com')
        Map other = createMerchant('Hidden Store', 'hidden@test.com')
        String apiKey = merchant.apiKey

        createPayment(apiKey, paymentBody('LIST-1', 10))
        createPayment(apiKey, paymentBody('LIST-2', 20))
        createPayment(apiKey, paymentBody('LIST-3', 30))
        postRequest('/api/payments/LIST-1/capture', apiKey)
        createPayment(other.apiKey as String, paymentBody('HIDDEN-1', 99))

        when:
        ApiResult successfulResult = getRequest(
                '/api/payments',
                apiKey,
                [status: 'SUCCESS']
        )
        Map successful = json(successfulResult)

        then:
        successfulResult.status == 200
        successful.total == 1
        successful.payments*.reference == ['LIST-1']
        successful.payments*.status == ['SUCCESS']

        when:
        ApiResult pageResult = getRequest(
                '/api/payments',
                apiKey,
                [max: '2', offset: '1']
        )
        Map page = json(pageResult)

        then:
        pageResult.status == 200
        page.max == 2
        page.offset == 1
        page.total == 3
        page.payments.size() == 2
        !page.payments*.reference.contains('HIDDEN-1')

        when:
        ApiResult invalidPage = getRequest('/api/payments', apiKey, [max: '101'])

        then:
        invalidPage.status == 400
        json(invalidPage).errorCode == '02'
        json(invalidPage).error
    }

    private Map createMerchant(String name, String email) {
        ApiResult result = postJson('/api/merchants', [name: name, email: email])
        assert result.status == 201
        json(result)
    }

    private ApiResult createPayment(String apiKey, Map body) {
        postJson('/api/payments', body, apiKey)
    }

    private ApiResult postJson(String path, Map body, String apiKey = null) {
        request('POST', path, apiKey, body)
    }

    private ApiResult postRequest(String path, String apiKey) {
        request('POST', path, apiKey)
    }

    private ApiResult getRequest(
            String path,
            String apiKey = null,
            Map<String, String> parameters = [:]
    ) {
        request('GET', path, apiKey, null, parameters)
    }

    private ApiResult request(
            String method,
            String path,
            String apiKey,
            Map body = null,
            Map<String, String> parameters = [:]
    ) {
        String query = parameters.collect { String name, String value ->
            "${URLEncoder.encode(name, 'UTF-8')}=${URLEncoder.encode(value, 'UTF-8')}"
        }.join('&')
        String requestPath = query ? "${path}?${query}" : path

        HttpURLConnection connection = new URL(
                "http://localhost:${serverPort}${requestPath}"
        ).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty('Accept', MediaType.APPLICATION_JSON_VALUE)
        connection.connectTimeout = 5000
        connection.readTimeout = 10000

        if (apiKey != null) {
            connection.setRequestProperty(API_KEY_HEADER, apiKey)
        }
        if (body != null) {
            byte[] payload = JsonOutput.toJson(body).getBytes(StandardCharsets.UTF_8)
            connection.doOutput = true
            connection.setRequestProperty('Content-Type', MediaType.APPLICATION_JSON_VALUE)
            connection.setFixedLengthStreamingMode(payload.length)
            connection.outputStream.withCloseable { output ->
                output.write(payload)
            }
        }

        int status = connection.responseCode
        InputStream responseStream = status >= 400
                ? connection.errorStream
                : connection.inputStream
        String responseBody = responseStream?.getText(StandardCharsets.UTF_8.name()) ?: ''

        new ApiResult(
                status: status,
                contentType: connection.contentType,
                body: responseBody
        )
    }

    private static Map json(ApiResult result) {
        new JsonSlurper().parseText(result.body) as Map
    }

    private static Map paymentBody(String reference, Number amount) {
        [reference: reference, amount: amount, currency: 'USD']
    }

    private static Map merchantSnapshot(Long id) {
        Merchant.withNewTransaction {
            Merchant merchant = Merchant.get(id)
            [
                    name       : merchant.name,
                    email      : merchant.email,
                    apiKey     : merchant.apiKey,
                    active     : merchant.active,
                    dateCreated: merchant.dateCreated,
                    lastUpdated: merchant.lastUpdated
            ]
        }
    }

    private static PaymentStatus paymentStatus(String reference) {
        PaymentTransaction.withNewTransaction {
            PaymentTransaction.findByReference(reference).status
        }
    }

    private static class ApiResult {
        int status
        String contentType
        String body
    }
}
