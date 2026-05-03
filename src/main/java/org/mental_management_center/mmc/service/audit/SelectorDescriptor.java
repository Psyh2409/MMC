package org.mental_management_center.mmc.service.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SelectorDescriptor {
    private String selectorName;
    private Map<String, Integer> htmlUsage; // Key: Template filename, Value: Occurrence count
    private List<StyleDefinition> definitions;

    public SelectorDescriptor(String selectorName) {
        this.selectorName = selectorName;
        this.htmlUsage = new ConcurrentHashMap<>();
        this.definitions = new ArrayList<>();
    }

    public String getSelectorName() {
        return selectorName;
    }

    public Map<String, Integer> getHtmlUsage() {
        return htmlUsage;
    }

    public List<StyleDefinition> getDefinitions() {
        return definitions;
    }

    @Override
    public String toString() {
        return "SelectorDescriptor{" +
                "selectorName='" + selectorName + '\'' +
                ", htmlUsage=" + htmlUsage +
                ", definitions=" + definitions +
                '}';
    }
}