package com.satmeter.app;

/** One snapshot of what the receiver reports about the selected satellite / transponder. */
public class Reading {
    public int strength;      // 0-100
    public int quality;       // 0-100
    public boolean locked;
    public String satellite = "";
    public String tp = "";
}
