package com.mike.cli

import com.mike.tuya.config.TuyaConfig
import com.mike.tuya.service.SmartMeterService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.system.exitProcess

/**
 * Command-line interface that replicates the functionality of the Python scripts
 * Usage:
 *   - list: List all meters
 *   - show <device_id>: Show device details  
 *   - add-balance <device_id> [amount]: Add balance to meter
 *   - set-reading <device_id> <reading>: Set current reading
 *   - set-units <device_id> <units>: Set units remaining
 *   - set-battery <device_id> <percentage>: Set battery percentage
 *   - check-cloud <device_id>: Check cloud status
 */
class TuyaCLI(private val config: TuyaConfig) {
    
    private val service = SmartMeterService(
        accessId = config.accessId,
        accessSecret = config.accessSecret,
        endpoint = config.endpoint,
        projectCode = config.projectCode
    )
    
    suspend fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            showUsage()
            return
        }
        
        val connected = service.connect()
        if (!connected) {
            println("❌ Failed to connect to Tuya Cloud")
            println("Please check your ACCESS_ID and ACCESS_SECRET credentials")
            exitProcess(1)
        }
        
        when (args[0].lowercase()) {
            "list" -> listMeters()
            "show" -> {
                if (args.size < 2) {
                    println("❌ Usage: show <device_id>")
                    exitProcess(1)
                }
                showDevice(args[1])
            }
            "add-balance" -> {
                if (args.size < 2) {
                    println("❌ Usage: add-balance <device_id> [amount]")
                    exitProcess(1)
                }
                val deviceId = args[1]
                val amount = if (args.size > 2) args[2].toDoubleOrNull() else null
                addBalance(deviceId, amount)
            }
            "set-reading" -> {
                if (args.size < 3) {
                    println("❌ Usage: set-reading <device_id> <reading>")
                    exitProcess(1)
                }
                val deviceId = args[1]
                val reading = args[2].toDoubleOrNull()
                if (reading == null) {
                    println("❌ Invalid reading value: ${args[2]}")
                    exitProcess(1)
                }
                setCurrentReading(deviceId, reading)
            }
            "set-units" -> {
                if (args.size < 3) {
                    println("❌ Usage: set-units <device_id> <units>")
                    exitProcess(1)
                }
                val deviceId = args[1]
                val units = args[2].toIntOrNull()
                if (units == null) {
                    println("❌ Invalid units value: ${args[2]}")
                    exitProcess(1)
                }
                setUnitsRemaining(deviceId, units)
            }
            "set-battery" -> {
                if (args.size < 3) {
                    println("❌ Usage: set-battery <device_id> <percentage>")
                    exitProcess(1)
                }
                val deviceId = args[1]
                val percentage = args[2].toIntOrNull()
                if (percentage == null || percentage < 0 || percentage > 100) {
                    println("❌ Invalid battery percentage: ${args[2]} (must be 0-100)")
                    exitProcess(1)
                }
                setBattery(deviceId, percentage)
            }
            "check-cloud" -> {
                if (args.size < 2) {
                    println("❌ Usage: check-cloud <device_id>")
                    exitProcess(1)
                }
                checkCloudStatus(args[1])
            }
            else -> {
                println("❌ Unknown command: ${args[0]}")
                showUsage()
                exitProcess(1)
            }
        }
    }
    
    private suspend fun listMeters() {
        println("\n📱 METERS IN TUYA CLOUD:")
        println("=" * 70)
        
        val meters = service.listAllMeters()
        
        if (meters.isEmpty()) {
            println("No meters found in your Tuya Cloud account.")
            return
        }
        
        println("Found ${meters.size} meter(s):\n")
        
        meters.forEachIndexed { index, meter ->
            val online = if (meter.online) "🟢 Online" else "🔴 Offline"
            println("${index + 1}. ${meter.name ?: "Unnamed Meter"} [${meter.id}]")
            println("   Product: ${meter.productName ?: "Unknown Product"}")
            println("   Status: $online")
            println()
        }
        
        if (meters.isNotEmpty()) {
            val meterId = meters.first().id
            println("Try these commands:")
            println("  show $meterId")
            println("  add-balance $meterId")
            println("  check-cloud $meterId")
        }
    }
    
    private suspend fun showDevice(deviceId: String) {
        println("\n📊 METER DETAILS:")
        println("=" * 70)
        
        val deviceInfo = service.getDeviceDetails(deviceId)
        if (deviceInfo == null) {
            println("Meter $deviceId not found.")
            return
        }
        
        val device = deviceInfo.device
        val online = if (device.online) "🟢 Online" else "🔴 Offline"
        
        println("Meter: ${device.name ?: "Unnamed Meter"}")
        println("ID: ${device.id}")
        println("Product: ${device.productName ?: "Unknown Product"}")
        println("Model: ${device.model ?: "Unknown Model"}")
        println("Status: $online")
        println("IP: ${device.ip ?: "Unknown"}")
        
        device.activeTime?.let { activeTime ->
            val instant = Instant.fromEpochMilliseconds(activeTime)
            println("First Active: $instant")
        }
        
        println("\n📈 DATA POINTS:")
        println("-" * 70)
        
        if (deviceInfo.status.isNotEmpty()) {
            deviceInfo.status.forEach { dp ->
                when (dp.code) {
                    "current_reading" -> {
                        val value = dp.value?.toDoubleOrNull()?.div(100)
                        println("Current Reading: ${value?.let { "%.2f kWh".format(it) } ?: "Unknown"}")
                    }
                    "units_remaining" -> {
                        println("Units Remaining: ${dp.value} kWh")
                    }
                    "battery_percentage" -> {
                        println("Battery Level: ${dp.value}%")
                    }
                    "device_status" -> {
                        println("Meter Status: ${dp.value}")
                    }
                    else -> {
                        println("${dp.code}: ${dp.value}")
                    }
                }
            }
            
            deviceInfo.summary?.let { summary ->
                println("\n💡 SMART METER SUMMARY:")
                println("-" * 70)
                
                summary.totalConsumption?.let { consumption ->
                    println("⚡ Total Consumption: %.2f kWh".format(consumption))
                }
                
                summary.creditRemaining?.let { credit ->
                    println("💰 Credit Remaining: $credit kWh")
                    when {
                        credit < 20 -> println("⚠️ WARNING: Very low credit!")
                        credit < 50 -> println("📉 Low credit")
                        else -> println("✅ Credit level good")
                    }
                }
                
                summary.batteryLevel?.let { battery ->
                    println("🔋 Battery: $battery%")
                    when {
                        battery < 20 -> println("⚠️ Battery critically low!")
                        battery < 50 -> println("📉 Battery low")
                        else -> println("✅ Battery level good")
                    }
                }
            }
        } else {
            println("No data points reported by this meter")
            println("This is normal for virtual meters without real hardware")
        }
        
        deviceInfo.specifications?.let { specs ->
            println("\n⚙️ METER SPECIFICATIONS:")
            println("-" * 70)
            specs.functions?.forEach { func ->
                println("Function: ${func.code} (${func.type})")
                func.values?.let { values ->
                    println("  Values: $values")
                }
            }
        }
    }
    
    private suspend fun addBalance(deviceId: String, amount: Double?) {
        println("\n💰 ADDING BALANCE:")
        println("=" * 70)
        
        val amountStr = amount?.let { "$it units" } ?: "default amount"
        println("Adding $amountStr to meter [$deviceId]...")
        
        val result = service.addBalance(deviceId, amount)
        
        if (result.success) {
            println("✅ ${result.message}")
            println("Note: Virtual meters may not actually update their balance")
        } else {
            println("❌ ${result.message}")
        }
    }
    
    private suspend fun setCurrentReading(deviceId: String, reading: Double) {
        println("\n⚡ SETTING CURRENT READING:")
        println("=" * 70)
        println("Setting current reading to $reading kWh for meter [$deviceId]...")
        
        val result = service.setCurrentReading(deviceId, reading)
        
        if (result.success) {
            println("✅ ${result.message}")
        } else {
            println("❌ ${result.message}")
        }
    }
    
    private suspend fun setUnitsRemaining(deviceId: String, units: Int) {
        println("\n💳 SETTING UNITS REMAINING:")
        println("=" * 70)
        println("Setting units remaining to $units kWh for meter [$deviceId]...")
        
        val result = service.setUnitsRemaining(deviceId, units)
        
        if (result.success) {
            println("✅ ${result.message}")
        } else {
            println("❌ ${result.message}")
        }
    }
    
    private suspend fun setBattery(deviceId: String, percentage: Int) {
        println("\n🔋 SETTING BATTERY PERCENTAGE:")
        println("=" * 70)
        println("Setting battery percentage to $percentage% for meter [$deviceId]...")
        
        val result = service.setBattery(deviceId, percentage)
        
        if (result.success) {
            println("✅ ${result.message}")
        } else {
            println("❌ ${result.message}")
        }
    }
    
    private suspend fun checkCloudStatus(deviceId: String) {
        println("\n🌐 TUYA CLOUD STATUS CHECKER")
        println("=" * 60)
        println("This tool shows ONLY real data from Tuya Cloud, no simulation\n")
        
        val deviceInfo = service.getDeviceDetails(deviceId)
        if (deviceInfo == null) {
            println("❌ Meter $deviceId not found")
            return
        }
        
        println("✅ Successfully connected to Tuya Cloud")
        
        val device = deviceInfo.device
        
        println("\n📋 METER INFORMATION FROM CLOUD:")
        println("-" * 40)
        println("Name: ${device.name}")
        println("ID: ${device.id}")
        println("Product: ${device.productName}")
        println("Online: ${device.online}")
        println("Category: ${device.category ?: "Unknown"}")
        println("IP: ${device.ip}")
        
        println("\n📊 METER STATUS FROM CLOUD:")
        println("-" * 40)
        
        if (deviceInfo.status.isNotEmpty()) {
            println("Data points reported by meter:")
            deviceInfo.status.forEach { dp ->
                println("  • ${dp.code}: ${dp.value}")
            }
        } else {
            println("⚠️ No data points reported by this meter")
            println("   This is normal for virtual meters that don't have real hardware")
        }
        
        println("\n🔍 SUMMARY:")
        println("-" * 40)
        if (deviceInfo.status.isEmpty()) {
            println("📢 This virtual meter has no real data points in the cloud")
            println("   This is expected behavior for virtual meters")
            println("   When you connect real hardware, data will appear here")
        } else {
            println("✅ Meter is reporting real data to the cloud!")
        }
        
        println("\n📋 NEXT STEPS:")
        println("1. Use the Ktor API for programmatic access")
        println("2. Check this status again after connecting real hardware")  
        println("3. View meter in Tuya IoT Platform: https://iot.tuya.com")
        println("=" * 60)
    }
    
    private fun showUsage() {
        println("\n🛠️ TUYA SMART METER CLI")
        println("=" * 70)
        println("Usage: ./tuya-cli <command> [arguments]")
        println()
        println("Commands:")
        println("  list                     - List all meters in Tuya Cloud")
        println("  show <meter_id>          - Show detailed meter information")
        println("  add-balance <meter_id> [amount] - Add balance to meter")
        println("  set-reading <meter_id> <reading> - Set current reading (kWh)")
        println("  set-units <meter_id> <units>     - Set units remaining")
        println("  set-battery <meter_id> <percentage> - Set battery percentage (0-100)")
        println("  check-cloud <meter_id>   - Check real cloud status")
        println()
        println("Examples:")
        println("  ./tuya-cli list")
        println("  ./tuya-cli show bf1234567890abcdef")
        println("  ./tuya-cli add-balance bf1234567890abcdef 100")
        println("  ./tuya-cli set-reading bf1234567890abcdef 150.5")
        println("  ./tuya-cli set-units bf1234567890abcdef 50")
        println("  ./tuya-cli set-battery bf1234567890abcdef 85")
        println("=" * 70)
    }
    
    fun close() {
        service.close()
    }
}

