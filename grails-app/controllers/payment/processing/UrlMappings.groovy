package payment.processing

class UrlMappings {

    static mappings = {

        "/api/merchants"(controller: "merchant") {
            action = [
                    POST: "save"
            ]
        }

        "/api/payments"(controller: "payment") {
            action = [
                    POST: "save",
                    GET : "index"
            ]
        }

        "/api/payments/$reference/capture"(controller: "payment") {
            action = [
                    POST: "capture"
            ]
        }

        "/api/payments/$reference/refund"(controller: "payment") {
            action = [
                    POST: "refund"
            ]
        }

        "/api/payments/$reference"(controller: "payment") {
            action = [
                    GET: "show"
            ]
        }

        "/"(controller: 'application', action: 'index')
        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
