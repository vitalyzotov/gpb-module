package ru.vzotov.gpb;

import java.util.List;

public class GazprombankConfig {
    private List<String> skip;
    private String path;

    public List<String> getSkip() {
        return skip;
    }

    public void setSkip(List<String> skip) {
        this.skip = skip;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
