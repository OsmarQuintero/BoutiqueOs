package com.osmar.boutiqueos.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AttemptKey implements Serializable {

    @Column(length = 512)
    private String key;

    public AttemptKey() {}

    public AttemptKey(String key) {
        this.key = key;
    }

    public String getKey() { return key; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttemptKey that)) return false;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
