package com.alpha4technologies.prayerclock;

import java.io.Serializable;

public class TasbihModel implements Serializable {

    public String id;
    public String name;
    public long count;
    public long todayCount;
    public long yesterdayCount;
    public long lastResetDate;
    public long updatedAt;

    public TasbihModel() {}

    public TasbihModel(String id, String name, int count) {
        this.id = id;
        this.name = name;
        this.count = count;
        this.todayCount = 0;
        this.yesterdayCount = 0;
        this.lastResetDate = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}
