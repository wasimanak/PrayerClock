package com.pksofter.prayerclock;

import java.io.Serializable;

public class TasbihModel implements Serializable {

    public String id;
    public String name;
    public long count;
    public long updatedAt;

    public TasbihModel() {}

    public TasbihModel(String id, String name, int count) {
        this.id = id;
        this.name = name;
        this.count = count;
        this.updatedAt = System.currentTimeMillis();
    }
}
