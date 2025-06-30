# Tuya Smart Meter API - Ktor Backend

A modern Kotlin/Ktor backend service for interacting with Tuya Cloud smart meters. This project provides both a REST API and command-line interface for managing smart meter devices.

## 🚀 Features

- ✅ **Connect to Tuya Cloud API** with proper authentication
- 📱 **Unified device management** - all devices are fetched from Tuya Cloud, cached in the database, and exposed via a single set of endpoints
- 📊 **Get detailed device information** including all data points
- 💰 **Add balance to meters** via API commands
- 🔧 **Send custom commands** to devices
- ⚡ **Set current readings, units, battery, and status**
- 🔐 **Built-in authentication system** with JWT
- 👥 **User-device assignments** - users are linked to specific devices
- 🔒 **Access control** - users can only access devices assigned to them (except admin)
- 🌐 **CORS support** for web frontends
- 📝 **Comprehensive error handling**
- 🖥️ **Command-line interface** for direct script usage

## 🛠️ Setup

### 1. Prerequisites
- Java 11 or higher
- Gradle (included via wrapper)

### 2. Environment Configuration

#### Setup
1. **Configure your Tuya credentials:**
   - Get your credentials from [Tuya IoT Platform](https://iot.tuya.com/)
   - Navigate to Cloud → Projects → Your Project → Overview
   - Copy your `Access ID` and `Access Secret`

2. **Make sure your `.env` file contains:**
   ```env
   # Tuya Cloud API Credentials
   ACCESS_ID=your_access_id
   ACCESS_SECRET=your_access_secret
   
   # API Configuration
   TUYA_ENDPOINT=https://openapi.tuyaeu.com
   
   # Device Configuration
   DEVICE_ID=your_device_id
   ```

> ⚠️ **Security Note:** The `.env` file contains sensitive credentials and should never be committed to version control. It's already included in `.gitignore`.

### 3. Build and Run
```bash
# Build the application
./gradlew build

# Run the REST API server
./gradlew run

# Or use the CLI directly
./gradlew cli -Pargs="list"
```

The server will start on `http://localhost:8080`

## 📡 API Endpoints

The API provides the following main endpoint groups:

- **Authentication**: `/auth/*` - Login and registration
- **Users**: `/users/*` - User management
- **Devices**: `/devices/*` - Device management with Tuya Cloud integration

For full API documentation, see the [API_DOCUMENTATION.md](API_DOCUMENTATION.md) file.

## 🧪 Testing

A test script is provided to test the main API endpoints:

```bash
# Make the script executable
chmod +x test_endpoints.sh

# Run the tests
./test_endpoints.sh
```
