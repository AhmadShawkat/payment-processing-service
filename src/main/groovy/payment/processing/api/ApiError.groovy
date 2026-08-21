package payment.processing.api

import org.springframework.http.HttpStatus

enum ApiError {

    API_KEY_REQUIRED('01', HttpStatus.UNAUTHORIZED, 'API key is required'),
    ACTIVE_MERCHANT_NOT_FOUND('01', HttpStatus.UNAUTHORIZED, 'Active merchant not found'),

    VALIDATION_FAILED('02', HttpStatus.BAD_REQUEST, null),
    MERCHANT_REQUEST_REQUIRED('02', HttpStatus.BAD_REQUEST, 'Merchant request is required'),
    PAYMENT_REQUEST_REQUIRED('02', HttpStatus.BAD_REQUEST, 'Payment request is required'),
    PAYMENT_FILTERS_REQUIRED('02', HttpStatus.BAD_REQUEST, 'Payment filters are required'),
    PAYMENT_REFERENCE_REQUIRED('02', HttpStatus.BAD_REQUEST, 'Payment reference is required'),

    PAYMENT_NOT_FOUND('03', HttpStatus.NOT_FOUND, 'Payment not found'),

    MERCHANT_EMAIL_EXISTS('04', HttpStatus.CONFLICT, 'A merchant with this email already exists'),
    PAYMENT_REFERENCE_EXISTS('04', HttpStatus.CONFLICT, 'A payment with this reference already exists'),

    PAYMENT_ALREADY_CAPTURED('10', HttpStatus.CONFLICT, 'Payment already captured'),
    CAPTURE_REQUIRES_PENDING('10', HttpStatus.CONFLICT, 'Only pending payments can be captured'),
    PAYMENT_ALREADY_REFUNDED('10', HttpStatus.CONFLICT, 'Payment already refunded'),
    REFUND_REQUIRES_SUCCESS('10', HttpStatus.CONFLICT, 'Only successful payments can be refunded')

    final String errorCode
    final HttpStatus httpStatus
    final String message

    ApiError(String errorCode, HttpStatus httpStatus, String message) {
        this.errorCode = errorCode
        this.httpStatus = httpStatus
        this.message = message
    }
}
