package com.exam.nearby;

import androidx.annotation.ColorRes;

public class Contact {
    private String name;
    private String endPointId;
    private @ColorRes int color;
    private boolean checked = false;

    public Contact(String name, String endPointId) {
        this.name = name;
        this.endPointId = endPointId;
    }


    public String getName() { return name; }
    public String getEndPointId() { return endPointId; }
    public @ColorRes int getColor() { return color; }
    public boolean isChecked() { return checked; }

    public void setColor(@ColorRes int color) {
        this.color = color;
    }
    public void setChecked(boolean checked) {
        this.checked = checked;
    }
    public void toggleChecked() {
        checked = !checked;
    }

    public boolean isSame(Contact other) {
        return this.name.equals(other.name) && this.endPointId.equals(other.endPointId);
    }
}
