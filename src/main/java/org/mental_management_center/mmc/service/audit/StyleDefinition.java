package org.mental_management_center.mmc.service.audit;

import java.util.Map;

public class StyleDefinition {
    private String sourceFile;
    private String mediaQuery; // Can be null
    private Map<String, String> properties; // Key: property name, Value: property value

    public StyleDefinition(String sourceFile, String mediaQuery, Map<String, String> properties) {
        this.sourceFile = sourceFile;
        this.mediaQuery = mediaQuery;
        this.properties = properties;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getMediaQuery() {
        return mediaQuery;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "StyleDefinition{" +
                "sourceFile='" + sourceFile + '\'' +
                ", mediaQuery='" + (mediaQuery != null ? mediaQuery.trim() : "N/A") + '\'' +
                ", properties=" + properties +
                '}';
    }
}