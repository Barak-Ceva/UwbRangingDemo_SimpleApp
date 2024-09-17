package com.example.uwbdemoapp;

public class Utils {
    public static String convertBytesToHexLittleEndian(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; i--) {
            if (i < bytes.length - 1) {
                hexString.append(":");
            }
            hexString.append(String.format("%02X", bytes[i]));
        }
        return hexString.toString();
    }
}
