# Tuya Smart Meter Integration System

A backend system for managing Tuya Smart Meters, user accounts, meter assignments, and payment processing via M-Pesa.

## Features

- Smart meter management via Tuya Cloud API
- User management and authentication
- Meter payment processing
- M-Pesa integration
- Billing and usage tracking
- Device assignments

## Documentation

For detailed information about specific features, see the following documentation:

- [Meter Billing Integration](meter_billing_integration.md)
- [M-Pesa Integration](mpesa_tuya_integration.md)
- [Device API Documentation](device_api_documentation.md)

## Getting Started

1. Clone this repository
2. Configure application.yaml with your Tuya Cloud and M-Pesa credentials
3. Run the application:

```bash
./gradlew run
```

## API Endpoints

- `/devices/*` - Smart meter management
- `/users/*` - User management
- `/mpesa/*` - M-Pesa integration

## Technology Stack

- Kotlin
- Ktor (web framework)
- Exposed (SQL framework)
- Tuya Cloud API
- M-Pesa API
