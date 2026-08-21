package payment.processing

import grails.validation.Validateable
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

class RequestValidationService {

    MessageSource messageSource

    void validate(Validateable command, String missingRequestMessage) {
        if (command == null) {
            throw new IllegalArgumentException(missingRequestMessage)
        }

        if (!command.validate()) {
            List<String> messages = command.errors.allErrors.collect { error ->
                messageSource.getMessage(error, LocaleContextHolder.locale)
            }

            throw new IllegalArgumentException(messages.unique().join(', '))
        }
    }
}
