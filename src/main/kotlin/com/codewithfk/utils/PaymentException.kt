package com.codewithfk.utils

sealed class PaymentException(message: String) : Exception(message) {
    class PaymentMethodRequired : PaymentException("Payment method is required")
    class PaymentAuthenticationRequired : PaymentException("Additional authentication required")
    class PaymentFailed(message: String) : PaymentException(message)
    class InvalidPaymentStatus(status: String) : PaymentException("Invalid payment status: $status")
    class UnauthorizedPaymentAccess : PaymentException("Unauthorized access to payment intent")
} 