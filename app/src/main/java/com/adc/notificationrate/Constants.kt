package com.adc.notificationrate

import java.util.*

object Constants {

    val ChannelIdFgService = "fgServiceChannelId"

    val NotificationIdFgService = 101

    val ChannelIdTestLimit = "testLimitChannelId"

    val NotificationIdTestLimit = 100

    val REQ_CODE_PERMISSION_LOCATION = 1000

    val REQ_CODE_ENABLE_BT = 1001

    //Service UUID in the advertising data
    var ADC_SENSOR_SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    // Characteristic to write on the sensor
    var SENSOR_INPUT_CHAR = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    // Characteristic to read or get notified of changes in the sensor data
    var SENSOR_OUTPUT_CHAR = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
    var SENSOR_OUTPUT_CLIENT_CONF_DESC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /*

    STANDARD BASE_UUID:	0000[0000]-0000-1000-8000-00805F9B34FB
    [XXXX]: 16 bit UUID short offset
      _ _ _ _ _           _ _ _ _ _
        Phone |           | Sensor
              |           |
    <- onRead |- Rx - - - | SENSOR_OUTPUT_CHAR
              |           |
     Write -> |- Tx - - - | SENSOR_INPUT_CHAR
      _ _ _ _ |           |_ _ _ _ _

    */

}