package com.alpha4technologies.prayerclock;

import java.io.Serializable;

public class TasbihModel implements Serializable {

    public String id;
    public String name;
    public String content;
    public long count;
    public long todayCount;
    public long yesterdayCount;
    public long lastResetDate;
    public long updatedAt;
    public boolean isCustom = false;

    public TasbihModel() {}

    public TasbihModel(String id, String name, int count) {
        this(id, name, "", count, false);
    }

    public TasbihModel(String id, String name, int count, boolean isCustom) {
        this(id, name, "", count, isCustom);
    }

    public TasbihModel(String id, String name, String content, int count, boolean isCustom) {
        this.id = id;
        this.name = name;
        this.content = content;
        this.count = count;
        this.todayCount = 0;
        this.yesterdayCount = 0;
        this.lastResetDate = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isCustom = isCustom;
    }
}
