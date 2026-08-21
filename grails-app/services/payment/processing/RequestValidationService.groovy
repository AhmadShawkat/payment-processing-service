package payment.processing

import grails.validation.Validateable
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import payment.processing.api.ApiError
import payment.processing.api.ApiException

class RequestValidationService {

    MessageSource messageSource

    void validate(Validateable command, ApiError missingRequestError) {
        if (command == null) {
            throw new ApiException(missingRequestError)
        }

        if (!command.validate()) {
            List<String> messages = command.errors.allErrors.collect { error ->
                messageSource.getMessage(error, LocaleContextHolder.locale)
            }

            throw new ApiException(
                    ApiError.VALIDATION_FAILED,
                    messages.unique().join(', ')
            )
        }
    }
}
