package com.codewiki.model;

/**
 * Represents a code file detected in a repository.
 * Contains the file path and detected programming language.
 */
public class CodeFile {
    private final String filePath;
    private final String language;

    public CodeFile(String filePath, String language) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language cannot be null or empty");
        }
        this.filePath = filePath;
        this.language = language;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getLanguage() {
        return language;
    }

    @Override
    public String toString() {
        return "CodeFile{filePath='" + filePath + "', language='" + language + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeFile codeFile = (CodeFile) o;
        return filePath.equals(codeFile.filePath) && language.equals(codeFile.language);
    }

    @Override
    public int hashCode() {
        return 31 * filePath.hashCode() + language.hashCode();
    }
}