// Extension function to replicate Python's string multiplication
private operator fun String.times(n: Int): String = this.repeat(n)

// Main function for CLI usage
fun main(args: Array<String>) = runBlocking {
    // Load environment variables from .env file
    val dotenv = io.github.cdimascio.dotenv.dotenv {
        ignoreIfMissing = true
        directory = "."
    }
    
    val config = TuyaConfig(
        accessId = dotenv["ACCESS_ID"] ?: System.getenv("ACCESS_ID") ?: run {
            println("❌ ACCESS_ID environment variable is required")
            exitProcess(1)
        },
        accessSecret = dotenv["ACCESS_SECRET"] ?: System.getenv("ACCESS_SECRET") ?: run {
            println("❌ ACCESS_SECRET environment variable is required")
            exitProcess(1)
        },
        endpoint = dotenv["TUYA_ENDPOINT"] ?: System.getenv("TUYA_ENDPOINT") ?: "https://openapi.tuyaeu.com",
        projectCode = dotenv["PROJECT_CODE"] ?: System.getenv("PROJECT_CODE"),
        deviceId = dotenv["DEVICE_ID"] ?: System.getenv("DEVICE_ID")
    )
    
    val cli = TuyaCLI(config)
    
    try {
        cli.execute(args)
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    } finally {
        cli.close()
    }
}
