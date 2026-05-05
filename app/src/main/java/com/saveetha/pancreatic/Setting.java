package com.saveetha.pancreatic;

public class Setting {
    public int iconResId;
    public String title;
    public Runnable action;

    public Setting(int iconResId, String title, Runnable action) {
        this.iconResId = iconResId;
        this.title = title;
        this.action = action;
    }
}
